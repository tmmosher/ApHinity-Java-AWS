import {ActionResult} from "../types/Types";
import {Action} from "@solidjs/router";

interface SendVerificationProps {
    action: Action<[FormData], ActionResult>;
    email: string;
}

const SendVerification = (props: SendVerificationProps) => {
    return (
      <div>
          <form
              class="w-full flex flex-col gap-4 text-left"
              aria-label="Verification form"
              action={props.action}
              method="post"
          >
              <label class="form-control w-full" for="verifyValue">
                  <div class="label">
                      <span class="label-text">Verification code</span>
                  </div>
                  <input id="verifyValue"
                         name="verifyValue"
                         type="number"
                         aria-label="Verification code"
                         placeholder="123456"/>
              </label>
              <input type="hidden" name="email" value={props.email}/>
              <button type="submit"
                      class="btn btn-primary text-center w-full"
                      aria-label="Submit verification form"
              >
                  Submit
              </button>
          </form>
      </div>
    );
}

export default SendVerification;