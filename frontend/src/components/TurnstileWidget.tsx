import { toast } from "solid-toast";
// @ts-ignore
import Turnstile from "solid-turnstile";

const siteKey = (import.meta.env.VITE_TURNSTILE_SITE_KEY as string | undefined)?.trim();

const TurnstileWidget = () => {
    if (!siteKey) {
        console.error("[Turnstile] Missing VITE_TURNSTILE_SITE_KEY.");
        return (
            <p class="text-xs text-error">
                Captcha is unavailable. Please contact support.
            </p>
        );
    }

    return <Turnstile
        sitekey={siteKey}
        onError={() => {
            toast.error("Captcha failed to load. Refresh and try again.");
        }}
        onVerify={() => {
            toast.success("Captcha verified successfully!");
        }}
    />
}

export default TurnstileWidget;
