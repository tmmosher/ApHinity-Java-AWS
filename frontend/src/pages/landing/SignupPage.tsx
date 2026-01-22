import {A, action, useNavigate, useSubmission} from "@solidjs/router";
import { AuthCard } from "../../components/AuthCard";
import {toast} from "solid-toast";
import {createEffect} from "solid-js";

const host = "http://localhost:8080";

export const SignupPage = () => {
    const submitSignup = action(async (formData: FormData) => {
        //TODO Zod schema validation before client submission
        try {
            const response = await fetch(host + "/api/auth/signup", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    name: formData.get("name"),
                    email: formData.get("email"),
                    password: formData.get("password")
                })
            });
            if (response.ok) {
                return { ok: true };
            }
            const errorBody = await response.json().catch(() => null);
            return { ok: false, message: errorBody?.message ?? "Signup failed" };
        } catch (error) {
            return { ok: false, message: "Signup failed" };
        }
    }, "submitSignup");

    const submission = useSubmission(submitSignup);
    const navigate = useNavigate();

    createEffect(() => {
        const result = submission.result;
        if (!result) {
            return;
        }
        if (result.ok) {
            toast.success("Account created successfully!");
            navigate("/login");
            return;
        }
        toast.error(result.message ?? "Signup failed");
    });

    return (
      <main class="w-full" aria-label="Sign up page">
          <AuthCard
              title="Sign Up"
              footer={
                  <div class="space-y-2 text-left">
                      <p>
                          Need help? <A class="link link-primary" href="/support">Contact support here!</A>
                      </p>
                  </div>
              }
          >
              <form method="post"
                    action={submitSignup}
                    class="w-full flex flex-col gap-4 text-left"
                    aria-label="Sign up form">
                  <label class="form-control w-full">
                      <div class="label">
                          <span class="label-text">Full name</span>
                      </div>
                      <input
                          type="text"
                          name="name"
                          placeholder="John Doe"
                          class="input opacity-70 input-bordered w-full"
                          aria-label="Full name"
                      />
                  </label>
                  <label class="form-control w-full">
                      <div class="label">
                          <span class="label-text">Work email</span>
                      </div>
                      <input
                          type="email"
                          name="email"
                          placeholder="john@company.com"
                          class="input opacity-70 input-bordered w-full"
                          aria-label="Work email"
                      />
                  </label>
                  <label class="form-control w-full">
                      <div class="label">
                          <span class="label-text">Password</span>
                      </div>
                      <input
                          type="password"
                          name="password"
                          placeholder="A secure password"
                          class="input opacity-70 input-bordered w-full"
                          aria-label="Password"
                      />
                  </label>
                  <button
                      type="submit"
                      class="btn btn-primary w-full text-center"
                      aria-label="Submit sign up form"
                  >
                      Submit
                  </button>
              </form>
          </AuthCard>
      </main>
    );
};
