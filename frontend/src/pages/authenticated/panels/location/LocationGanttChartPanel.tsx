import Gantt from "frappe-gantt";
import {useParams} from "@solidjs/router";
import {Show, createEffect, createMemo, createResource, createSignal, onCleanup, type JSX} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../../../context/ApiHostContext";
import {useProfile} from "../../../../context/ProfileContext";
import type {CreateLocationGanttTaskRequest, LocationGanttTask} from "../../../../types/Types";
import {GanttChartShell} from "../../../../components/location/GanttChartShell";
import {GanttTaskPopover} from "../../../../components/location/GanttTaskPopover";
import {applyStateSnapshot, undoStateSnapshot} from "../../../../util/common/stateHistory";
import {
  GANTT_CHART_HOST_ID,
  createFrappeGanttOptions,
  isStagedGanttTask,
  resolveFrappeGanttContainerHeight,
  toFrappeGanttTask,
  type TimelineTaskLike
} from "../../../../util/location/frappeGanttChart";
import {parseGanttTaskCsvFile} from "../../../../util/location/ganttTaskCsv";
import {
  canEditLocationGanttTask,
  createDefaultGanttTaskDraft,
  createLocationGanttTaskRequestFromDraft,
  GANTT_TASK_TITLE_MAX_LENGTH
} from "../../../../util/location/ganttTaskForm";
import {
  createLocationGanttTaskById,
  deleteLocationGanttTaskById,
  fetchLocationGanttTasksById,
  updateLocationGanttTaskById
} from "../../../../util/location/locationGanttTaskApi";
import {createDashboardLocationResetGuard} from "../../../../util/location/locationView";
import {createLocationReactiveSearchControl} from "../../../../util/location/locationReactiveSearchControl";
import "../../../../styles/frappe-gantt.css";

type GanttTaskResource = {
  locationId: string;
  query: string;
  value: LocationGanttTask[];
};

type StagedLocationGanttTask = TimelineTaskLike & {
  isStaged: true;
};

type TimelineTask = TimelineTaskLike;

const cloneStagedTasks = (tasks: StagedLocationGanttTask[]): StagedLocationGanttTask[] => (
  JSON.parse(JSON.stringify(tasks)) as StagedLocationGanttTask[]
);

const stagedTaskSignature = (tasks: readonly StagedLocationGanttTask[]): string => (
  JSON.stringify(tasks.map((task) => ({
    id: task.id,
    title: task.title,
    startDate: task.startDate,
    endDate: task.endDate,
    description: task.description
  })))
);

const nextStagedTaskId = (tasks: readonly StagedLocationGanttTask[]): number => (
  tasks.reduce((lowestId, task) => Math.min(lowestId, task.id), 0) - 1
);

const taskRequestFromTask = (
  task: Pick<TimelineTask, "title" | "startDate" | "endDate" | "description">
): CreateLocationGanttTaskRequest => ({
  title: task.title,
  startDate: task.startDate,
  endDate: task.endDate,
  description: task.description ?? null
});

const createStagedTask = (
  request: CreateLocationGanttTaskRequest,
  id: number
): StagedLocationGanttTask => {
  const now = new Date().toISOString();
  return {
    id,
    title: request.title,
    startDate: request.startDate,
    endDate: request.endDate,
    description: request.description,
    createdAt: now,
    updatedAt: now,
    isStaged: true
  };
};

