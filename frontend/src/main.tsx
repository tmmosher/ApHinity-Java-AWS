import { render } from "solid-js/web";
import {Route, Router} from "@solidjs/router";
import App from "./App";
import "./index.css";

const root = document.getElementById("root");

if (root) {
  render(
    () => (
      <Router>
        <Route path="" component={App}/>
      </Router>
    ),
    root
  );
}
