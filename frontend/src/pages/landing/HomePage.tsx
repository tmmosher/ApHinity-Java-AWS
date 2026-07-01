import { A } from "@solidjs/router";
import Popover from "corvu/popover";
import { For, onCleanup, onMount } from "solid-js";
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
      "Partners manage reactive dashboards by site and treatment system to deliver clean monthly reporting."
  },
  {
    title: "Client Visibility",
    description:
      "Clients receive focused views of outcomes, trends, and risk signals without exposing partner administration controls."
  },
  {
    title: "Operations-to-Action Loop",
    description:
      "The platform tracks operational events and turns them into measurable summaries for client communication" +
      " and follow-up. "
  }
];

const CLIENT_EXAMPLES = ["Hoag Hospital", "TSMC", "HelloFresh", "Dexcom", "Leprino Foods"];

const REPORTING_SIGNALS = [
  { label: "Sites tracked", value: "42", tone: "bg-primary" },
  { label: "Open actions", value: "18", tone: "bg-warning" },
  { label: "Reports sent", value: "96%", tone: "bg-success" }
];

const TECHNOLOGY_POPUPS: TechnologyPopup[] = [
  {
    id: "water-treatment",
    title: "Engineered Water Treatment Programs",
    tagline: "Engineered Resin, Forward Osmosis, and membrane process programs.",
    paragraphs: [
      "ApHinity Technology solutions include water and air treatment technologies fit for wide applications. Trusted by a variety of technology, healthcare, food preparation, and agricultural systems.",
      "With AIM, partner teams can map performance metrics for reliability, water conservation, and compliance exceptions.",
    ]
  },
  {
    id: "oxidant-programs",
    title: "Oxidant Generation and Disinfection Programs",
    tagline: "Chlorine dioxide, Sulfur Dioxide",
    paragraphs: [
      "ApHinity oxidant generation programs cover chlorine dioxide, mixed oxidants, hypochlorite generation, and high purity generation workflows.",
    ]
  },
  {
    id: "air-programs",
    title: "Engineered Air Programs",
    tagline: "NOx/SOx abatement, odor mitigation, and airborne microbiological control.",
    paragraphs: [
      "ApHinity Technologies engineered air solutions focus on efficient removal of environmentally regulated compounds, including nitrogen oxides, sulfur oxides, and odor concerns.",
    ]
  }
];

