import { render } from "solid-js/web";
import { Route, Router } from "@solidjs/router";
import App from "./App";
import { HomePage } from "./pages/landing/HomePage";
import { LoginPage } from "./pages/landing/LoginPage";
import { SignupPage } from "./pages/landing/SignupPage";
import { RecoveryPage } from "./pages/landing/RecoveryPage";
import { SupportPage } from "./pages/landing/SupportPage";
import { Dashboard } from "./pages/authenticated/Dashboard";
import "./index.css";

const root = document.getElementById("root");

if (root) {
// TODO change this to lazy loading
  render(
    () => (
      <Router root={App}>
        <Route path="/" component={HomePage} />
        <Route path="/login" component={LoginPage} />
        <Route path="/signup" component={SignupPage} />
        <Route path="/recovery" component={RecoveryPage} />
        <Route path="/support" component={SupportPage} />
        <Route path="/home" component={Dashboard} />
      </Router>
    ),
    root
  );
}
