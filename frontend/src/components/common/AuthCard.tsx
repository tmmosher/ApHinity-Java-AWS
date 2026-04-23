import type { JSX } from "solid-js";

type AuthCardProps = {
  title: string;
  children: JSX.Element;
  footer?: JSX.Element;
};

export const AuthCard = (props: AuthCardProps) => (
    <div class="w-full max-w-sm aspect-auto mx-auto">
        <div class="card bg-base-100 shadow-md w-full h-full">
            <div class="card-body items-center text-center justify-center gap-2">
                <h2 class="card-title text-3xl font-bold">{props.title}</h2>
                {props.children}
                <div class="text-sm text-base-content/70">{props.footer}</div>
            </div>
        </div>
    </div>
);
