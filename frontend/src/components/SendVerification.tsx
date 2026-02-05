import {Serializer} from "node:v8";
import {JSX} from "solid-js";
import SerializableAttributeValue = JSX.SerializableAttributeValue;

interface SendVerificationProps {
    action: string | SerializableAttributeValue | undefined;
    email: string;
}

const SendVerification = (props: SendVerificationProps) => {
    return (
      <div>
          <form
              class="w-full flex flex-col gap-4 text-left"
              aria-label="Verification form"
              action={props.action}
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