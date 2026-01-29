// @ts-ignore
import {Turnstile, TurnstileRef} from "@nerimity/solid-turnstile";
import {toast} from "solid-toast";
import {useApiHost} from "../context/ApiHostContext";
import {createEffect} from "solid-js";

// pretty much a 1-1 of the docs. See https://www.npmjs.com/package/@nerimity/solid-turnstile.
const TurnstileWidget = () => {
    let ref: TurnstileRef | undefined;
    // import.meta.env.VITE_TURNSTILE_SITE_KEY as string | undefined
    return <Turnstile
        sitekey={"0x4AAAAAACT3h056peO4g-ht"}
        onVerify={() => {
            toast.success("Captcha verified successfully!");
        }}
        onError={() => {
            toast.error("Unable to verify captcha. Please retry.");
        }}
        onTimeout={() => {
            toast.error("Captcha timed out. Please retry.");
        }}
    />;
}

export default TurnstileWidget;