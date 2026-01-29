import {For} from "solid-js";
import {A} from "@solidjs/router";

export type NavItem = {
    label: string;
    href?: string;
};

const SidebarNav = (props: { items: NavItem[] }) => (
    <ul class="menu mt-2">
        <For each={props.items}>
            {(item) => (
                <li>
                    {item.href ? (
                        <A href={item.href} activeClass="active" end preload>
                            {item.label}
                        </A>
                    ) : (
                        <span class="opacity-70 cursor-not-allowed" aria-disabled="true">
              {item.label}
            </span>
                    )}
                </li>
            )}
        </For>
    </ul>
);

export default SidebarNav;