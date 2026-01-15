import {A, useNavigate} from "@solidjs/router";
import { AuthCard } from "../../components/AuthCard";

export const LoginPage = () => {
  const navigate = useNavigate();
  return(
  <main class="w-full" aria-label="Login page">
    <AuthCard
        title="Login"
        footer={
          <div class="space-y-2">
            <p>
              Forgot your password? <A class="link link-primary" href="/recovery">Recover it here!</A>
            </p>
          </div>
        }
    >
      {/*TODO turn this into an action when auth system implemented*/}
      <form class="w-full flex flex-col gap-2 text-left"
            aria-label="Login form"
            onSubmit={() => {
              navigate("/home")
            }}
      >
        <label class="form-control w-full">
          <div class="label">
            <span class="label-text">Email</span>
          </div>
          <input
              type="email"
              placeholder="you@company.com"
              class="input opacity-70 input-bordered w-full"
              aria-label="Email"
          />
        </label>
        <label class="form-control w-full">
          <div class="label">
            <span class="label-text">Password</span>
          </div>
          <input
              type="password"
              placeholder="********"
              class="input opacity-70 input-bordered w-full"
              aria-label="Password"
          />
        </label>
        <button
            type="submit"
            class="btn btn-primary w-full text-center"
            aria-label="Submit login form"
        >
          Submit
        </button>
      </form>
    </AuthCard>
  </main>
)};
