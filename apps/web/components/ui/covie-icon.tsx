import Image from "next/image";
import type { ComponentProps } from "react";

export type CoVieIconName = "home" | "explore" | "search" | "filter" | "need" | "orders" | "profile" | "cart" | "location" | "back" | "close" | "plus" | "minus" | "rating-star" | "feedback-error" | "feedback-warning";

const dimensions: Record<CoVieIconName, readonly [number, number]> = {
  home: [24, 24], explore: [24, 24], search: [20, 20], filter: [24, 24], need: [24, 24],
  orders: [24, 24], profile: [24, 24], cart: [24, 24], location: [12, 16], back: [24, 24], close: [20, 20], plus: [20, 20], minus: [20, 20], "rating-star": [16, 16], "feedback-error": [20, 20], "feedback-warning": [20, 20],
};

export function CoVieIcon({ name, active = false, ...props }: Omit<ComponentProps<typeof Image>, "src" | "alt" | "width" | "height"> & { name: CoVieIconName; active?: boolean }) {
  const [width, height] = dimensions[name];
  const variant = name === "home" || name === "explore" ? `-${active ? "active" : "default"}` : name === "need" || name === "orders" || name === "profile" ? "-default" : "";
  return <Image src={`/figma/icons/${name}${variant}.svg`} width={width} height={height} alt="" aria-hidden="true" unoptimized {...props} />;
}
