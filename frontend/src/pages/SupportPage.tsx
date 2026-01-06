import { A } from "@solidjs/router";
import { AuthCard } from "../components/AuthCard";

export const SupportPage = () => (
  <main class="w-full" aria-label="Contact support page">
    <AuthCard
      title="Contact Support"
    >
      <div class="w-full text-left space-y-4">
        <p class="text-sm text-base-content/80">
          Email our support team for help with:
        </p>
        <ul class="list-disc list-inside text-sm text-base-content/70 space-y-1">
          <li>Missing verification or recovery emails</li>
          <li>Technical problems related to sign up or login</li>
          <li>Accessibility concerns or special needs</li>
        </ul>
        <a
          class="btn btn-primary w-full text-center"
          href="mailto:tmosher@aphinitytech.com"
          aria-label="Email ApHinity support"
        >
          Email Support
        </a>
        <p class="text-xs text-base-content/60">
          Include the email you use with ApHinity and any error messages.
        </p>
      </div>
    </AuthCard>
  </main>
);
