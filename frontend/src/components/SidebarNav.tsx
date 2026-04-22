import {A, useLocation} from "@solidjs/router";
import {createEffect, createSignal, For} from "solid-js";
import {isDashboardNavItemActive} from "../pages/authenticated/dashboardConfig";
import GradientBorder from "./GradientBorder";

export type NavItem = {
  label: string;
  href?: string;
};

const baseNavItemClass = "flex min-h-11 w-full items-center rounded-[inherit] px-3 py-2.5 text-sm font-medium transition-[background-color,color,box-shadow] duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/35 focus-visible:ring-inset";
const inactiveNavItemClass = "text-base-content/72 hover:bg-base-200/70 hover:text-base-content";
const activeNavItemClass = "bg-base-200/85 text-base-content";
const disabledNavItemClass = "flex min-h-11 w-full items-center rounded-xl px-3 py-2.5 text-sm font-medium text-base-content/45";

const SidebarNavLink = (props: { item: NavItem; pathname: string }) => {
  const [animationKey, setAnimationKey] = createSignal(0);
  const href = () => props.item.href ?? "";
  const isActive = () => props.item.href !== undefined && isDashboardNavItemActive(href(), props.pathname);

  createEffect<boolean>((wasActive) => {
    const nextActive = isActive();
    if (nextActive && !wasActive) {
      setAnimationKey((currentKey) => currentKey + 1);
    }
    return nextActive;
  }, false);

  return (
    <GradientBorder
      active={isActive()}
      animationKey={animationKey()}
      class="w-full"
      data-dashboard-nav-item={href()}
    >
      <A
        aria-current={isActive() ? "page" : undefined}
        class={`${baseNavItemClass} ${isActive() ? activeNavItemClass : inactiveNavItemClass}`}
        data-dashboard-nav-link={href()}
        end
        href={href()}
        preload
      >
        {props.item.label}
      </A>
    </GradientBorder>
  );
};

const SidebarNav = (props: { items: NavItem[] }) => {
  const location = useLocation();

  return (
    <ul class="mt-3 flex flex-col gap-2">
      <For each={props.items}>
        {(item) => (
          <li>
            {item.href ? (
              <SidebarNavLink item={item} pathname={location.pathname} />
            ) : (
              <span class={disabledNavItemClass} aria-disabled="true">
                {item.label}
              </span>
            )}
          </li>
        )}
      </For>
    </ul>
  );
};

export default SidebarNav;
