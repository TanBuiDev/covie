import type { components } from "@covie/api-client";
import { listCatalogItemsForServer } from "@/features/catalog/catalog-server-api";
import { SellerModerationNotices, type SellerModerationNotice } from "@/features/seller/seller-moderation-notices";
import { readApiError } from "@/lib/api/api-error";
import { createServerReadClient } from "@/lib/api/server-api-client";
import { ShieldCheck } from "@phosphor-icons/react/dist/ssr";

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

    return (
    <section className="seller-stack">
      <header className="surface-header seller-desktop-heading">
        <p className="eyebrow">Seller Center / Kiểm duyệt</p>
        <div style={{ display: "flex", alignItems: "center", gap: "12px", marginTop: "4px" }}>
          <div
            style={{
              width: "38px",
              height: "38px",
              borderRadius: "10px",
              background: "rgba(4, 120, 87, 0.08)",
              color: "#047857",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              flexShrink: 0,
            }}
          >
            <ShieldCheck size={22} weight="duotone" />
          </div>
          <h1 style={{ margin: 0 }}>Thông báo vi phạm & kiểm duyệt</h1>
        </div>
        <p>Theo dõi các thông báo cập nhật trạng thái kiểm duyệt nội dung và hồ sơ gian hàng từ quản trị viên.</p>
      </header>
      <SellerModerationNotices merchantId={merchantId} initialNotices={notices} />
    </section>
  );
}
