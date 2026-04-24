import {toast} from "solid-toast";
import {LocationSummary} from "../../types/Types";
import {
  runRenameLocationAction,
  runUploadLocationThumbnailAction
} from "./dashboardLocationsActions";

type LocationMutate = (next: (current: LocationSummary[] | undefined) => LocationSummary[] | undefined) => void;

export const createRenameLocationHandler = (
  host: string,
  mutate: LocationMutate
) => async (locationId: number, nextName: string): Promise<boolean> => {
  try {
    const result = await runRenameLocationAction(host, locationId, nextName);
    if (!result.ok || !result.updatedLocation) {
      toast.error(result.message ?? "Unable to update location name");
      return false;
    }

    mutate((current) =>
      current?.map((candidate) => (
        candidate.id === result.updatedLocation!.id ? result.updatedLocation! : candidate
      ))
    );
    return true;
  } catch (error) {
    toast.error(error instanceof Error ? error.message : "Unable to update location name");
    return false;
  }
};

export const createUploadLocationThumbnailHandler = (
  host: string,
  mutate: LocationMutate
) => async (locationId: number, file: File): Promise<boolean> => {
  try {
    const result = await runUploadLocationThumbnailAction(host, locationId, file);
    if (!result.ok || !result.updatedLocation) {
      toast.error(result.message ?? "Unable to update location thumbnail");
      return false;
    }

    mutate((current) =>
      current?.map((candidate) => (
        candidate.id === result.updatedLocation!.id ? result.updatedLocation! : candidate
      ))
    );
    return true;
  } catch (error) {
    toast.error(error instanceof Error ? error.message : "Unable to update location thumbnail");
    return false;
  }
};
