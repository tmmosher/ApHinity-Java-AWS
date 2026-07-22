import {
  createLocationGraphById,
  deleteLocationGraphById,
  deleteLocationSectionById,
  fetchLocationById,
  fetchLocationGraphsById,
  renameLocationGraphById,
  saveLocationGraphsById,
  uploadLocationDashboardSpreadsheetById
} from "./locationDetailApi";

/** Application-facing dashboard transport contract, supplied by the composition root. */
export type LocationDashboardGateway = {
  fetchLocation: typeof fetchLocationById;
  fetchGraphs: typeof fetchLocationGraphsById;
  createGraph: typeof createLocationGraphById;
  saveGraphs: typeof saveLocationGraphsById;
  renameGraph: typeof renameLocationGraphById;
  deleteGraph: typeof deleteLocationGraphById;
  deleteSection: typeof deleteLocationSectionById;
  uploadSpreadsheet: typeof uploadLocationDashboardSpreadsheetById;
};

export const httpLocationDashboardGateway: LocationDashboardGateway = {
  fetchLocation: fetchLocationById,
  fetchGraphs: fetchLocationGraphsById,
  createGraph: createLocationGraphById,
  saveGraphs: saveLocationGraphsById,
  renameGraph: renameLocationGraphById,
  deleteGraph: deleteLocationGraphById,
  deleteSection: deleteLocationSectionById,
  uploadSpreadsheet: uploadLocationDashboardSpreadsheetById
};
