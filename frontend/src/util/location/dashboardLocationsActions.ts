import {ActionResult, LocationSummary} from "../../types/Types";
import {
  createLocation as createLocationRequest,
  renameLocation as renameLocationRequest,
  uploadLocationThumbnail as uploadLocationThumbnailRequest
} from "../common/locationApi";

export type RenameLocationActionResult = ActionResult & {
  locationId: number;
  updatedLocation?: LocationSummary;
};

export type CreateLocationActionResult = ActionResult & {
  createdLocation?: LocationSummary;
};

export type UploadLocationThumbnailActionResult = ActionResult & {
  locationId: number;
  updatedLocation?: LocationSummary;
};

export const sortLocationsByName = (items: LocationSummary[]): LocationSummary[] =>
  [...items].sort((left, right) => left.name.localeCompare(right.name, undefined, {
    sensitivity: "base"
  }));

export const runRenameLocationAction = async (
  host: string,
  locationId: number,
  nextName: string
): Promise<RenameLocationActionResult> => {
  try {
    return {
      ok: true,
      locationId,
      updatedLocation: await renameLocationRequest(host, locationId, nextName)
    };
  } catch (error) {
    return {
      ok: false,
      locationId,
      message: error instanceof Error ? error.message : "Unable to update location name."
    };
  }
};

export const runCreateLocationAction = async (
  host: string,
  nextName: string
): Promise<CreateLocationActionResult> => {
  try {
    return {
      ok: true,
      createdLocation: await createLocationRequest(host, nextName)
    };
  } catch (error) {
    return {
      ok: false,
      message: error instanceof Error ? error.message : "Unable to create location."
    };
  }
};

export const runUploadLocationThumbnailAction = async (
  host: string,
  locationId: number,
  file: File
): Promise<UploadLocationThumbnailActionResult> => {
  try {
    return {
      ok: true,
      locationId,
      updatedLocation: await uploadLocationThumbnailRequest(host, locationId, file)
    };
  } catch (error) {
    return {
      ok: false,
      locationId,
      message: error instanceof Error ? error.message : "Unable to update location thumbnail"
    };
  }
};
