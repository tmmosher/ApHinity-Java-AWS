import type { ParentProps } from "solid-js";
import {ProfileProvider} from "../context/ProfileContext";
import {LocationProvider} from "../context/LocationContext";

export const AuthenticatedLayout = (props: ParentProps) => (
  <div class="flex-1 flex flex-col">
      <ProfileProvider>
          <LocationProvider>
              <div class="flex-1 px-4 py-8">
                  {props.children}
              </div>
          </LocationProvider>
      </ProfileProvider>
  </div>
);
