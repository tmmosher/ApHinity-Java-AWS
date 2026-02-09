import TurnstileWidget from "./TurnstileWidget";
import {Setter, Show} from "solid-js";
import {Action} from "@solidjs/router";
import { ActionResult } from "../types/Types";
import { getRecoverySubmitButtonClass } from "../util/recoverySubmissionControl";

interface MailSendComponentProps {
    action: Action<[FormData], ActionResult>;
    setEmail: Setter<string>;
    isSubmitDisabled: boolean;
    turnstileInstance: number;
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
            <Show when={props.turnstileInstance} keyed>
                <TurnstileWidget/>
            </Show>
            <button
                type="submit"
                class={getRecoverySubmitButtonClass(props.isSubmitDisabled)}
                aria-label="Submit email recovery form"
                disabled={props.isSubmitDisabled}
            >
                Submit
            </button>
        </form>
    );
}

export default SendRecovery;
