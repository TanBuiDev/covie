export type NotificationLinkInput = {
  type: string;
  recipientRole?: "BUYER" | "SELLER" | "ADMIN" | string;
  orderId?: string;
  merchantId?: string;
  needRequestId?: string;
};

function segment(value: string | undefined): string | null {
  const normalized = value?.trim();
  return normalized ? encodeURIComponent(normalized) : null;
}

/**
 * Derives navigation exclusively from trusted notification type + typed entity ids.
 * Arbitrary URLs from notification payloads are intentionally not accepted.
 */
export function notificationDeepLink(input: NotificationLinkInput): string {
  const orderId = segment(input.orderId);
  const merchantId = segment(input.merchantId);
  const needRequestId = segment(input.needRequestId);

  if (input.type.startsWith("ORDER_")) {
    if (input.recipientRole === "SELLER" && merchantId && orderId) return `/seller/${merchantId}/orders/${orderId}`;
    if (orderId) return `/orders/${orderId}`;
  }

  if (input.type.startsWith("NEED_RESPONSE_") && needRequestId) return `/needs/${needRequestId}`;
  if (input.type === "NEED_REQUEST_FULFILLED" && needRequestId) return `/needs/${needRequestId}`;
  if (input.type === "NEED_MATCH_AVAILABLE" && merchantId) return `/seller/${merchantId}/needs`;

  if (input.type.startsWith("MODERATION_") && merchantId) return `/seller/${merchantId}/catalog`;

  return "/me/notifications";
}
