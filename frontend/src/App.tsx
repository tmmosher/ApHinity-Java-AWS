import type { ParentProps } from "solid-js";
import { Toaster } from "solid-toast";
import {ApiHostProvider} from "./context/ApiHostContext";

export default function App(props: ParentProps) {
  return (
    <div class="min-h-screen bg-base-200 flex flex-col">
      <Toaster />
      <ApiHostProvider>
          {props.children}
      </ApiHostProvider>
    </div>
  );
}