export const HomePage = () => {
  let pageRef: HTMLElement | undefined;

  onMount(() => {
    if (!pageRef) {
      return;
    }

    const revealElements = Array.from(pageRef.querySelectorAll<HTMLElement>("[data-scroll-reveal]"));

    if (!("IntersectionObserver" in window)) {
      revealElements.forEach((element) => {
        element.dataset.scrollReveal = "visible";
      });
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) {
            return;
          }

          const element = entry.target as HTMLElement;
          element.dataset.scrollReveal = "visible";
          observer.unobserve(element);
        });
      },
      {
        root: null,
        rootMargin: "0px 0px -12% 0px",
        threshold: 0.16
      }
    );

    revealElements.forEach((element) => {
      observer.observe(element);
    });

    onCleanup(() => {
      observer.disconnect();
    });
  });

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
      toast.error(firstIssue?.message ?? "Please complete every contact field before submitting");
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
    <div class="relative isolate w-full max-w-6xl mx-auto">
      <main ref={pageRef} class="w-full space-y-8 md:space-y-10" aria-labelledby="home-title">
        <section class="relative isolate overflow-hidden rounded-md border border-base-300 bg-gradient-to-br from-base-100 via-base-100 to-base-200 shadow">
          <div class="grid gap-8 p-6 md:p-10 lg:grid-cols-[1.08fr_0.92fr] lg:items-center">
            <div class="space-y-6">
              <h1 id="home-title" class="text-4xl md:text-5xl font-bold leading-tight">
                Visual Reporting for Partner and Client Teams
              </h1>
              <span class="select-none badge badge-primary badge-outline bg-base-100/70">ApHinity Information Management (AIM) System</span>
              <p class="text-base md:text-lg text-base-content/80">
                AIM supports ApHinity management partners in communicating key performance indicators to
                clients through structured dashboards, validation workflows, and clear operational context.
              </p>
              <p class="text-sm text-base-content/70">
                AIM is intended for data presentation only. Select our products and
                services from our{" "}
                <a class="link link-primary" href="https://aphinitytech.com/">
                  website
                </a>
                !
              </p>
              <div class="flex flex-wrap gap-3">
                <A class="btn btn-primary" href="/login" preload>
                  Login
                </A>
                <a class="btn btn-outline bg-base-100/70" href="#partner-contact-form">
                  Contact Partner Team
                </a>
              </div>
            </div>
            <div class="select-none rounded-md bg-base-300/40 p-3 shadow" aria-hidden="true">
              <img src="/example-board.png" alt="Portal Visual" />
            </div>
          </div>
        </section>

        <section class="grid gap-4 md:grid-cols-2 xl:grid-cols-3" data-scroll-reveal="hidden">
          <For each={PORTAL_CAPABILITIES}>
            {(capability) => (
              <article class="card select-none rounded-md border border-base-300 bg-base-100 shadow transition hover:-translate-y-0.5 hover:border-primary/40 hover:shadow-md">
                <div class="card-body gap-3">
                  <h2 class="card-title text-xl">{capability.title}</h2>
                  <p class="text-sm text-base-content/75 leading-6">{capability.description}</p>
                </div>
              </article>
            )}
          </For>
        </section>

        <section class="select-none rounded-md border border-base-300 bg-gradient-to-r from-base-100 via-base-100 to-base-200 shadow" data-scroll-reveal="hidden">
          <div class="p-6 md:p-8 space-y-4">
            <h2 class="text-2xl font-semibold">Client Coverage</h2>
            <p class="text-sm text-base-content/75 leading-6">
              Partner reporting structures support a broad client mix, including organizations such as Hoag Hospitals, TSMC,
              HelloFresh, Dexcom, and various regulated healthcare and agricultural operations across manufacturing and processing
              environments.
            </p>
            <div class="flex flex-wrap gap-2">
              <For each={CLIENT_EXAMPLES}>
                {(client) => <span class="select-none badge badge-outline badge-lg">{client}</span>}
              </For>
            </div>
          </div>
        </section>

        <section class="space-y-4" data-scroll-reveal="hidden">
          <div class="grid overflow-hidden rounded-md border border-base-300 bg-base-100 shadow md:grid-cols-3">
            <For each={TECHNOLOGY_POPUPS}>
              {(panel, index) => (
                <Popover placement="bottom-start">
                  {(popover) => (
                    <>
                      <Popover.Trigger
                        class={`group min-h-52 w-full bg-base-100 p-6 text-left transition hover:-translate-y-0.5 hover:border-primary/40 hover:bg-base-200 hover:shadow-md focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary ${
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
                          class="z-50 w-[min(92vw,44rem)] rounded-md border border-base-300 bg-base-100 shadow-lg"
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

        <section class="grid gap-6 lg:grid-cols-5" data-scroll-reveal="hidden">
          <article
            id="partner-contact-form"
            class="card scroll-mt-28 rounded-md border border-base-300 bg-base-100 shadow transition hover:-translate-y-0.5 hover:border-primary/40 hover:shadow-md lg:col-span-3"
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

          <aside class="card rounded-md border border-base-300 bg-base-100 shadow transition hover:-translate-y-0.5 hover:border-primary/40 hover:shadow-md lg:col-span-2">
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
      <footer class="w-fit mt-8 block rounded-md border border-base-300 bg-base-100 px-4 py-3 text-sm text-base-content/70 shadow">
        <div class="flex flex-row items-start justify-start gap-3 sm:flex-row">
          <div class="flex flex-col flex-wrap items-left justify-between gap-3">
            <span class="font-medium text-base-content">© Copyright 2026 ApHinity®: Technologies. All Rights Reserved.</span>
            <a class="font-medium link link-primary" href="https://aphinitytech.com" aria-label="ApHinity Inc. website">
              Our site
            </a>
            <span class="font-medium text-base-content">Photo by {" "}
              <a href="https://unsplash.com/@unstable_affliction?utm_source=unsplash&utm_medium=referral&utm_content=creditCopyText">Ivan Bandura</a> on <a href="https://unsplash.com/photos/an-overhead-view-of-a-street-with-a-lot-of-water-6wSevhW1Dzc?utm_source=unsplash&utm_medium=referral&utm_content=creditCopyText">Unsplash</a>
          </span>
          </div>
        </div>
      </footer>
    </div>
  );
};
