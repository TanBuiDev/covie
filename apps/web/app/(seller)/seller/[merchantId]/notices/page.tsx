import type { components } from "@covie/api-client";
import { listCatalogItemsForServer } from "@/features/catalog/catalog-server-api";
import { SellerModerationNotices, type SellerModerationNotice } from "@/features/seller/seller-moderation-notices";
import { readApiError } from "@/lib/api/api-error";
import { createServerReadClient } from "@/lib/api/server-api-client";

type Notification = components["schemas"]["NotificationResponse"];

function dataString(data: Notification["data"], key: string) {
  if (!data || typeof data !== "object") return undefined;
  const value = (data as Record<string, unknown>)[key];
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

export default async function SellerNoticesPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const client = await createServerReadClient();
  const [notificationResult, catalogItems] = await Promise.all([
    client.GET("/api/v1/me/notifications", { params: { query: { limit: 50 } } }),
    listCatalogItemsForServer(merchantId),
  ]);

  if (!notificationResult.response.ok || !notificationResult.data) {
    throw await readApiError(notificationResult.response);
  }

  const catalogIds = new Set(catalogItems.flatMap((item) => item.id ? [item.id] : []));
  const notices: SellerModerationNotice[] = (notificationResult.data.items ?? []).flatMap((notification) => {
    if (!notification.id || notification.type !== "MODERATION_UPDATED") return [];
    const targetType = dataString(notification.data, "targetType");
    const targetId = dataString(notification.data, "targetId");
    const belongsToMerchant = targetType === "MERCHANT" && targetId === merchantId;
    const belongsToCatalog = targetType === "CATALOG_ITEM" && Boolean(targetId && catalogIds.has(targetId));
    if (!belongsToMerchant && !belongsToCatalog) return [];
    return [{
      id: notification.id,
      read: notification.read ?? false,
      createdAt: notification.createdAt,
      targetType: targetType as SellerModerationNotice["targetType"],
    }];
  });

  return <SellerModerationNotices merchantId={merchantId} initialNotices={notices} />;
}
