import type { ParentProps } from "solid-js";

export const AuthenticatedLayout = (props: ParentProps) => (
  <div class="flex-1 flex flex-col">
    <div class="flex-1 flex items-center justify-center px-4 py-12">
      {props.children}
    </div>
  </div>
);
