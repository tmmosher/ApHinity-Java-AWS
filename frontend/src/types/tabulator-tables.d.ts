declare module "tabulator-tables" {
  export type TabulatorCellComponent = {
    getRow: () => {
      getData: () => Record<string, unknown>;
    };
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
    paginationSize?: number;
    paginationSizeSelector?: number[];
    paginationCounter?: "rows" | "pages";
  };

  export class TabulatorFull {
    constructor(element: HTMLElement, options: TabulatorOptions);
    setData(data: Record<string, unknown>[]): Promise<unknown>;
    setColumns(columns: TabulatorColumnDefinition[]): void;
    destroy(): void;
  }
}
