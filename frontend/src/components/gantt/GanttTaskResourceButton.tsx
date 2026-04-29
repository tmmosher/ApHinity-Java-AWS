import type {TimelineTaskLike} from "../../util/location/frappeGanttChart";

type GanttTaskResourceButtonProps = {
  task: TimelineTaskLike;
};

const resourceButtonBaseClass =
  "flex h-full w-full items-center rounded-xl border px-3 text-left text-sm font-medium shadow-sm transition duration-150 ease-out " +
  "motion-reduce:transform-none motion-reduce:transition-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30 " +
  "active:translate-y-px active:scale-[0.99]";

const resourceButtonStaticClass =
  "border-base-300/80 bg-base-100 text-base-content/75 hover:-translate-y-px hover:border-primary/30 hover:bg-primary/10 hover:text-primary";

const resourceButtonStagedClass =
  "border-dashed border-warning/35 bg-warning/10 text-warning-content/80 hover:border-warning/50 hover:bg-warning/15 hover:text-warning-content";

export const GanttTaskResourceButton = (props: GanttTaskResourceButtonProps) => (
  <button
    type="button"
    title={props.task.title}
    aria-label={props.task.title}
    data-gantt-resource-button=""
    data-gantt-resource-task-id={props.task.id}
    class={
      resourceButtonBaseClass +
      " " +
      (props.task.isStaged ? resourceButtonStagedClass : resourceButtonStaticClass)
    }
  >
    <span class="min-w-0 truncate">{props.task.title}</span>
  </button>
);

export default GanttTaskResourceButton;
