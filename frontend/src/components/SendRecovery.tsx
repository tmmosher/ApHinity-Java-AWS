import TurnstileWidget from "./TurnstileWidget";
import {JSX} from "solid-js";
import SerializableAttributeValue = JSX.SerializableAttributeValue;

interface MailSendComponentProps {
    action: string | SerializableAttributeValue | undefined;
}

const SendRecovery = (props: MailSendComponentProps) => {
    return (
        <form
            class="w-full flex flex-col gap-4 text-left"
            aria-label="Email recovery form"
            action={props.action}
        >
            <label class="form-control w-full">
                <div class="label">
                    <span class="label-text">Email</span>
                </div>
                <input
                    type="email"
                    name="email"
                    placeholder="you@company.com"
                    class="input opacity-70 input-bordered w-full"
                    aria-label="Email"
                />
            </label>
            <TurnstileWidget/>
            <button
                type="submit"
                class="btn btn-primary w-full text-center"
                aria-label="Submit email recovery form"
            >
                Submit
            </button>
        </form>
    );
}

export default SendRecovery;