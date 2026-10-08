/** Only local application paths may be used after authentication. */
export function safeReturnTo(value: unknown): string {
  if (typeof value !== "string" || !value.startsWith("/") || value.startsWith("//")) return "/";
  if (/[\\\u0000-\u0020\u007f]/.test(value) || /%(?:2f|5c|0[0-9a-f]|1[0-9a-f]|7f)/i.test(value)) return "/";
  const url = new URL(value, "https://covie.invalid");
  if (url.origin !== "https://covie.invalid" || /^\/(?:api|login|register|reset-password|onboarding)(?:\/|$)/.test(url.pathname)) return "/";
  return `${url.pathname}${url.search}${url.hash}`;
}
