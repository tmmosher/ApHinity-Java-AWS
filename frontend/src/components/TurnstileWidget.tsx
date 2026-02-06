// @ts-ignore
import {Turnstile} from "@nerimity/solid-turnstile";
import {toast} from "solid-toast";

const TurnstileWidget = () => {
    const site_key = import.meta.env.VITE_TURNSTILE_SITE_KEY as string | undefined
    return <Turnstile
        size="normal"
        theme="auto"
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