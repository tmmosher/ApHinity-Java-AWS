import {createSignal, type Accessor} from "solid-js";
import {toast} from "solid-toast";
import {parseGanttTaskSpreadsheetFile} from "./ganttTaskSpreadsheet";
import {
  buildGanttTaskRequestsFromStagedTasks,
  deleteStagedGanttTask,
  editStagedGanttTask,
  stageImportedGanttTasks,
  undoStagedGanttTaskMutation,
  type StagedLocationGanttTask
} from "./stagedGanttTasks";
import {createLocationGanttTasksBulkById} from "./locationGanttTaskApi";
import type {CreateLocationGanttTaskRequest} from "../../types/Types";

type GanttTaskImportControllerProps = {
  host: string;
  locationId: Accessor<string>;
  locationSessionToken: Accessor<number>;
  clearUploadInput: () => void;
  refetchTasks: () => Promise<void>;
};

type StagedTaskMutation = {
  nextTasks: StagedLocationGanttTask[];
  nextUndoStack: StagedLocationGanttTask[][];
  changed: boolean;
};

const applyStagedMutation = (
  mutation: StagedTaskMutation,
  setStagedTasks: (tasks: StagedLocationGanttTask[]) => void,
  setStagedUndoStack: (undoStack: StagedLocationGanttTask[][]) => void
): boolean => {
  if (!mutation.changed) {
    return false;
  }

  setStagedTasks(mutation.nextTasks);
  setStagedUndoStack(mutation.nextUndoStack);
  return true;
};

export const createGanttTaskImportController = (props: GanttTaskImportControllerProps) => {
  const [stagedTasks, setStagedTasks] = createSignal<StagedLocationGanttTask[]>([]);
  const [stagedUndoStack, setStagedUndoStack] = createSignal<StagedLocationGanttTask[][]>([]);
  const [isImportingSpreadsheet, setIsImportingSpreadsheet] = createSignal(false);
  const [isApplyingImports, setIsApplyingImports] = createSignal(false);

  const hasStagedTasks = () => stagedTasks().length > 0;
  const hasPendingTaskChanges = () => stagedUndoStack().length > 0;
  const isSpreadsheetMutationBusy = () => isImportingSpreadsheet() || isApplyingImports();

  const reset = (): void => {
    setStagedTasks([]);
    setStagedUndoStack([]);
    setIsImportingSpreadsheet(false);
    setIsApplyingImports(false);
  };

  const stageSpreadsheetImportFile = async (file: File): Promise<void> => {
    setIsImportingSpreadsheet(true);
    try {
      const requests = await parseGanttTaskSpreadsheetFile(file);
      const result = stageImportedGanttTasks(stagedTasks(), stagedUndoStack(), requests);
      if (!applyStagedMutation(result, setStagedTasks, setStagedUndoStack)) {
        return;
      }

      toast.success(`${requests.length} gantt task${requests.length === 1 ? "" : "s"} staged from spreadsheet`);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to import gantt spreadsheet");
    } finally {
      setIsImportingSpreadsheet(false);
    }
  };

  const undoLastTaskMutation = (): void => {
    if (isSpreadsheetMutationBusy()) {
      return;
    }

    const result = undoStagedGanttTaskMutation(stagedTasks(), stagedUndoStack());
    if (!result.undone) {
      return;
    }

    setStagedTasks(result.nextTasks);
    setStagedUndoStack(result.nextUndoStack);
  };

  const applyStagedImports = async (): Promise<void> => {
    if (isSpreadsheetMutationBusy() || stagedTasks().length === 0) {
      return;
    }

    const uploadLocationId = props.locationId();
    const uploadSessionToken = props.locationSessionToken();
    const tasksToImport = stagedTasks();
    setIsApplyingImports(true);

    try {
      await createLocationGanttTasksBulkById(
        props.host,
        uploadLocationId,
        buildGanttTaskRequestsFromStagedTasks(tasksToImport)
      );
      if (uploadLocationId !== props.locationId() || uploadSessionToken !== props.locationSessionToken()) {
        return;
      }

      setStagedTasks([]);
      setStagedUndoStack([]);
      props.clearUploadInput();
      toast.success(`Imported ${tasksToImport.length} gantt task${tasksToImport.length === 1 ? "" : "s"}`);

      try {
        await props.refetchTasks();
      } catch {
        if (uploadLocationId === props.locationId() && uploadSessionToken === props.locationSessionToken()) {
          toast.error("Gantt tasks imported, but automatic refresh failed. Please refresh the page");
        }
      }
    } catch (error) {
      if (uploadLocationId !== props.locationId() || uploadSessionToken !== props.locationSessionToken()) {
        return;
      }

      if (error instanceof Error && error.message === "CSRF invalid") {
        toast.error("Security token refresh failed. Please retry Apply; your staged tasks are still local");
        return;
      }
      if (error instanceof Error && error.message === "Security token rejected") {
        toast.error("Security validation failed. Retrying Apply usually succeeds without losing staged tasks");
        return;
      }
      if (error instanceof Error && error.message === "Authentication required") {
        toast.error("Session refresh failed. Please sign in again; your staged tasks are still on this page");
        return;
      }
      if (error instanceof Error && error.message === "Insufficient permissions") {
        toast.error("You no longer have permission to import gantt tasks");
        return;
      }

      toast.error(error instanceof Error ? error.message : "Unable to import staged gantt tasks");
    } finally {
      if (uploadLocationId === props.locationId() && uploadSessionToken === props.locationSessionToken()) {
        setIsApplyingImports(false);
      }
    }
  };

  const editStagedTask = (
    taskId: number,
    request: CreateLocationGanttTaskRequest
  ): boolean => applyStagedMutation(
    editStagedGanttTask(stagedTasks(), stagedUndoStack(), taskId, request),
    setStagedTasks,
    setStagedUndoStack
  );

  const deleteStagedTaskById = (taskId: number): boolean => applyStagedMutation(
    deleteStagedGanttTask(stagedTasks(), stagedUndoStack(), taskId),
    setStagedTasks,
    setStagedUndoStack
  );

  return {
    stagedTasks,
    stagedUndoStack,
    hasStagedTasks,
    hasPendingTaskChanges,
    isImportingSpreadsheet,
    isApplyingImports,
    isSpreadsheetMutationBusy,
    reset,
    stageSpreadsheetImportFile,
    undoLastTaskMutation,
    applyStagedImports,
    editStagedTask,
    deleteStagedTaskById
  };
};
