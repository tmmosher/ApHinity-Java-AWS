import Popover from "corvu/popover";
import {useParams} from "@solidjs/router";
import {
  For,
  Show,
  createEffect,
  createMemo,
  createResource,
  createSignal,
  type JSX
} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../../../context/ApiHostContext";
import {useProfile} from "../../../../context/ProfileContext";
import type {CreateLocationGanttTaskRequest, LocationGanttTask} from "../../../../types/Types";
import {applyStateSnapshot, undoStateSnapshot} from "../../../../util/common/stateHistory";
import {formatDisplayDate} from "../../../../util/location/dateUtility";
import {parseGanttTaskCsvFile} from "../../../../util/location/ganttTaskCsv";
import {
  canEditLocationGanttTask,
  createDefaultGanttTaskDraft,
  createGanttTaskDraftFromTask,
  createLocationGanttTaskRequestFromDraft,
  GANTT_TASK_TITLE_MAX_LENGTH,
  type GanttTaskDraft
} from "../../../../util/location/ganttTaskForm";
import {
  createLocationGanttTaskById,
  deleteLocationGanttTaskById,
  fetchLocationGanttTasksById,
  updateLocationGanttTaskById
} from "../../../../util/location/locationGanttTaskApi";
import {createDashboardLocationResetGuard} from "../../../../util/location/locationView";
import {createLocationReactiveSearchControl} from "../../../../util/location/locationReactiveSearchControl";
import {SERVICE_EVENT_POPOVER_POSITION_PROPS} from "../../../../util/location/serviceEventPopoverPosition";

type GanttTaskResource = {
  locationId: string;
  query: string;
  value: LocationGanttTask[];
};

type StagedLocationGanttTask = LocationGanttTask & {
  isStaged: true;
};

type TimelineTask = LocationGanttTask | StagedLocationGanttTask;

const DAY_MS = 24 * 60 * 60 * 1000;

const parseUtcDate = (value: string): number => {
  const [year, month, day] = value.split("-").map(Number);
  return Date.UTC(year, month - 1, day);
};

const isStagedGanttTask = (task: TimelineTask): task is StagedLocationGanttTask => (
  "isStaged" in task && task.isStaged === true
);

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

const taskBarStyle = (task: TimelineTask, startUtc: number, totalDays: number): JSX.CSSProperties => {
  const taskStart = parseUtcDate(task.startDate);
  const taskEnd = parseUtcDate(task.endDate);
  const offsetDays = Math.max(0, Math.floor((taskStart - startUtc) / DAY_MS));
  const durationDays = Math.max(1, Math.floor((taskEnd - taskStart) / DAY_MS) + 1);
  const leftPercent = (offsetDays / totalDays) * 100;
  const widthPercent = (durationDays / totalDays) * 100;
  const clampedWidth = Math.min(Math.max(widthPercent, 3), Math.max(100 - leftPercent, 0));
  return {left: `${leftPercent}%`, width: `${clampedWidth}%`};
};

