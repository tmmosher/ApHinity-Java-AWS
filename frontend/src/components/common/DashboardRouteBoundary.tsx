import {A} from "@solidjs/router";
import {ErrorBoundary, ParentProps} from "solid-js";

type DashboardRouteBoundaryProps = ParentProps & {
  title: string;
  backHref?: string;
};

export const DashboardRouteBoundary = (props: DashboardRouteBoundaryProps) => (
  <ErrorBoundary fallback={(error, reset) => (
    <div class="space-y-4" role="alert" aria-live="assertive">
      <header class="space-y-1">
        <h1 class="text-3xl font-semibold tracking-tight">{props.title}</h1>
        <p class="text-error">This panel hit an unexpected error.</p>
      </header>
      <p class="text-sm text-base-content/70">
        {error instanceof Error ? error.message : "An unexpected error occurred."}
      </p>
      <div class="flex flex-wrap gap-2">
        <button type="button" class="btn btn-primary" onClick={reset}>
          Retry panel
        </button>
        <A href={props.backHref ?? "/dashboard"} class="btn btn-ghost" preload>
          Back
        </A>
      </div>
    </div>
  )}>
    {props.children}
  </ErrorBoundary>
);
