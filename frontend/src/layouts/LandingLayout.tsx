import type { ParentProps } from "solid-js";
import { Banner } from "../components/Banner";

export const LandingLayout = (props: ParentProps) => (
  <div class="flex-1 flex flex-col">
    <Banner />
    <div class="flex-1 flex items-center justify-center px-4 py-12">
      {props.children}
    </div>
  </div>
);
