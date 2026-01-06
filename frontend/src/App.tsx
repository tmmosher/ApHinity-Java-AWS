import type { ParentProps } from "solid-js";

export default function App(props: ParentProps) {
  return (
    <div class="min-h-screen bg-base-200 flex flex-col">
      {props.children}
    </div>
  );
}
