// the maker of the module didn't create anything for TypeScript. This is
// needed for autocomplete and to suppress TS errors for just the Turnstile.


declare module "@nerimity/solid-turnstile" {
    import { Component } from "solid-js";

    export interface TurnstileProps {
        sitekey: string | undefined;
        theme?: "light" | "dark" | "auto";
        size?: "normal" | "compact";
        tabindex?: number;
        action?: string;
        cData?: string;
        onVerify?: (token: string) => void;
        onExpire?: () => void;
        onError?: () => void;
        onTimeout?: () => void;
    }

    export const Turnstile: Component<TurnstileProps>;
}
