import type { ParentProps } from "solid-js";
import { Banner } from "./components/Banner";

export default function App(props: ParentProps) {
  return (
    <div class="min-h-screen bg-base-200 flex flex-col">
      <Banner />
      <div class="flex-1 flex items-center justify-center px-4 py-12">
        {props.children}
      </div>
    </div>
  );
}
