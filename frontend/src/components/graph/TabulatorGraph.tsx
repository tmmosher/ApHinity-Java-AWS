import {createEffect, onCleanup, onMount} from "solid-js";
import type {LocationDashboardTablePage, LocationGraph} from "../../types/Types";
import {createTabulatorGraphModel, type TabulatorGraphRow} from "../../util/graph/tabulatorGraph";
import "tabulator-tables/dist/css/tabulator_simple.min.css";
import type {TabulatorColumnDefinition} from "tabulator-tables";
import {fetchLocationGraphTablePageById} from "../../util/graph/locationDetailApi";

type TabulatorGraphProps = {
  graph: LocationGraph;
  apiHost?: string;
  locationId?: string;
  monthRange?: number;
  class?: string;
};

const DEFAULT_PAGE_SIZE = 10;
const PAGE_SIZE_SELECTOR = [10, 25, 50, 100];

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

const toPositiveInteger = (value: unknown, fallback: number): number => {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return fallback;
  }
  return Math.max(1, Math.trunc(value));
};

const TabulatorGraph = (props: TabulatorGraphProps) => {
  let host!: HTMLDivElement;
  let table: {setData: (rows?: Record<string, unknown>[] | string) => Promise<unknown>; setColumns: (columns: TabulatorColumnDefinition[]) => void; destroy: () => void} | null = null;
  let disposed = false;
  const pageCache = new Map<string, Promise<LocationDashboardTablePage>>();

  const model = () => createTabulatorGraphModel(props.graph);
  const canUseRemotePagination = () =>
    typeof props.apiHost === "string" && props.apiHost.length > 0
      && typeof props.locationId === "string" && props.locationId.length > 0;
  const cacheKey = (page: number, size: number) =>
    `${props.graph.id}:${props.monthRange ?? -1}:${page}:${size}`;

  const loadPage = (page: number, size: number): Promise<LocationDashboardTablePage> => {
    const key = cacheKey(page, size);
    const cached = pageCache.get(key);
    if (cached) {
      return cached;
    }
    const request = fetchLocationGraphTablePageById(
      props.apiHost ?? "",
      props.locationId ?? "",
      props.graph.id,
      page,
      size,
      props.monthRange ?? -1
    );
    pageCache.set(key, request);
    void request.catch(() => pageCache.delete(key));
    return request;
  };

  const prefetchAdjacentPages = (page: LocationDashboardTablePage) => {
    const previousPage = page.page - 1;
    const nextPage = page.page + 1;
    if (previousPage >= 1) {
      void loadPage(previousPage, page.size);
    }
    if (nextPage <= page.last_page) {
      void loadPage(nextPage, page.size);
    }
  };

  onMount(() => {
    void import("tabulator-tables").then(({TabulatorFull}) => {
      if (disposed) {
        return;
      }
      const currentModel = model();

      table = new TabulatorFull(host, {
        data: canUseRemotePagination() ? undefined : currentModel.rows,
        columns: withInteractiveColumns(currentModel.columns),
        layout: "fitDataStretch",
        height: "100%",
        placeholder: "No recent sample measurements",
        index: "rowIdentifier",
        pagination: true,
        paginationMode: canUseRemotePagination() ? "remote" : "local",
        paginationSize: DEFAULT_PAGE_SIZE,
        paginationSizeSelector: PAGE_SIZE_SELECTOR,
        paginationCounter: "rows",
        ajaxURL: canUseRemotePagination() ? "dashboard-table-page" : undefined,
        ajaxRequestFunc: async (_url, _config, params) => {
          const page = await loadPage(
            toPositiveInteger(params.page, 1),
            toPositiveInteger(params.size, DEFAULT_PAGE_SIZE)
          );
          prefetchAdjacentPages(page);
          return page;
        },
        ajaxResponse: (_url, _params, response) => response
      });
      if (canUseRemotePagination()) {
        void table.setData();
      }
    });
  });

  createEffect(() => {
    const currentTable = table;
    if (!currentTable) {
      return;
    }
    const currentModel = model();
    currentTable.setColumns(withInteractiveColumns(currentModel.columns));
    if (canUseRemotePagination()) {
      pageCache.clear();
      void currentTable.setData();
      return;
    }
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
