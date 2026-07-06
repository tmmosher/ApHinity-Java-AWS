import {createEffect, onCleanup, onMount} from "solid-js";
import type {LocationGraph} from "../../types/Types";
import {createTabulatorGraphModel, type TabulatorGraphRow} from "../../util/graph/tabulatorGraph";
import "tabulator-tables/dist/css/tabulator_simple.min.css";
import type {TabulatorColumnDefinition} from "tabulator-tables";

type TabulatorGraphProps = {
  graph: LocationGraph;
  class?: string;
};

const buildFollowUpPopup = (row: TabulatorGraphRow): HTMLElement => {
  const root = document.createElement("div");
  root.className = "min-w-56 space-y-2 rounded-lg bg-base-100 p-3 text-sm text-base-content shadow-xl";

  const status = document.createElement("p");
  status.className = "text-xs font-semibold uppercase tracking-wide text-base-content/70";
  status.textContent = row.caStatus || "Unknown";
  root.appendChild(status);

  const list = document.createElement("ul");
  list.className = "space-y-1";
  const followUps = row.followUps.length > 0 ? row.followUps : [{date: "", value: "No follow-up measurements"}];
  for (const followUp of followUps) {
    const item = document.createElement("li");
    item.className = "flex items-center justify-between gap-4";
    const date = document.createElement("span");
    date.textContent = followUp.date;
    const value = document.createElement("span");
    value.className = "font-medium";
    value.textContent = followUp.value;
    item.append(date, value);
    list.appendChild(item);
  }
  root.appendChild(list);
  return root;
};

const withInteractiveColumns = (columns: TabulatorColumnDefinition[]): TabulatorColumnDefinition[] =>
  columns.map((column) => {
    if (column.field !== "follow_ups") {
      return column;
    }
    return {
      ...column,
      hozAlign: "center",
      formatter: (cell) => {
        const row = cell.getRow().getData() as TabulatorGraphRow;
        return row.followUps.length > 0 ? String(row.followUps.length) : "";
      },
      clickPopup: (_event, cell) =>
        buildFollowUpPopup(cell.getRow().getData() as TabulatorGraphRow)
    };
  });

const TabulatorGraph = (props: TabulatorGraphProps) => {
  let host!: HTMLDivElement;
  let table: {setData: (rows: Record<string, unknown>[]) => Promise<unknown>; setColumns: (columns: TabulatorColumnDefinition[]) => void; destroy: () => void} | null = null;
  let disposed = false;

  const model = () => createTabulatorGraphModel(props.graph);

  onMount(() => {
    void import("tabulator-tables").then(({TabulatorFull}) => {
      if (disposed) {
        return;
      }
      const currentModel = model();

      table = new TabulatorFull(host, {
        data: currentModel.rows,
        columns: withInteractiveColumns(currentModel.columns),
        layout: "fitDataStretch",
        height: "100%",
        placeholder: "No recent sample measurements",
        index: "rowIdentifier"
      });
    });
  });

  createEffect(() => {
    const currentTable = table;
    if (!currentTable) {
      return;
    }
    const currentModel = model();
    currentTable.setColumns(withInteractiveColumns(currentModel.columns));
    void currentTable.setData(currentModel.rows);
  });

  onCleanup(() => {
    disposed = true;
    table?.destroy();
    table = null;
  });

  return <div ref={host} class={props.class} />;
};

export default TabulatorGraph;
