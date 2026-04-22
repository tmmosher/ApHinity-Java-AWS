declare module "frappe-gantt" {
  export interface FrappeGanttViewModeConfig {
    name: string;
    padding?: string | readonly [string, string];
    step?: string;
    date_format?: string;
    column_width?: number;
    lower_text?: string | ((currentDate: Date, previousDate: Date | null | undefined, lang: string) => string);
    upper_text?: string | ((currentDate: Date, previousDate: Date | null | undefined, lang: string) => string);
    upper_text_frequency?: number;
    thick_line?: (currentDate: Date) => boolean;
    snap_at?: string;
  }

  export type FrappeGanttViewMode = "Day" | "Week" | "Month" | "Year" | string | FrappeGanttViewModeConfig;

  export interface FrappeGanttTask {
    id: string;
    name: string;
    start: string;
    end: string;
    progress: number;
    custom_class?: string;
    description?: string | null;
    [key: string]: unknown;
  }

  export interface FrappeGanttOptions {
    readonly?: boolean;
    readonly_dates?: boolean;
    readonly_progress?: boolean;
    popup?: (task: FrappeGanttTask) => string | false | undefined;
    popup_on?: "click" | "hover";
    on_click?: (task: FrappeGanttChartTask) => void;
    view_mode?: FrappeGanttViewMode;
    date_format?: string;
    container_height?: "auto" | number;
    view_mode_select?: boolean;
    scroll_to?: "today" | "start" | "end" | string;
    infinite_padding?: false | boolean;
    view_modes?: FrappeGanttViewMode[];
  }

  export interface FrappeGanttBar {
    group: SVGGElement;
    $bar: SVGRectElement;
  }

  export default class Gantt {
    constructor(wrapper: string | HTMLElement, tasks: FrappeGanttTask[], options?: FrappeGanttOptions);
    refresh(tasks?: FrappeGanttTask[]): void;
    update_options(newOptions: Partial<FrappeGanttOptions>): void;
    change_view_mode(mode: FrappeGanttViewMode): void;
    get_bar(id: string): FrappeGanttBar | undefined;
  }
}
