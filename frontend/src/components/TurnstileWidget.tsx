import { toast } from "solid-toast";
// @ts-ignore
import Turnstile from "solid-turnstile";

const TurnstileWidget = () => {
    const site_key = import.meta.env.VITE_TURNSTILE_SITE_KEY as string | undefined
    return <Turnstile
        sitekey={site_key}
        onVerify={() => {
            toast.success("Captcha verified successfully!");
        }}
    />
}

export default TurnstileWidget;