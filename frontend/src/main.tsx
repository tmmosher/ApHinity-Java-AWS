import { render } from "solid-js/web";
import { Route, Router } from "@solidjs/router";
import App from "./App";
import { HomePage } from "./pages/HomePage";
import { LoginPage } from "./pages/LoginPage";
import { SignupPage } from "./pages/SignupPage";
import { RecoveryPage } from "./pages/RecoveryPage";
import { SupportPage } from "./pages/SupportPage";
import "./index.css";

const root = document.getElementById("root");

if (root) {
  render(
    () => (
      <Router root={App}>
        <Route path="/" component={HomePage} />
        <Route path="/login" component={LoginPage} />
        <Route path="/signup" component={SignupPage} />
        <Route path="/recovery" component={RecoveryPage} />
        <Route path="/support" component={SupportPage} />
      </Router>
    ),
    root
  );
}
