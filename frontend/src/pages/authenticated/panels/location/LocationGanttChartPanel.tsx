import Gantt from "frappe-gantt";
import {useParams} from "@solidjs/router";
import {
  Show,
  createEffect,
  createMemo,
  createResource,
  createSignal,
  onCleanup,
  untrack,
  type JSX
} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../../../context/ApiHostContext";
import {useProfile} from "../../../../context/ProfileContext";
import type {CreateLocationGanttTaskRequest, LocationGanttTask} from "../../../../types/Types";
import {GanttChartContent} from "../../../../components/gantt/GanttChartContent";
import {GanttChartToolbar} from "../../../../components/gantt/GanttChartToolbar";
import {GanttTaskPopover} from "../../../../components/gantt/GanttTaskPopover";
import {
  GANTT_CHART_HOST_ID,
  createFrappeGanttOptions,
  isStagedGanttTask,
  resolveFrappeGanttContainerHeight,
  toFrappeGanttTask,
  type TimelineTaskLike
} from "../../../../util/location/frappeGanttChart";
import {fitGanttTaskLabels} from "../../../../util/location/ganttTaskLabelTruncation";
import {canEditLocationGanttTask} from "../../../../util/location/ganttTaskForm";
import {
  createLocationGanttTaskById,
  deleteLocationGanttTaskById,
  fetchLocationGanttTasksById,
  updateLocationGanttTaskById
} from "../../../../util/location/locationGanttTaskApi";
import {createDashboardLocationResetGuard, getFreshLocationScopedValue, type LocationScopedResource} from "../../../../util/location/locationView";
import {createLocationReactiveSearchControl} from "../../../../util/location/locationReactiveSearchControl";
import {createGanttTaskImportController} from "../../../../util/location/createGanttTaskImportController";
import "../../../../styles/frappe-gantt.css";

type GanttTaskResource = LocationScopedResource<LocationGanttTask[]> & {
  query: string;
};

type TimelineTask = TimelineTaskLike;

