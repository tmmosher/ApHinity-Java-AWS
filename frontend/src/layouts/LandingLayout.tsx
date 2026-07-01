import type { ParentProps } from "solid-js";
import { useLocation } from "@solidjs/router";
import { Banner } from "../components/common/Banner";

export const LandingLayout = (props: ParentProps) => {
  const location = useLocation();
  const isHomeRoute = () => location.pathname === "/";

  return (
    <div class="flex-1 flex flex-col">
      <Banner />
      <div
        class="flex-1 w-full px-4 py-8 md:py-12"
        classList={{
          "flex items-center justify-center": !isHomeRoute(),
          "bg-[linear-gradient(rgb(255_255_255/0.62),rgb(255_255_255/0.62)),url('/bg-splash.jpg')] bg-cover bg-center bg-fixed dark:bg-[linear-gradient(rgb(0_0_0/0.42),rgb(0_0_0/0.42)),url('/bg-splash.jpg')]": isHomeRoute()
        }}
      >
        {props.children}
      </div>
    </div>
  );
};
