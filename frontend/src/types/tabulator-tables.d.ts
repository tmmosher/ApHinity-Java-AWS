declare module "tabulator-tables" {
  export type TabulatorCellComponent = {
    getRow: () => {
      getData: () => Record<string, unknown>;
    };
    getElement: () => HTMLElement;
  };

  export type TabulatorRowComponent = {
    getData: () => Record<string, unknown>;
    getElement: () => HTMLElement;
  };

  export type TabulatorColumnDefinition = {
    title: string;
    field: string;
    minWidth?: number;
    widthGrow?: number;
    hozAlign?: "left" | "center" | "right";
    headerSort?: boolean;
    formatter?: (cell: TabulatorCellComponent) => string | HTMLElement;
    clickPopup?: (event: MouseEvent, cell: TabulatorCellComponent) => string | HTMLElement;
  };

  export type TabulatorOptions = {
    data?: Record<string, unknown>[];
    columns?: TabulatorColumnDefinition[];
    layout?: string;
    height?: string | number;
    reactiveData?: boolean;
    placeholder?: string;
    index?: string;
    pagination?: boolean | "local" | "remote";
    paginationMode?: "local" | "remote";
    paginationSize?: number;
    paginationSizeSelector?: number[];
    paginationCounter?: "rows" | "pages";
    rowFormatter?: (row: TabulatorRowComponent) => void;
    ajaxURL?: string;
    ajaxRequestFunc?: (
      url: string,
      config: Record<string, unknown>,
      params: Record<string, unknown>
    ) => Promise<unknown>;
    ajaxResponse?: (
      url: string,
      params: Record<string, unknown>,
      response: unknown
    ) => unknown;
  };

  export class TabulatorFull {
    constructor(element: HTMLElement, options: TabulatorOptions);
    setData(data?: Record<string, unknown>[] | string): Promise<unknown>;
    setColumns(columns: TabulatorColumnDefinition[]): void;
    destroy(): void;
  }
}
