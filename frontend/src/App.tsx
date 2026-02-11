import { onMount, type ParentProps } from "solid-js";
import { Toaster } from "solid-toast";
import {ApiHostProvider} from "./context/ApiHostContext";
import {initializeThemePreference} from "./util/themePreference";

export default function App(props: ParentProps) {
  onMount(() => {
    initializeThemePreference();
  });

  return (
    <div class="min-h-screen bg-base-200 flex flex-col">
      <Toaster />
      <ApiHostProvider>
          {props.children}
      </ApiHostProvider>
    </div>
  );
}
