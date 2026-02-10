import type { ParentProps } from "solid-js";
import { useLocation } from "@solidjs/router";
import { Banner } from "../components/Banner";

export const LandingLayout = (props: ParentProps) => {
  const location = useLocation();
  const isHomeRoute = () => location.pathname === "/";

  return (
    <div class="flex-1 flex flex-col">
      <Banner />
      <div
        class="flex-1 w-full px-4 py-8 md:py-12"
        classList={{
          "flex items-center justify-center": !isHomeRoute()
        }}
      >
        {props.children}
      </div>
    </div>
  );
};
