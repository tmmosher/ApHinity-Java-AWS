import { A } from "@solidjs/router";

export const Banner = () => (
  <div class="navbar bg-base-100 shadow-sm">
    <div class="navbar-start">
      <A class="btn btn-ghost text-xl normal-case" href="/">
        ApHinity Technologies
      </A>
    </div>
    <div class="navbar-end gap-2">
      <A class="btn btn-outline btn-sm md:btn-md" href="/signup">
        Sign Up
      </A>
      <A class="btn btn-primary btn-sm md:btn-md" href="/login">
        Login
      </A>
    </div>
  </div>
);
