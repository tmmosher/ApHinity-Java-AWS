import TurnstileWidget from "./TurnstileWidget";
import {Setter} from "solid-js";
import {Action} from "@solidjs/router";
import { ActionResult } from "../types/Types";

interface MailSendComponentProps {
    action: Action<[FormData], ActionResult>;
    setEmail: Setter<string>;
}

const SendRecovery = (props: MailSendComponentProps) => {
    return (
        <form
            class="w-full flex flex-col gap-4 text-left"
            method="post"
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
                    onInput={(e) => props.setEmail(e.currentTarget.value)}
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