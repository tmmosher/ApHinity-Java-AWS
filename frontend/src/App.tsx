import { Route, Router } from "@solidjs/router";
import { Banner } from "./components/Banner";
import { HomePage } from "./pages/HomePage";
import { LoginPage } from "./pages/LoginPage";
import { SignupPage } from "./pages/SignupPage";
import { RecoveryPage } from "./pages/RecoveryPage";

export default function App() {
  return (
    <div class="min-h-screen bg-base-200 flex flex-col">
      <Banner />
      <div class="flex-1 flex items-center justify-center px-4 py-12">
          <Route path="/" component={HomePage} />
          <Route path="/login" component={LoginPage} />
          <Route path="/signup" component={SignupPage} />
          <Route path="/recovery" component={RecoveryPage} />
      </div>
    </div>
  );
}