export const LocationGanttChartPanel = () => {
  const host = useApiHost();
  const profileContext = useProfile();
  const params = useParams<{ locationId: string }>();
  const role = createMemo(() => profileContext.profile()?.role);
  const canEditTasks = createMemo(() => canEditLocationGanttTask(role()));
  const searchControl = createLocationReactiveSearchControl();
  const shouldResetState = createDashboardLocationResetGuard(params.locationId);

  const [manualDraft, setManualDraft] = createSignal(createDefaultGanttTaskDraft());
  const [isSavingManual, setIsSavingManual] = createSignal(false);
  const [stagedTasks, setStagedTasks] = createSignal<StagedLocationGanttTask[]>([]);
  const [stagedUndoStack, setStagedUndoStack] = createSignal<StagedLocationGanttTask[][]>([]);
  const [isImportingCsv, setIsImportingCsv] = createSignal(false);
  const [isApplyingImports, setIsApplyingImports] = createSignal(false);
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

  const clearSelectedTask = () => {
    setSelectedTask(undefined);
    setSelectedTaskAnchorStyle(undefined);
  };

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

  const syncSelectedTaskAnchor = () => {
    const task = selectedTask();
    if (!task) {
      return;
    }

    setSelectedTaskAnchorFromBar(task.id);
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

  const persistedTasks = createMemo(() => {
    const resource = ganttTaskResource();
    const request = ganttTaskRequest();
    if (!resource || resource.locationId !== request.locationId || resource.query !== request.query) {
      return undefined;
    }
    return resource.value;
  });

  const filteredStagedTasks = createMemo(() => {
    const query = searchControl.searchQuery().toLowerCase();
    if (!query) {
      return stagedTasks();
    }
    return stagedTasks().filter((task) => task.title.toLowerCase().includes(query));
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

  const clearUploadInput = (): void => {
    if (csvUploadInput) {
      csvUploadInput.value = "";
    }
  };

  createEffect(() => {
    if (!shouldResetState(params.locationId)) {
      return;
    }

    searchControl.setSearchDraft("");
    searchControl.setSearchQuery("");
    setManualDraft(createDefaultGanttTaskDraft());
    setIsSavingManual(false);
    setStagedTasks([]);
    setStagedUndoStack([]);
    setIsImportingCsv(false);
    setIsApplyingImports(false);
    setLocationSessionToken((token) => token + 1);
    clearSelectedTask();
    clearUploadInput();
  });

  const applyStagedSnapshot = (nextTasks: StagedLocationGanttTask[]): boolean => {
    const result = applyStateSnapshot(
      stagedTasks(),
      stagedUndoStack(),
      nextTasks,
      cloneStagedTasks,
      (left, right) => stagedTaskSignature(left) === stagedTaskSignature(right)
    );
    if (!result.changed) {
      return false;
    }
    setStagedTasks(result.nextState);
    setStagedUndoStack(result.nextUndoStack);
    return true;
  };

  const stageCsvImport = async (event: Event): Promise<void> => {
    const input = event.currentTarget;
    if (!(input instanceof HTMLInputElement)) {
      return;
    }
    const file = input.files?.[0];
    if (!file) {
      return;
    }

    setIsImportingCsv(true);
    try {
      const requests = await parseGanttTaskCsvFile(file);
      let nextId = nextStagedTaskId(stagedTasks());
      const nextTasks = [
        ...stagedTasks(),
        ...requests.map((request) => {
          const stagedTask = createStagedTask(request, nextId);
          nextId -= 1;
          return stagedTask;
        })
      ];
      if (applyStagedSnapshot(nextTasks)) {
        toast.success(`${requests.length} gantt task${requests.length === 1 ? "" : "s"} staged.`);
      }
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to import gantt tasks.");
    } finally {
      setIsImportingCsv(false);
      input.value = "";
    }
  };

  const applyStagedImports = async (): Promise<void> => {
    if (!stagedTasks().length || isApplyingImports()) {
      return;
    }

    const uploadLocationId = params.locationId;
    const uploadSessionToken = locationSessionToken();
    const tasksToImport = [...stagedTasks()];
    setIsApplyingImports(true);

    let importedCount = 0;
    try {
      for (const task of tasksToImport) {
        await createLocationGanttTaskById(host, uploadLocationId, taskRequestFromTask(task));
        importedCount += 1;
      }

      if (uploadLocationId !== params.locationId || uploadSessionToken !== locationSessionToken()) {
        return;
      }

      setStagedTasks([]);
      setStagedUndoStack([]);
      clearUploadInput();
      await refetchTasks();
      toast.success("Imported staged gantt tasks.");
    } catch (error) {
      if (uploadLocationId !== params.locationId || uploadSessionToken !== locationSessionToken()) {
        return;
      }

      if (importedCount > 0) {
        setStagedTasks(tasksToImport.slice(importedCount));
        setStagedUndoStack([]);
        await refetchTasks();
        toast.error(
          `Imported ${importedCount} gantt task${importedCount === 1 ? "" : "s"} before an error. Remaining tasks are still staged.`
        );
      } else {
        toast.error(error instanceof Error ? error.message : "Unable to import staged tasks.");
      }
    } finally {
      if (uploadLocationId === params.locationId && uploadSessionToken === locationSessionToken()) {
        setIsApplyingImports(false);
      }
    }
  };

  const saveTask = async (task: TimelineTask, request: CreateLocationGanttTaskRequest): Promise<void> => {
    if (!canEditTasks()) {
      throw new Error("Only partners and admins can update gantt tasks.");
    }
    if (isStagedGanttTask(task)) {
      applyStagedSnapshot(stagedTasks().map((currentTask) => (
        currentTask.id === task.id
          ? {...currentTask, ...request, updatedAt: new Date().toISOString()}
          : currentTask
      )));
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
      applyStagedSnapshot(stagedTasks().filter((currentTask) => currentTask.id !== task.id));
      toast.success("Staged gantt task removed.");
      return;
    }

    await deleteLocationGanttTaskById(host, params.locationId, task.id);
    await refetchTasks();
    toast.success("Gantt task deleted.");
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
      syncSelectedTaskAnchor();
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
      const task = timelineTasks().find((candidate) => String(candidate.id) === String(chartTask.id));
      if (task) {
        setSelectedTaskAnchorFromBar(task.id);
        setSelectedTask(task);
      }
    });

    if (!ganttChart) {
      ganttChart = new Gantt(hostElement, chartTasks, chartOptions);
    } else {
      ganttChart.update_options({
        container_height: chartOptions.container_height
      });
      ganttChart.refresh(chartTasks);
    }

    syncSelectedTaskAnchor();
  });

  return (
    <div class="flex min-h-[calc(100vh-16rem)] flex-col gap-4">
      <section class="rounded-2xl border border-base-300 bg-base-100/70 p-6 shadow-sm">
        <div class="flex flex-wrap items-center gap-3">
          <h2 class="text-xl font-semibold tracking-tight">Gantt Chart</h2>
          <label class="input input-bordered ml-auto flex h-10 min-h-10 w-full items-center gap-2 md:w-72">
            <input
              type="search"
              class="grow"
              placeholder="Search title..."
              value={searchControl.searchDraft()}
              onInput={(event) => {
                searchControl.updateSearchDraft(event.currentTarget.value);
              }}
            />
          </label>
        </div>

        <div class="mt-3 flex flex-wrap items-center gap-2 text-xs text-base-content/70">
          <span class="inline-flex items-center gap-2 rounded-full border border-base-300 bg-base-200/60 px-2.5 py-1">
            <span class="h-2.5 w-5 rounded-full border border-[#86efac] bg-[#dcfce7]" />
            Persisted
          </span>
          <span class="inline-flex items-center gap-2 rounded-full border border-base-300 bg-base-200/60 px-2.5 py-1">
            <span class="h-2.5 w-5 rounded-full border border-primary/55 bg-primary/20" />
            Staged
          </span>
          <span class="text-base-content/55">Click a bar to inspect or edit a task.</span>
        </div>

        <Show when={canEditTasks()}>
          <div class="mt-3 flex flex-wrap items-center gap-2">
            <label
              for="gantt_csv_upload"
              class={"btn btn-outline btn-sm " + ((isImportingCsv() || isApplyingImports()) ? "btn-disabled" : "")}
            >
              Upload CSV
            </label>
            <input
              id="gantt_csv_upload"
              type="file"
              ref={(element) => {
                csvUploadInput = element;
              }}
              class="hidden"
              accept=".csv"
              disabled={isImportingCsv() || isApplyingImports()}
              onChange={(event) => {
                void stageCsvImport(event);
              }}
            />
            <button
              type="button"
              class={"btn btn-primary btn-sm " + ((!stagedTasks().length || isApplyingImports()) ? "btn-disabled" : "")}
              disabled={!stagedTasks().length || isApplyingImports()}
              onClick={() => {
                void applyStagedImports();
              }}
            >
              {isApplyingImports() ? "Applying..." : "Apply"}
            </button>
            <button
              type="button"
              class={"btn btn-outline btn-sm " + ((!stagedUndoStack().length || isApplyingImports()) ? "btn-disabled" : "")}
              disabled={!stagedUndoStack().length || isApplyingImports()}
              onClick={() => {
                const result = undoStateSnapshot(stagedTasks(), stagedUndoStack(), cloneStagedTasks);
                if (!result.undone) {
                  return;
                }
                setStagedTasks(result.nextState);
                setStagedUndoStack(result.nextUndoStack);
              }}
            >
              Undo
            </button>
          </div>
        </Show>
      </section>

      <Show when={canEditTasks()}>
        <section class="rounded-2xl border border-base-300 bg-base-100/70 p-6 shadow-sm">
          <h3 class="text-lg font-semibold tracking-tight">Add Task</h3>
          <form
            class="mt-4 grid grid-cols-1 gap-3 md:grid-cols-2"
            onSubmit={(event) => {
              event.preventDefault();
              if (isSavingManual()) {
                return;
              }
              setIsSavingManual(true);
              void createLocationGanttTaskById(host, params.locationId, createLocationGanttTaskRequestFromDraft(manualDraft()))
                .then(async () => {
                  setManualDraft(createDefaultGanttTaskDraft());
                  await refetchTasks();
                  toast.success("Gantt task created.");
                })
                .catch((error) => {
                  toast.error(error instanceof Error ? error.message : "Unable to create gantt task.");
                })
                .finally(() => {
                  setIsSavingManual(false);
                });
            }}
          >
            <input
              type="text"
              class="input input-bordered md:col-span-2"
              maxlength={GANTT_TASK_TITLE_MAX_LENGTH}
              value={manualDraft().title}
              onInput={(event) => {
                setManualDraft((current) => ({...current, title: event.currentTarget.value}));
              }}
              required
            />
            <input
              type="date"
              class="input input-bordered"
              value={manualDraft().startDate}
              onInput={(event) => {
                setManualDraft((current) => ({...current, startDate: event.currentTarget.value}));
              }}
              required
            />
            <input
              type="date"
              class="input input-bordered"
              value={manualDraft().endDate}
              onInput={(event) => {
                setManualDraft((current) => ({...current, endDate: event.currentTarget.value}));
              }}
              required
            />
            <textarea
              class="textarea textarea-bordered md:col-span-2"
              value={manualDraft().description}
              onInput={(event) => {
                setManualDraft((current) => ({...current, description: event.currentTarget.value}));
              }}
            />
            <div class="md:col-span-2 flex justify-end">
              <button type="submit" class="btn btn-primary" disabled={isSavingManual()}>
                {isSavingManual() ? "Saving..." : "Add Task"}
              </button>
            </div>
          </form>
        </section>
      </Show>

      <section class="flex flex-col rounded-2xl border border-base-300 bg-base-100/70 p-4 shadow-sm md:p-6">
        <Show when={taskLoadError()}>
          {(message) => (
            <div class="mb-3 rounded-xl border border-error/25 bg-error/10 px-3 py-2 text-sm text-error">
              {message()}
            </div>
          )}
        </Show>

        <Show when={ganttTaskResource.loading && persistedTasks() === undefined}>
          <p class="mb-3 text-sm text-base-content/70">Loading gantt tasks...</p>
        </Show>

        <div class="self-center w-fit max-w-full overflow-x-auto rounded-2xl border border-base-300 bg-base-100/80">
          <GanttChartShell
            hostId={GANTT_CHART_HOST_ID}
            class="w-max min-h-[32rem]"
          />
        </div>

        <Show when={!ganttTaskResource.loading && timelineTasks().length === 0}>
          <p class="mt-3 text-sm text-base-content/70">
            {searchControl.searchQuery() ? "No gantt tasks match your search." : "No gantt tasks available for this location."}
          </p>
        </Show>
      </section>

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
