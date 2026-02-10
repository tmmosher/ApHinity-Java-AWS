import type { ParentProps } from "solid-js";
import {ProfileProvider} from "../context/ProfileContext";

export const AuthenticatedLayout = (props: ParentProps) => (
  <div class="flex-1 flex flex-col">
      <ProfileProvider>
          <div class="flex-1 flex items-center justify-center px-4 py-12">
              {props.children}
          </div>
      </ProfileProvider>
  </div>
);
