const DEEP_LINK_KEYS = ["deepLink", "href", "path", "route", "billingUrl"] as const;

function safeInternalPath(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const path = value.trim();
  if (!path.startsWith("/") || path.startsWith("//")) return null;
  if (/[\u0000-\u001f\\]/.test(path)) return null;
  return path;
}

/**
 * Notification navigation is backend-owned. We only accept a relative, same-origin
 * path carried in the notification data and never derive a destination from type.
 */
export function notificationDeepLink(data: Record<string, unknown> | null | undefined): string | null {
  if (!data) return null;
  for (const key of DEEP_LINK_KEYS) {
    const path = safeInternalPath(data[key]);
    if (path) return path;
  }
  return null;
}
