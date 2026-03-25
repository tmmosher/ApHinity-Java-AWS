import type {Component} from "solid-js";
import {DashboardRouteBoundary} from "./DashboardRouteBoundary";

export const withDashboardRouteBoundary = (
  Panel: Component<any>,
  title: string,
  backHref = "/dashboard"
): Component<any> => {
  return (props) => (
    <DashboardRouteBoundary title={title} backHref={backHref}>
      <Panel {...props} />
    </DashboardRouteBoundary>
  );
};
