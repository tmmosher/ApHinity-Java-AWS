import Dialog from "corvu/dialog";
import {For, Show, createEffect, createMemo, createResource, createSignal, on, type JSX} from "solid-js";
import {createList} from "solid-list";
import {
  createAvailableGanttTaskDependencyTasks,
  createGanttTaskDependencyOptions
} from "../../util/location/ganttTaskDependencies";
import {createMapById} from "../../util/common/indexById";
import {fetchLocationGanttTasksById} from "../../util/location/locationGanttTaskApi";
import {createLocationReactiveSearchControl} from "../../util/location/locationReactiveSearchControl";
import {normalizeGanttTaskDependencyTaskIds} from "../../util/location/ganttTaskForm";

type GanttTaskDependenciesDialogProps = {
  apiHost: string;
  locationId: string;
  currentTaskId?: number;
  currentTaskTitle: string;
  selectedDependencyTaskIds: readonly number[];
  defaultOpen?: boolean;
  disabled?: boolean;
  onApply: (dependencyTaskIds: number[]) => void;
};

export const GanttTaskDependenciesDialog = (props: GanttTaskDependenciesDialogProps) => {
  const searchControl = createLocationReactiveSearchControl("", 150);
  const [isOpen, setIsOpen] = createSignal(props.defaultOpen ?? false);
  const normalizeSelectedDependencyTaskIds = (dependencyTaskIds: readonly number[]) => (
    normalizeGanttTaskDependencyTaskIds(dependencyTaskIds)
      .filter((dependencyTaskId) => dependencyTaskId !== props.currentTaskId)
  );
  const [selectedDependencyTaskIds, setSelectedDependencyTaskIds] = createSignal<number[]>(
    normalizeSelectedDependencyTaskIds(props.selectedDependencyTaskIds)
  );

  const [allTasks] = createResource(() => (isOpen() ? props.locationId : undefined), async (locationId) => (
    fetchLocationGanttTasksById(props.apiHost, locationId)
  ));

  const persistedTasks = createMemo(() => (
    allTasks() ?? []
  ));

  const taskOptionsById = createMemo(() => createMapById(persistedTasks()));

  const availableTaskOptions = createMemo(() => {
    return createAvailableGanttTaskDependencyTasks(
      persistedTasks(),
      props.currentTaskId,
      selectedDependencyTaskIds(),
      searchControl.searchQuery()
    );
  });

  const availableTaskList = createList({
    items: () => availableTaskOptions().map((task) => task.id),
    initialActive: null,
    handleTab: false
  });

  const selectedDependencyOptions = createMemo(() => (
    createGanttTaskDependencyOptions(taskOptionsById(), selectedDependencyTaskIds())
  ));
  const isBusy = () => allTasks.loading;

  createEffect(on(isOpen, (open) => {
    if (!open) {
      return;
    }

    setSelectedDependencyTaskIds(normalizeSelectedDependencyTaskIds(props.selectedDependencyTaskIds));
    searchControl.setSearchDraft("");
    searchControl.setSearchQuery("");
  }));

  const loadError = createMemo(() => {
    if (isBusy()) {
      return undefined;
    }

    if (allTasks.error instanceof Error) {
      return allTasks.error.message;
    }

    return allTasks.error ? "Unable to load gantt tasks." : undefined;
  });

  createEffect(() => {
    const availableOptions = availableTaskOptions();
    if (availableOptions.length === 0) {
      availableTaskList.setActive(null);
      return;
    }

    const activeTaskId = availableTaskList.active();
    if (activeTaskId === null || !availableOptions.some((task) => task.id === activeTaskId)) {
      availableTaskList.setActive(availableOptions[0].id);
    }
  });

  const addDependencyTask = (dependencyTaskId: number): void => {
    setSelectedDependencyTaskIds((current) => normalizeSelectedDependencyTaskIds([...current, dependencyTaskId]));
  };

  const removeDependencyTask = (dependencyTaskId: number): void => {
    setSelectedDependencyTaskIds((current) => current.filter((currentTaskId) => currentTaskId !== dependencyTaskId));
  };

  const applySelection = (): void => {
    props.onApply(normalizeSelectedDependencyTaskIds(selectedDependencyTaskIds()));
    setIsOpen(false);
  };

  const openDialog = (): void => {
    if (props.disabled || isBusy()) {
      return;
    }

    setIsOpen(true);
  };

  const handleSearchKeyDown: JSX.EventHandlerUnion<HTMLInputElement, KeyboardEvent> = (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      const activeTaskId = availableTaskList.active();
      if (activeTaskId !== null) {
        addDependencyTask(activeTaskId);
      }
      return;
    }

    availableTaskList.onKeyDown(event);
  };

  return (
    <>
      <button
        type="button"
        class="btn btn-ghost btn-xs"
        data-gantt-task-dependencies=""
        disabled={props.disabled || isBusy()}
        onClick={openDialog}
      >
        Dependencies
      </button>

      <Dialog
        open={isOpen()}
        onOpenChange={(open) => {
          setIsOpen(open);
        }}
      >
        <Dialog.Portal>
          <Dialog.Overlay class="fixed inset-0 z-[70] bg-black/45 data-closed:pointer-events-none" />
          <Dialog.Content class="fixed inset-0 z-[80] m-auto flex h-[min(92vh,42rem)] w-[min(96vw,42rem)] flex-col gap-4 rounded-2xl border border-base-300 bg-base-100 p-4 shadow-2xl data-closed:pointer-events-none md:p-5">
            <div class="space-y-1">
              <Dialog.Label class="text-lg font-semibold">Dependencies</Dialog.Label>
              <Dialog.Description class="text-sm text-base-content/70">
                Select gantt tasks that {props.currentTaskTitle} depends on.
              </Dialog.Description>
            </div>

            <div class="min-h-0 flex-1 space-y-4 overflow-y-auto pr-1">
              <Show when={loadError()}>
                {(message) => (
                  <div
                    role="alert"
                    class="rounded-xl border border-error/25 bg-error/10 px-3 py-2 text-sm text-error"
                  >
                    {message()}
                  </div>
                )}
              </Show>

              <label class="form-control w-full">
                <span class="label-text text-sm">Search tasks</span>
                <input
                  type="search"
                  class="input input-bordered w-full"
                  placeholder="Search by task title..."
                  value={searchControl.searchDraft()}
                  disabled={isBusy()}
                  onInput={(event) => {
                    searchControl.updateSearchDraft(event.currentTarget.value);
                  }}
                  onKeyDown={handleSearchKeyDown}
                />
              </label>

              <section class="space-y-2">
                <div class="flex items-center justify-between gap-2">
                  <p class="text-sm font-semibold">Available tasks</p>
                  <Show when={!isBusy() && availableTaskOptions().length > 0}>
                    <p class="text-xs text-base-content/60">
                      Use arrow keys and Enter to add a dependency.
                    </p>
                  </Show>
                </div>

                <div class="rounded-2xl border border-base-300 bg-base-200/30">
                  <Show
                    when={!isBusy()}
                    fallback={
                      <div class="p-3 text-sm text-base-content/65">
                        Loading gantt tasks...
                      </div>
                    }
                  >
                    <Show
                      when={availableTaskOptions().length > 0}
                      fallback={
                        <div class="p-3 text-sm text-base-content/65">
                          {loadError()
                            ? "Unable to load gantt tasks."
                            : searchControl.searchQuery().trim()
                            ? "No gantt tasks match this search."
                            : "No persisted gantt tasks are available for this location."}
                        </div>
                      }
                    >
                      <ul
                        class="max-h-48 divide-y divide-base-300 overflow-y-auto"
                        role="listbox"
                        aria-label="Available gantt tasks"
                      >
                        <For each={availableTaskOptions()}>
                          {(task) => {
                            const isActive = () => availableTaskList.active() === task.id;
                            return (
                              <li>
                                <button
                                  type="button"
                                  class={
                                    "flex w-full items-center justify-between gap-3 px-3 py-2 text-left transition "
                                    + (isActive() ? "bg-primary/10 text-primary" : "hover:bg-base-300/60")
                                  }
                                  role="option"
                                  aria-selected={isActive()}
                                  data-gantt-task-dependency-option=""
                                  data-task-id={task.id}
                                  onMouseEnter={() => {
                                    availableTaskList.setActive(task.id);
                                  }}
                                  onClick={() => {
                                    addDependencyTask(task.id);
                                  }}
                                >
                                  <span class="min-w-0 truncate text-sm font-medium">{task.title}</span>
                                  <span class="text-xs text-base-content/55">Add</span>
                                </button>
                              </li>
                            );
                          }}
                        </For>
                      </ul>
                    </Show>
                  </Show>
                </div>
              </section>

              <section class="space-y-2">
                <p class="text-sm font-semibold">Selected dependencies</p>

                <div class="rounded-2xl border border-base-300 bg-base-200/30">
                  <Show
                    when={selectedDependencyOptions().length > 0}
                    fallback={
                      <div class="p-3 text-sm text-base-content/65">
                        No dependencies selected.
                      </div>
                    }
                  >
                    <ul class="divide-y divide-base-300">
                      <For each={selectedDependencyOptions()}>
                        {(task) => (
                          <li class="flex items-center justify-between gap-3 px-3 py-2">
                            <span
                              class={
                                "min-w-0 truncate text-sm"
                                + (task.isMissing ? " text-warning" : " text-base-content")
                              }
                              data-gantt-task-dependency-selected=""
                              data-task-id={task.id}
                            >
                              {task.title}
                            </span>
                            <button
                              type="button"
                              class="btn btn-ghost btn-xs"
                              data-gantt-task-dependency-remove=""
                              onClick={() => {
                                removeDependencyTask(task.id);
                              }}
                            >
                              Remove
                            </button>
                          </li>
                        )}
                      </For>
                    </ul>
                  </Show>
                </div>
              </section>
            </div>

            <div class="flex flex-wrap items-center justify-end gap-2">
              <button
                type="button"
                class="btn btn-ghost btn-sm"
                disabled={isBusy()}
                onClick={() => {
                  setIsOpen(false);
                }}
              >
                Cancel
              </button>
              <button
                type="button"
                class={"btn btn-sm " + (isBusy() ? "btn-disabled" : "btn-primary")}
                disabled={isBusy()}
                onClick={applySelection}
              >
                Apply
              </button>
            </div>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog>
    </>
  );
};

export default GanttTaskDependenciesDialog;