export const LocationGanttChartPanel = () => {
  const host = useApiHost();
  const profileContext = useProfile();
  const params = useParams<{ locationId: string }>();
  const role = createMemo(() => profileContext.profile()?.role);
  const canEditTasks = createMemo(() => canEditLocationGanttTask(role()));
  const searchControl = createLocationReactiveSearchControl();
  const shouldResetState = createDashboardLocationResetGuard(params.locationId);

  const [locationSessionToken, setLocationSessionToken] = createSignal(0);
  const [selectedTask, setSelectedTask] = createSignal<TimelineTask>();
  const [selectedTaskAnchorStyle, setSelectedTaskAnchorStyle] = createSignal<JSX.CSSProperties>();
  const [ganttContainerHeight, setGanttContainerHeight] = createSignal(1);
  let ganttChart: Gantt | undefined;
  let ganttResizeObserver: ResizeObserver | undefined;
  let csvUploadInput: HTMLInputElement | undefined;

  onCleanup(() => {
    ganttResizeObserver?.disconnect();
    ganttResizeObserver = undefined;
    ganttChart = undefined;
  });

  const clearSelectedTask = (): void => {
    setSelectedTask(undefined);
    setSelectedTaskAnchorStyle(undefined);
  };

  const clearUploadInput = (): void => {
    if (csvUploadInput) {
      csvUploadInput.value = "";
    }
  };

  const ganttTaskRequest = createMemo(() => ({
    locationId: params.locationId,
    query: searchControl.searchQuery()
  }));

  const [ganttTaskResource, {refetch: refetchTasks}] = createResource(
    ganttTaskRequest,
    async (request): Promise<GanttTaskResource> => ({
      ...request,
      value: await fetchLocationGanttTasksById(host, request.locationId, request.query)
    })
  );

  const importController = createGanttTaskImportController({
    host,
    locationId: () => params.locationId,
    locationSessionToken,
    clearUploadInput,
    refetchTasks: async () => {
      await refetchTasks();
    }
  });

  const persistedTasks = createMemo(() => {
    const resource = ganttTaskResource();
    const freshLocationTasks = getFreshLocationScopedValue(params.locationId, resource);
    if (!freshLocationTasks) {
      return undefined;
    }
    if (resource?.query !== ganttTaskRequest().query) {
      return undefined;
    }
    return freshLocationTasks;
  });

  const filteredStagedTasks = createMemo(() => {
    const query = searchControl.searchQuery().toLowerCase();
    if (!query) {
      return importController.stagedTasks();
    }
    return importController.stagedTasks().filter((task) => (
      task.title.toLowerCase().includes(query)
    ));
  });

  const timelineTasks = createMemo<TimelineTask[]>(() => (
    [...(persistedTasks() ?? []), ...filteredStagedTasks()].sort((left, right) => (
      left.startDate.localeCompare(right.startDate)
      || left.endDate.localeCompare(right.endDate)
      || left.id - right.id
    ))
  ));

  const taskLoadError = createMemo(() => {
    if (ganttTaskResource.loading || !ganttTaskResource.error) {
      return undefined;
    }
    return ganttTaskResource.error instanceof Error
      ? ganttTaskResource.error.message
      : "Unable to load gantt tasks.";
  });

  const setSelectedTaskAnchorFromBar = (taskId: number): void => {
    if (!ganttChart) {
      return;
    }

    const bar = ganttChart.get_bar(String(taskId));
    const target = bar?.$bar ?? bar?.group;
    if (!target) {
      return;
    }

    const rect = target.getBoundingClientRect();
    setSelectedTaskAnchorStyle({
      position: "fixed",
      left: `${rect.left}px`,
      top: `${rect.top}px`,
      width: `${rect.width}px`,
      height: `${rect.height}px`
    });
  };

  createEffect(() => {
    if (!shouldResetState(params.locationId)) {
      return;
    }

    searchControl.setSearchDraft("");
    searchControl.setSearchQuery("");
    importController.reset();
    setLocationSessionToken((token) => token + 1);
    clearSelectedTask();
    clearUploadInput();
  });

  const saveTask = async (task: TimelineTask, request: CreateLocationGanttTaskRequest): Promise<void> => {
    if (!canEditTasks()) {
      throw new Error("Only partners and admins can update gantt tasks.");
    }
    if (isStagedGanttTask(task)) {
      importController.editStagedTask(task.id, request);
      return;
    }

    await updateLocationGanttTaskById(host, params.locationId, task.id, request);
    await refetchTasks();
    toast.success("Gantt task updated.");
  };

  const deleteTask = async (task: TimelineTask): Promise<void> => {
    if (!canEditTasks()) {
      throw new Error("Only partners and admins can delete gantt tasks.");
    }
    if (isStagedGanttTask(task)) {
      if (!importController.deleteStagedTaskById(task.id)) {
        return;
      }
      toast.success("Staged gantt task removed.");
      return;
    }

    await deleteLocationGanttTaskById(host, params.locationId, task.id);
    await refetchTasks();
    toast.success("Gantt task deleted.");
  };

  const createTask = async (request: CreateLocationGanttTaskRequest): Promise<void> => {
    if (!canEditTasks()) {
      throw new Error("Only partners and admins can create gantt tasks.");
    }

    await createLocationGanttTaskById(host, params.locationId, request);
    await refetchTasks();
    toast.success("Gantt task created.");
  };

  createEffect(() => {
    if (typeof document === "undefined") {
      return;
    }

    const hostElement = document.getElementById(GANTT_CHART_HOST_ID);
    if (!(hostElement instanceof HTMLElement)) {
      return;
    }

    const updateHeight = () => {
      setGanttContainerHeight(
        resolveFrappeGanttContainerHeight(hostElement.getBoundingClientRect().height)
      );
    };

    updateHeight();

    if (typeof ResizeObserver === "undefined") {
      return;
    }

    ganttResizeObserver?.disconnect();
    ganttResizeObserver = new ResizeObserver(() => {
      updateHeight();
    });
    ganttResizeObserver.observe(hostElement);

    onCleanup(() => {
      ganttResizeObserver?.disconnect();
      ganttResizeObserver = undefined;
    });
  });

  createEffect(() => {
    if (typeof document === "undefined") {
      return;
    }

    const hostElement = document.getElementById(GANTT_CHART_HOST_ID);
    if (!(hostElement instanceof HTMLElement)) {
      return;
    }

    const measuredHeight = resolveFrappeGanttContainerHeight(hostElement.getBoundingClientRect().height);
    const chartHeight = Math.max(ganttContainerHeight(), measuredHeight);
    const tasks = timelineTasks();
    const chartTasks = tasks.map(toFrappeGanttTask);
    const chartOptions = createFrappeGanttOptions(chartHeight, (chartTask) => {
      const selected = timelineTasks().find((candidate) => String(candidate.id) === String(chartTask.id));
      if (!selected) {
        return;
      }
      setSelectedTask(selected);
      setSelectedTaskAnchorFromBar(selected.id);
    });

    if (!ganttChart) {
      ganttChart = new Gantt(hostElement, chartTasks, chartOptions);
    } else {
      ganttChart.update_options({
        container_height: chartOptions.container_height
      });
      ganttChart.refresh(chartTasks);
    }

    // Frappe positions SVG labels after drawing the bars, so truncate them here
    // to keep long titles inside the task bounds.
    fitGanttTaskLabels(hostElement);

    const selected = untrack(selectedTask);
    if (selected) {
      setSelectedTaskAnchorFromBar(selected.id);
    }
  });

  createEffect(() => {
    const selected = selectedTask();
    if (!selected) {
      return;
    }

    ganttContainerHeight();
    timelineTasks().length;
    setSelectedTaskAnchorFromBar(selected.id);
  });

  return (
    <div class="flex min-h-[calc(100vh-16rem)] flex-col gap-4">
      <GanttChartToolbar
        canEdit={canEditTasks()}
        isImportingCsv={importController.isImportingCsv()}
        isApplyingImports={importController.isApplyingImports()}
        hasStagedTasks={importController.hasStagedTasks()}
        hasPendingTaskChanges={importController.hasPendingTaskChanges()}
        csvUploadInputRef={(element) => {
          csvUploadInput = element;
        }}
        onCsvUploadChange={(event) => {
          const input = event.currentTarget;
          const file = input.files?.[0];
          if (!file) {
            return;
          }
          void importController.stageCsvImportFile(file)
            .finally(() => {
              input.value = "";
            });
        }}
        onApply={() => {
          void importController.applyStagedImports();
        }}
        onUndo={importController.undoLastTaskMutation}
      />

      <GanttChartContent
        searchDraft={searchControl.searchDraft()}
        onSearchInput={(event) => {
          searchControl.updateSearchDraft(event.currentTarget.value);
        }}
        taskLoadError={taskLoadError()}
        isLoading={ganttTaskResource.loading && persistedTasks() === undefined}
        canEdit={canEditTasks()}
        onCreateTask={createTask}
        hostId={GANTT_CHART_HOST_ID}
        timelineTaskCount={timelineTasks().length}
        searchQuery={searchControl.searchQuery()}
      />

      <Show when={selectedTask()}>
        {(task) => (
          <GanttTaskPopover
            task={task()}
            canEdit={canEditTasks()}
            anchorStyle={selectedTaskAnchorStyle()}
            onSave={saveTask}
            onDelete={deleteTask}
            onClose={clearSelectedTask}
          />
        )}
      </Show>
    </div>
  );
};
