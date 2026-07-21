import {Show, createEffect, createSignal, onCleanup, onMount} from "solid-js";
import {toast} from "solid-toast";
import type {LocationDashboardTablePage, LocationGraph} from "../../types/Types";
import {createTabulatorGraphModel, type TabulatorGraphRow} from "../../util/graph/tabulatorGraph";
import tabulatorSimpleThemeUrl from "tabulator-tables/dist/css/tabulator_simple.min.css?url";
import tabulatorMidnightThemeUrl from "tabulator-tables/dist/css/tabulator_midnight.min.css?url";
import {registerTabulatorLoadHandlers} from "../../util/graph/tabulatorGraphLoading";
import GraphLoadingPlaceholder from "./GraphLoadingPlaceholder";
import "tabulator-tables/dist/css/tabulator_simple.min.css";
import type {TabulatorColumnDefinition} from "tabulator-tables";
import {fetchLocationGraphTablePageById} from "../../util/graph/locationDetailApi";
import {getDocumentThemePreference, type ThemePreference} from "../../util/common/themePreference";

type TabulatorGraphProps = {
  graph: LocationGraph;
  apiHost?: string;
  locationId?: string;
  monthRange?: number;
  class?: string;
};

const DEFAULT_PAGE_SIZE = 19;
const PAGE_SIZE_SELECTOR = [19, 30, 50, 100];
const TABULATOR_THEME_LINK_ID = "aphinity-tabulator-theme";
export const MINIMUM_TABULATOR_WIDTH_PX = 320;

const getTabulatorThemeUrl = (themePreference: ThemePreference): string =>
  themePreference === "dark" ? tabulatorMidnightThemeUrl : tabulatorSimpleThemeUrl;

const applyTabulatorThemeStylesheet = (themePreference: ThemePreference): void => {
  if (typeof document === "undefined") {
    return;
  }

  const themeUrl = getTabulatorThemeUrl(themePreference);
  let link = document.getElementById(TABULATOR_THEME_LINK_ID) as HTMLLinkElement | null;
  if (!link) {
    link = document.createElement("link");
    link.id = TABULATOR_THEME_LINK_ID;
    link.rel = "stylesheet";
    document.head.appendChild(link);
  }
  if (link.getAttribute("href") !== themeUrl) {
    link.href = themeUrl;
  }
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

const formatStatusRows = (row: {getData: () => Record<string, unknown>; getElement: () => HTMLElement}) => {
  const status = String(row.getData().caStatus ?? "");
  const element = row.getElement();
  element.classList.remove("aphinity-ca-active-row", "aphinity-ca-resolved-row");
  if (status === "Active") {
    element.classList.add("aphinity-ca-active-row");
  }
  if (status === "Resolved") {
    element.classList.add("aphinity-ca-resolved-row");
  }
};

const toPositiveInteger = (value: unknown, fallback: number): number => {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return fallback;
  }
  return Math.max(1, Math.trunc(value));
};

const TabulatorGraph = (props: TabulatorGraphProps) => {
  let host!: HTMLDivElement;
  let table: {on: (event: string, callback: (...args: unknown[]) => void) => void; setData: (rows?: Record<string, unknown>[] | string) => Promise<unknown>; setColumns: (columns: TabulatorColumnDefinition[]) => void; redraw: (force?: boolean) => void; destroy: () => void} | null = null;
  let disposed = false;
  let disconnectThemeObserver: (() => void) | undefined;
  let disconnectResizeObserver: (() => void) | undefined;
  const [themePreference, setThemePreference] = createSignal<ThemePreference>(getDocumentThemePreference());
  const pageCache = new Map<string, Promise<LocationDashboardTablePage>>();
  const [isLoading, setIsLoading] = createSignal(true);

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
    setThemePreference(getDocumentThemePreference());
    applyTabulatorThemeStylesheet(getDocumentThemePreference());

    const observer = new MutationObserver(() => {
      setThemePreference(getDocumentThemePreference());
    });
    observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ["data-theme"]
    });
    disconnectThemeObserver = () => observer.disconnect();

    void import("tabulator-tables").then(({TabulatorFull}) => {
      if (disposed) {
        return;
      }
      const currentModel = model();

      table = new TabulatorFull(host, {
        data: canUseRemotePagination() ? undefined : currentModel.rows,
        columns: withInteractiveColumns(currentModel.columns),
        layout: "fitColumns",
        height: "100%",
        placeholder: "No recent sample measurements",
        index: "rowIdentifier",
        pagination: true,
        paginationMode: canUseRemotePagination() ? "remote" : "local",
        paginationSize: DEFAULT_PAGE_SIZE,
        paginationSizeSelector: PAGE_SIZE_SELECTOR,
        paginationCounter: "rows",
        rowFormatter: formatStatusRows,
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
      registerTabulatorLoadHandlers(table, {
        setLoading: setIsLoading,
        notifyError: toast.error
      });
      if (typeof ResizeObserver !== "undefined") {
        const resizeObserver = new ResizeObserver(() => table?.redraw(true));
        resizeObserver.observe(host);
        disconnectResizeObserver = () => resizeObserver.disconnect();
      }
      if (canUseRemotePagination()) {
        void table.setData();
      } else {
        setIsLoading(false);
      }
    });
  });

  createEffect(() => {
    applyTabulatorThemeStylesheet(themePreference());
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
    disconnectThemeObserver?.();
    disconnectResizeObserver?.();
    table?.destroy();
    table = null;
  });

  return (
    <div class={"relative min-w-0 overflow-x-auto " + (props.class ?? "")}>
      <div
        ref={host}
        class={"h-full w-full aphinity-tabulator [&_.aphinity-ca-active-row_.tabulator-cell]:!bg-error/20 [&_.aphinity-ca-resolved-row_.tabulator-cell]:!bg-success/20 " + (props.class ?? "")}
        style={{"min-width": `${MINIMUM_TABULATOR_WIDTH_PX}px`}}
      />
      <Show when={isLoading()}>
        <div class="absolute inset-0 z-10 bg-base-100" data-tabulator-loading-placeholder="">
          <GraphLoadingPlaceholder graphName={props.graph.name} />
        </div>
      </Show>
    </div>
  );
};

export default TabulatorGraph;
