export type TabulatorLoadEventTable = {
  on: (event: string, callback: (...args: unknown[]) => void) => void;
};

type TabulatorLoadHandlers = {
  setLoading: (loading: boolean) => void;
  notifyError: (message: string) => void;
};

export const resolveTabulatorLoadErrorMessage = (error: unknown): string => {
  if (error instanceof Error && error.message) {
    return error.message;
  }
  if (error && typeof error === "object" && "status" in error) {
    const status = Number((error as {status?: unknown}).status);
    if (Number.isFinite(status) && status > 0) {
      return `Unable to load table data (${status})`;
    }
  }
  return "Unable to load table data";
};

export const registerTabulatorLoadHandlers = (
  table: TabulatorLoadEventTable,
  handlers: TabulatorLoadHandlers
) => {
  table.on("dataLoading", () => handlers.setLoading(true));
  table.on("dataLoaded", () => handlers.setLoading(false));
  table.on("renderComplete", () => handlers.setLoading(false));
  table.on("dataLoadError", (error) => {
    handlers.setLoading(false);
    handlers.notifyError(resolveTabulatorLoadErrorMessage(error));
  });
};