const TaskPopover = (props: {
  task: TimelineTask;
  canEdit: boolean;
  canDelete: boolean;
  onSave?: (task: TimelineTask, request: CreateLocationGanttTaskRequest) => Promise<void>;
  onDelete?: (task: TimelineTask) => Promise<void>;
  children: JSX.Element;
}) => {
  const [isEditing, setIsEditing] = createSignal(false);
  const [isSaving, setIsSaving] = createSignal(false);
  const [isDeleting, setIsDeleting] = createSignal(false);
  const [submissionError, setSubmissionError] = createSignal<string>();
  const [deletionError, setDeletionError] = createSignal<string>();
  const [draft, setDraft] = createSignal<GanttTaskDraft>(createGanttTaskDraftFromTask(props.task));

  const resetEditor = (): void => {
    setIsEditing(false);
    setIsSaving(false);
    setIsDeleting(false);
    setSubmissionError(undefined);
    setDeletionError(undefined);
    setDraft(createGanttTaskDraftFromTask(props.task));
  };

  const save = async (closePopover: () => void): Promise<void> => {
    if (!props.canEdit || !props.onSave || isSaving()) {
      return;
    }
    setIsSaving(true);
    setSubmissionError(undefined);
    setDeletionError(undefined);
    try {
      await props.onSave(props.task, createLocationGanttTaskRequestFromDraft(draft()));
      closePopover();
      resetEditor();
    } catch (error) {
      setSubmissionError(error instanceof Error ? error.message : "Unable to save gantt task.");
    } finally {
      setIsSaving(false);
    }
  };

  const remove = async (closePopover: () => void): Promise<void> => {
    if (!props.canDelete || !props.onDelete || isDeleting()) {
      return;
    }
    setIsDeleting(true);
    setSubmissionError(undefined);
    setDeletionError(undefined);
    try {
      await props.onDelete(props.task);
      closePopover();
      resetEditor();
    } catch (error) {
      setDeletionError(error instanceof Error ? error.message : "Unable to delete gantt task.");
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <Popover
      {...SERVICE_EVENT_POPOVER_POSITION_PROPS}
      trapFocus={false}
      restoreFocus={false}
      closeOnOutsideFocus={false}
      onOpenChange={(open) => {
        if (!open) {
          resetEditor();
        }
      }}
    >
      {(popover) => (
        <>
          {props.children}

          <Popover.Portal>
            <Popover.Content class="z-50 w-[min(96vw,28rem)] rounded-2xl border border-base-300 bg-base-100 shadow-2xl">
              <Show
                when={isEditing()}
                fallback={
                  <div class="space-y-4 p-4 md:p-5">
                    <div class="flex items-start justify-between gap-3">
                      <Popover.Label class="text-base font-semibold leading-tight">
                        {props.task.title}
                      </Popover.Label>
                      <div class="flex items-center gap-2">
                        <Show when={props.canEdit}>
                          <button type="button" class="btn btn-primary btn-xs" onClick={() => setIsEditing(true)}>
                            Edit
                          </button>
                        </Show>
                      </div>
                    </div>

                    <p class="text-sm text-base-content/75">
                      {formatDisplayDate(props.task.startDate)} - {formatDisplayDate(props.task.endDate)}
                    </p>
                    <p class="text-sm leading-6 text-base-content/80">
                      {props.task.description ?? "No description provided."}
                    </p>

                    <Show when={deletionError()}>
                      {(message) => <p class="text-sm text-error">{message()}</p>}
                    </Show>

                    <Show when={props.canDelete}>
                      <div class="flex justify-end">
                        <button
                          type="button"
                          class="btn btn-error btn-outline btn-sm"
                          disabled={isDeleting()}
                          onClick={() => {
                            void remove(() => popover.setOpen(false));
                          }}
                        >
                          {isDeleting() ? "Deleting..." : "Delete"}
                        </button>
                      </div>
                    </Show>
                  </div>
                }
              >
                <form
                  class="space-y-3 p-4 md:p-5"
                  onSubmit={(event) => {
                    event.preventDefault();
                    void save(() => popover.setOpen(false));
                  }}
                >
                  <Popover.Label class="text-base font-semibold">Edit Task</Popover.Label>
                  <input
                    type="text"
                    class="input input-bordered h-10 w-full"
                    maxlength={GANTT_TASK_TITLE_MAX_LENGTH}
                    value={draft().title}
                    onInput={(event) => {
                      setDraft((current) => ({...current, title: event.currentTarget.value}));
                    }}
                    required
                  />
                  <div class="grid grid-cols-1 gap-2 sm:grid-cols-2">
                    <input
                      type="date"
                      class="input input-bordered h-10"
                      value={draft().startDate}
                      onInput={(event) => {
                        setDraft((current) => ({...current, startDate: event.currentTarget.value}));
                      }}
                      required
                    />
                    <input
                      type="date"
                      class="input input-bordered h-10"
                      value={draft().endDate}
                      onInput={(event) => {
                        setDraft((current) => ({...current, endDate: event.currentTarget.value}));
                      }}
                      required
                    />
                  </div>
                  <textarea
                    class="textarea textarea-bordered min-h-[5rem] w-full"
                    value={draft().description}
                    onInput={(event) => {
                      setDraft((current) => ({...current, description: event.currentTarget.value}));
                    }}
                  />
                  <Show when={submissionError()}>
                    {(message) => <p class="text-sm text-error">{message()}</p>}
                  </Show>
                  <div class="flex justify-end gap-2">
                    <button type="button" class="btn btn-ghost btn-sm" disabled={isSaving()} onClick={resetEditor}>
                      Cancel
                    </button>
                    <button type="submit" class="btn btn-primary btn-sm" disabled={isSaving()}>
                      {isSaving() ? "Saving..." : "Save"}
                    </button>
                  </div>
                </form>
              </Show>
            </Popover.Content>
          </Popover.Portal>
        </>
      )}
    </Popover>
  );
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
  let csvUploadInput: HTMLInputElement | undefined;

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
      parseUtcDate(left.startDate) - parseUtcDate(right.startDate)
      || parseUtcDate(left.endDate) - parseUtcDate(right.endDate)
      || left.id - right.id
    ))
  ));

  const timelineMetrics = createMemo(() => {
    const tasks = timelineTasks();
    if (tasks.length === 0) {
      const now = new Date();
      const startUtc = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1);
      const endUtc = Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 0);
      return {startUtc, totalDays: Math.max(1, Math.floor((endUtc - startUtc) / DAY_MS) + 1)};
    }
    const startUtc = Math.min(...tasks.map((task) => parseUtcDate(task.startDate)));
    const endUtc = Math.max(...tasks.map((task) => parseUtcDate(task.endDate)));
    return {startUtc, totalDays: Math.max(1, Math.floor((endUtc - startUtc) / DAY_MS) + 1)};
  });

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

  const undoLastStagedMutation = (): void => {
    const result = undoStateSnapshot(stagedTasks(), stagedUndoStack(), cloneStagedTasks);
    if (!result.undone) {
      return;
    }
    setStagedTasks(result.nextState);
    setStagedUndoStack(result.nextUndoStack);
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
              onClick={undoLastStagedMutation}
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

      <section class="flex min-h-[44rem] flex-1 flex-col rounded-2xl border border-base-300 bg-base-100/70 p-4 shadow-sm md:p-6">
        <Show when={!ganttTaskResource.loading || persistedTasks() !== undefined} fallback={<p>Loading gantt tasks...</p>}>
          <Show when={taskLoadError()}>
            {(message) => (
              <div class="mb-3 rounded-xl border border-error/25 bg-error/10 px-3 py-2 text-sm text-error">
                {message()}
              </div>
            )}
          </Show>

          <Show when={timelineTasks().length > 0} fallback={
            <p class="text-sm text-base-content/70">
              {searchControl.searchQuery() ? "No gantt tasks match your search." : "No gantt tasks available for this location."}
            </p>
          }>
            <div class="space-y-3">
              <For each={timelineTasks()}>
                {(task) => (
                  <div class="rounded-xl border border-base-300/80 bg-base-100/80 p-3">
                    <div class="mb-2 flex items-center justify-between gap-2 text-xs">
                      <span class="truncate font-semibold">
                        {task.title}
                        <Show when={isStagedGanttTask(task)}>
                          <span class="ml-2 rounded-full border border-primary/30 bg-primary/10 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.12em] text-primary">
                            Staged
                          </span>
                        </Show>
                      </span>
                      <span class="text-base-content/65">
                        {formatDisplayDate(task.startDate)} - {formatDisplayDate(task.endDate)}
                      </span>
                    </div>

                    <div class="relative h-10 rounded-xl border border-base-300 bg-base-200/55">
                      <TaskPopover
                        task={task}
                        canEdit={canEditTasks()}
                        canDelete={canEditTasks()}
                        onSave={saveTask}
                        onDelete={deleteTask}
                      >
                        <Popover.Trigger
                          type="button"
                          style={taskBarStyle(task, timelineMetrics().startUtc, timelineMetrics().totalDays)}
                          class={"absolute top-1/2 h-7 -translate-y-1/2 truncate rounded-lg border px-2 text-left text-xs font-semibold shadow-sm " +
                            (isStagedGanttTask(task)
                              ? "border-dashed border-primary/55 bg-primary/20 text-primary"
                              : "border-[#86efac] bg-[#dcfce7] text-[#166534]")}
                        >
                          {task.title}
                        </Popover.Trigger>
                      </TaskPopover>
                    </div>
                  </div>
                )}
              </For>
            </div>
          </Show>
        </Show>
      </section>
    </div>
  );
};
