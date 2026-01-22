import type { ParentProps } from "solid-js";
import { Toaster } from "solid-toast";

export default function App(props: ParentProps) {
  return (
    <div class="min-h-screen bg-base-200 flex flex-col">
      <Toaster />
      {props.children}
    </div>
  );
}
