import {
  createLocationGraphById,
  deleteLocationGraphById,
  deleteLocationSectionById,
  fetchLocationById,
  fetchLocationGraphsById,
  fetchLocationSectionGraphsById,
  renameLocationGraphById,
  saveLocationGraphsById,
  uploadLocationDashboardSpreadsheetById
} from "./locationDetailApi";

/** Application-facing dashboard transport contract, supplied by the composition root. */
export type LocationDashboardGateway = {
  fetchLocation: typeof fetchLocationById;
  fetchGraphs: typeof fetchLocationGraphsById;
  fetchSectionGraphs: typeof fetchLocationSectionGraphsById;
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
  fetchSectionGraphs: fetchLocationSectionGraphsById,
  createGraph: createLocationGraphById,
  saveGraphs: saveLocationGraphsById,
  renameGraph: renameLocationGraphById,
  deleteGraph: deleteLocationGraphById,
  deleteSection: deleteLocationSectionById,
  uploadSpreadsheet: uploadLocationDashboardSpreadsheetById
};
