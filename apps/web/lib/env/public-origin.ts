export function browserPublicOrigin(): string {
  if (typeof window === "undefined") {
    throw new Error("browserPublicOrigin is browser-only");
  }
  return window.location.origin;
}
