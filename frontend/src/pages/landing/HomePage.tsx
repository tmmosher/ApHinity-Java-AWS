import { A } from "@solidjs/router";
import Popover from "corvu/popover";
import { For } from "solid-js";
import { Motion } from "solid-motionone";
import { toast } from "solid-toast";
import { z } from "zod";

type TechnologyPopup = {
  id: string;
  title: string;
  tagline: string;
  paragraphs: string[];
};

const partnerContactSchema = z.object({
  fullName: z.string().trim().min(1, "Full name is required"),
  company: z.string().trim().min(1, "Company is required"),
  email: z
    .string()
    .trim()
    .min(1, "Email is required")
    .email("Please enter a valid email address."),
  message: z.string().trim().min(1, "Message is required")
});

const PORTAL_CAPABILITIES = [
  {
    title: "Partner Reporting Workspaces",
    description:
      "Management partners can structure dashboards by site, treatment system, and contract scope to deliver clean monthly reporting."
  },
  {
    title: "Client Visibility",
    description:
      "Clients receive focused views of outcomes, trends, and risk signals without exposing partner administration controls."
  },
  {
    title: "Operations-to-Action Loop",
    description:
      "The platform tracks operational events and turns them into measurable summaries for client communication and follow-up."
  }
];

const CLIENT_EXAMPLES = ["TSMC", "HelloFresh", "Dexcom", "Semiconductor Facilities", "Food Processing"];

const TECHNOLOGY_POPUPS: TechnologyPopup[] = [
  {
    id: "water-treatment",
    title: "Engineered Water Treatment Programs",
    tagline: "Engineered Resin, Forward Osmosis, and membrane process programs.",
    paragraphs: [
      "ApHinity Technology solutions include water and air treatment technologies fit for wide applications. Trusted by a variety of technology, healthcare, food preparation, and agricultural systems.",
      "With the ApHinity Management System, partner teams can map performance metrics for utilization, reliability, water conservation, and compliance exceptions.",
      "Our visualizations allow client teams to evaluate treatment outcomes with a bird's eye view to support plant-level decisions."
    ]
  },
  {
    id: "oxidant-programs",
    title: "Oxidant Generation and Disinfection Programs",
    tagline: "Chlorine dioxide, Sulfur Dioxide",
    paragraphs: [
      "ApHinity oxidant generation programs cover gas and liquid phase chlorine dioxide, mixed oxidants, hypochlorite generation, and high purity chloramine generation workflows.",
      "Portal graphs can track setpoints, treatment efficacy, intervention timing, and verification results across water and process disinfection applications.",
      "This gives client teams an auditable view of how treatment controls are being managed over time."
    ]
  },
  {
    id: "air-programs",
    title: "Engineered Air Programs",
    tagline: "NOx/SOx abatement, odor mitigation, and airborne microbiological control.",
    paragraphs: [
      "ApHinity Technologies engineered air solutions focus on environmentally regulated compounds, including nitrogen oxides, sulfur oxides, odors, and airborne microbiological concerns.",
      "In ApHinityMS, partners can publish trend views and event notes that explain root causes, corrective actions, and performance stabilization windows.",
      "This supports clear client communication on air-related risk and mitigation status."
    ]
  }
];

