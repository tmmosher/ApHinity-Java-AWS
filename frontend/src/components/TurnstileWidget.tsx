// @ts-ignore
import {Turnstile} from "solid-turnstile";
import {toast} from "solid-toast";
import {useApiHost} from "../context/ApiHostContext";

const host = useApiHost();

const TurnstileWidget = () => {
    return <Turnstile
        sitekey={process.env.VITE_TURNSTILE_SITE_KEY as string | undefined}
        onVerify={async (token: string) => {
            const response = await fetch(host+"/api/auth/captcha", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    captchaToken: token
                })
            });
        }}
        onError={() => {
            toast.error("Unable to verify captcha. Please retry.");
        }}
        onExpire={() => {
            toast.error("Captcha expired. Please retry.");
        }}
    />;
}

export default TurnstileWidget;