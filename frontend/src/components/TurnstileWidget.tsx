// @ts-ignore
import {Turnstile, TurnstileRef} from "@nerimity/solid-turnstile";
import {toast} from "solid-toast";
import {useApiHost} from "../context/ApiHostContext";
import {createEffect} from "solid-js";

const TurnstileWidget = () => {
    const site_key = import.meta.env.VITE_TURNSTILE_SITE_KEY as string | undefined
    return <Turnstile
        sitekey={site_key}
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