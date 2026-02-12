import { lazy } from "solid-js";
import { render } from "solid-js/web";
import { Route, Router } from "@solidjs/router";
import App from "./App";
import "./index.css";

const root = document.getElementById("root");

const LandingLayout = lazy(() =>
  import("./layouts/LandingLayout").then((module) => ({
    default: module.LandingLayout
  }))
);
const AuthenticatedLayout = lazy(() =>
  import("./layouts/AuthenticatedLayout").then((module) => ({
    default: module.AuthenticatedLayout
  }))
);
const HomePage = lazy(() =>
  import("./pages/landing/HomePage").then((module) => ({
    default: module.HomePage
  }))
);
const LoginPage = lazy(() =>
  import("./pages/landing/LoginPage").then((module) => ({
    default: module.LoginPage
  }))
);
const SignupPage = lazy(() =>
  import("./pages/landing/SignupPage").then((module) => ({
    default: module.SignupPage
  }))
);
const RecoveryPage = lazy(() =>
  import("./pages/landing/RecoveryPage").then((module) => ({
    default: module.RecoveryPage
  }))
);
const SupportPage = lazy(() =>
  import("./pages/landing/SupportPage").then((module) => ({
    default: module.SupportPage
  }))
);
const ErrorPage = lazy(() =>
  import("./pages/landing/ErrorPage").then((module) => ({
    default: module.ErrorPage
  }))
);
const Dashboard = lazy(() =>
  import("./pages/authenticated/Dashboard").then((module) => ({
    default: module.Dashboard
  }))
);
const DashboardHomePanel = lazy(() =>
  import("./pages/authenticated/panels/DashboardHomePanel").then((module) => ({
    default: module.DashboardHomePanel
  }))
);
const DashboardProfilePanel = lazy(() =>
  import("./pages/authenticated/panels/DashboardProfilePanel").then((module) => ({
    default: module.DashboardProfilePanel
  }))
);
const DashboardLocationsPanel = lazy(() =>
  import("./pages/authenticated/panels/DashboardLocationsPanel").then((module) => ({
    default: module.DashboardLocationsPanel
  }))
);
const DashboardLocationDetailPanel = lazy(() =>
  import("./pages/authenticated/panels/DashboardLocationDetailPanel").then((module) => ({
    default: module.DashboardLocationDetailPanel
  }))
);
const DashboardInvitesPanel = lazy(() =>
  import("./pages/authenticated/panels/DashboardInvitesPanel").then((module) => ({
    default: module.DashboardInvitesPanel
  }))
);
const DashboardInviteUsersPanel = lazy(() =>
  import("./pages/authenticated/panels/DashboardInviteUsersPanel").then((module) => ({
    default: module.DashboardInviteUsersPanel
  }))
);
const DashboardPermissionsPanel = lazy(() =>
  import("./pages/authenticated/panels/DashboardPermissionsPanel").then((module) => ({
    default: module.DashboardPermissionsPanel
  }))
);
const DashboardManagementPanel = lazy(() =>
  import("./pages/authenticated/panels/DashboardManagementPanel").then((module) => ({
    default: module.DashboardManagementPanel
  }))
);

if (root) {
  render(
    () => (
      <Router root={App} preload>
        <Route path="/" component={LandingLayout}>
          <Route path="/" component={HomePage} />
          <Route path="/login" component={LoginPage} />
          <Route path="/signup" component={SignupPage} />
          <Route path="/support" component={SupportPage} />
          <Route path="/error" component={ErrorPage} />
          <Route path="/recovery">
            <Route path="/" component={RecoveryPage} />
          </Route>
          <Route path="*404" component={ErrorPage} />
        </Route>
        <Route path="/dashboard" component={AuthenticatedLayout}>
          <Route path="/" component={Dashboard}>
            <Route path="/" component={DashboardHomePanel} />
            <Route path="/locations" component={DashboardLocationsPanel} />
            <Route path="/locations/:locationId" component={DashboardLocationDetailPanel} />
            <Route path="/invites" component={DashboardInvitesPanel} />
            <Route path="/invite-users" component={DashboardInviteUsersPanel} />
            <Route path="/permissions" component={DashboardPermissionsPanel} />
            <Route path="/management" component={DashboardManagementPanel} />
            <Route path="/profile" component={DashboardProfilePanel} />
          </Route>
        </Route>
      </Router>
    ),
    root
  );
}
