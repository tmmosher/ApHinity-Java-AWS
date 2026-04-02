import {createEffect, createSignal, splitProps, type JSX, type ParentProps} from "solid-js";

type GradientBorderProps = ParentProps<JSX.HTMLAttributes<HTMLDivElement> & {
  active?: boolean;
  animationKey?: number | string;
  focusMode?: "none" | "animate-once";
  innerClass?: string;
  frameClass?: string;
}>;

const GradientBorder = (props: GradientBorderProps) => {
  const [isAnimating, setIsAnimating] = createSignal(false);
  const [local, rest] = splitProps(props, [
    "active",
    "animationKey",
    "children",
    "class",
    "frameClass",
    "focusMode",
    "innerClass",
  ]);

  const containerClass = () => ["gradient-border", local.class].filter(Boolean).join(" ").trim();
  const frameClass = () => ["gradient-border-frame", local.frameClass].filter(Boolean).join(" ").trim();
  const surfaceClass = () => ["gradient-border-surface", local.innerClass].filter(Boolean).join(" ").trim();
  const handleAnimationEnd: JSX.EventHandlerUnion<HTMLDivElement, AnimationEvent> = (event) => {
    if (event.currentTarget !== event.target) {
      return;
    }
    setIsAnimating(false);
  };

  createEffect<number | string | undefined>((previousKey) => {
    const nextKey = local.animationKey;
    if (nextKey === undefined) {
      return nextKey;
    }
    if (previousKey === undefined) {
      if (nextKey !== 0 && nextKey !== "") {
        setIsAnimating(true);
      }
      return nextKey;
    }
    if (nextKey !== previousKey) {
      setIsAnimating(true);
    }
    return nextKey;
  });

  return (
    <div
      {...rest}
      data-active={local.active ? "true" : "false"}
      data-animating={isAnimating() ? "true" : "false"}
      data-focus-mode={local.focusMode ?? "none"}
      class={containerClass()}
    >
      <div
        aria-hidden="true"
        class={frameClass()}
        onAnimationEnd={handleAnimationEnd}
      />
      <div class={surfaceClass()}>
        {local.children}
      </div>
    </div>
  );
};

export default GradientBorder;