export const HomePage = () => {
  const submitContactForm = (event: SubmitEvent) => {
    event.preventDefault();
    const form = event.currentTarget;
    if (!(form instanceof HTMLFormElement)) {
      return;
    }

    const formData = new FormData(form);
    const parsed = partnerContactSchema.safeParse({
      fullName: String(formData.get("fullName") ?? ""),
      company: String(formData.get("company") ?? ""),
      email: String(formData.get("email") ?? ""),
      message: String(formData.get("message") ?? "")
    });

    if (!parsed.success) {
      const firstIssue = parsed.error.issues[0];
      toast.error(firstIssue?.message ?? "Please complete every contact field before submitting.");
      return;
    }

    if (typeof window === "undefined") {
      return;
    }

    const { fullName, company, email, message } = parsed.data;
    const subject = encodeURIComponent(`Partner contact request - ${company}`);
    const body = encodeURIComponent(
      [
        "New partner contact request:",
        "",
        `Full Name: ${fullName}`,
        `Company: ${company}`,
        `Email: ${email}`,
        "",
        "Message:",
        message
      ].join("\n")
    );

    window.location.href = `mailto:sales@aphinitytech.com?subject=${subject}&body=${body}`;
    form.reset();
  };

  return (
    <>
      <main class="w-full max-w-6xl mx-auto space-y-8 md:space-y-10" aria-labelledby="home-title">
        <section class="rounded-2xl border border-base-300 bg-base-100 shadow-xl">
          <div class="p-6 md:p-10 space-y-6">
            <span class="badge badge-primary badge-outline">ApHinity Management Solutions</span>
            <h1 id="home-title" class="text-4xl md:text-5xl font-bold leading-tight">
              Visual Reporting for Management Partners and Client Teams
            </h1>
            <p class="text-base md:text-lg text-base-content/80">
              AMS supports ApHinity management partners in communicating key performance indicators to
              clients through structured dashboards, validation workflows, and clear operational context.
            </p>
            <p class="text-sm text-base-content/70">
              Aphinity Management Solutions is for partner/client data presentation only. Select our products and
              services from our{" "}
              <a class="link link-primary" href="https://aphinitytech.com/">
                website
              </a>
              !
            </p>
              <div class="flex flex-wrap gap-3">
                  <A class="btn btn-primary" href="/login" preload>
                      Partner and Client Login
                  </A>
                  <a class="btn btn-outline" href="#partner-contact-form">
                      Contact Partner Team
                  </a>
            </div>
          </div>
        </section>

        <section class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          <For each={PORTAL_CAPABILITIES}>
            {(capability) => (
              <article class="card bg-base-100 border border-base-300 shadow-sm">
                <div class="card-body gap-3">
                  <h2 class="card-title text-xl">{capability.title}</h2>
                  <p class="text-sm text-base-content/75 leading-6">{capability.description}</p>
                </div>
              </article>
            )}
          </For>
        </section>

        <section class="rounded-2xl border border-base-300 bg-base-100 shadow-sm">
          <div class="p-6 md:p-8 space-y-4">
            <h2 class="text-2xl font-semibold">Client Coverage</h2>
            <p class="text-sm text-base-content/75 leading-6">
              Partner reporting structures support a broad client mix, including organizations such as TSMC,
              HelloFresh, Dexcom, and various regulated healthcare and agricultural operations across manufacturing and processing
              environments.
            </p>
            <div class="flex flex-wrap gap-2">
              <For each={CLIENT_EXAMPLES}>
                {(client) => <span class="badge badge-outline badge-lg">{client}</span>}
              </For>
            </div>
          </div>
        </section>

        <section class="space-y-4">
          <div class="grid overflow-hidden rounded-2xl border border-base-300 md:grid-cols-3">
            <For each={TECHNOLOGY_POPUPS}>
              {(panel, index) => (
                <Popover placement="bottom-start">
                  {(popover) => (
                    <>
                      <Popover.Trigger
                        class={`group w-full min-h-52 bg-base-100 p-6 text-left transition-colors hover:bg-base-200 focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary ${
                          index() > 0 ? "border-t border-base-300 md:border-l md:border-t-0" : ""
                        }`}
                      >
                        <h2 class="text-lg font-semibold">{panel.title}</h2>
                        <p class="mt-2 text-sm text-base-content/75">{panel.tagline}</p>
                      </Popover.Trigger>

                      <Popover.Portal forceMount>
                        <Popover.Content
                          forceMount
                          as={Motion.div}
                          class="z-50 w-[min(92vw,44rem)] rounded-2xl border border-base-300 bg-base-100 shadow-2xl"
                          initial={{ opacity: 0, y: 4, scale: 0.99 }}
                          animate={popover.open ? { opacity: 1, y: 0, scale: 1 } : { opacity: 0, y: 4, scale: 0.99 }}
                          transition={{ duration: 0.12, easing: "ease-out" }}
                          style={{ "pointer-events": popover.open ? "auto" : "none" }}
                        >
                          <div class="space-y-5 p-6 md:p-7">
                            <div class="flex items-start justify-between gap-4">
                              <div class="space-y-2">
                                <Popover.Label class="text-xl font-semibold leading-tight">{panel.title}</Popover.Label>
                                <Popover.Description class="text-sm text-base-content/70">
                                  {panel.tagline}
                                </Popover.Description>
                              </div>
                              <Popover.Close class="btn btn-ghost btn-sm">Close</Popover.Close>
                            </div>
                            <div class="max-h-72 space-y-4 overflow-y-auto pr-1 text-sm leading-6 text-base-content/80">
                              <For each={panel.paragraphs}>{(paragraph) => <p>{paragraph}</p>}</For>
                            </div>
                          </div>
                        </Popover.Content>
                      </Popover.Portal>
                    </>
                  )}
                </Popover>
              )}
            </For>
          </div>
        </section>

        <section class="grid gap-6 lg:grid-cols-5">
          <article
            id="partner-contact-form"
            class="card scroll-mt-28 lg:col-span-3 border border-base-300 bg-base-100 shadow-sm"
          >
            <div class="card-body">
              <h2 class="card-title text-2xl">Partner Contact Form</h2>
              <p class="text-sm text-base-content/75">
                Send onboarding requests for partner reporting workspaces.
              </p>
              <form class="grid gap-4 sm:grid-cols-2" onSubmit={submitContactForm}>
                <label class="form-control w-full">
                  <span class="label-text text-sm">Full Name</span>
                  <input
                    type="text"
                    name="fullName"
                    placeholder="Jane Smith"
                    class="input input-bordered w-full"
                    aria-label="Full name"
                  />
                </label>
                <label class="form-control w-full">
                  <span class="label-text text-sm">Company</span>
                  <input
                    type="text"
                    name="company"
                    placeholder="Partner or Client Company"
                    class="input input-bordered w-full"
                    aria-label="Company"
                  />
                </label>
                <label class="form-control w-full sm:col-span-2">
                  <span class="label-text text-sm">Email</span>
                  <input
                    type="email"
                    name="email"
                    placeholder="you@company.com"
                    class="input input-bordered w-full"
                    aria-label="Email"
                  />
                </label>
                <label class="form-control w-full sm:col-span-2">
                  <span class="label-text text-sm">Message</span>
                  <textarea
                    name="message"
                    placeholder="Describe the reporting scope, facilities, and timeline."
                    class="textarea textarea-bordered w-full min-h-28"
                    aria-label="Message"
                  />
                </label>
                <button type="submit" class="btn btn-primary sm:col-span-2">
                  Submit Request
                </button>
              </form>
            </div>
          </article>

          <aside class="card lg:col-span-2 border border-base-300 bg-base-100 shadow-sm">
              <div class="card-body gap-5">
                  <h2 class="card-title text-2xl">Office and Support</h2>
                  <div class="space-y-2 text-sm text-base-content/80">
                      <p class="font-semibold text-base-content">California</p>
                      <address class="not-italic">
                          921 North Harbor Blvd, Unit #466
                          <br/>
                          La Habra, CA 90631 {" "}
                          <a class="link link-primary" href="https://maps.app.goo.gl/5Ppe8SKxyw6BzzJK6">Maps</a>
                      </address>
                      <p class="font-semibold text-base-content">Arizona</p>
                      <address class="not-italic">
                          1548 N Tech Blvd, Suite #101
                          <br/>
                          Gilbert, AZ 85233 {" "}
                          <a class="link link-primary" href="https://maps.app.goo.gl/eq4yVgsycKCsdgDY8">Maps</a>
                      </address>
                  </div>
                  <div class="space-y-2 text-sm text-base-content/80">
                      <p class="font-semibold text-base-content">Contact</p>
                      <a class="link link-primary" href="tel:+19512728662">
                          (951) 272-8662
                      </a>
                  </div>
                  <div class="space-y-2 text-sm text-base-content/80">
                      <a class="link link-primary" href="mailto:sales@aphinitytech.com">
                          sales@aphinitytech.com
                      </a>
                  </div>
                  <div class="space-y-2 text-sm text-base-content/80">
                      <a class="link link-primary" href="mailto:help@aphinityms.com">
                          help@aphinityms.com
                      </a>
                  </div>
              </div>
          </aside>
        </section>
      </main>
    </>
  );
};
