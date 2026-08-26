import type { ReactNode } from "react";
import Image from "next/image";
import Link from "next/link";
import { SellerNavLinks } from "@/features/seller/seller-nav";
import { SellerPageTitle } from "@/features/seller/seller-page-title";
import { sellerBasePath } from "@/features/seller/seller-context";

function statusLabel(status?: string) {
  switch (status) {
    case "ACTIVE": return "đang hoạt động";
    case "PAUSED": return "tạm dừng";
    case "SUSPENDED": return "bị hạn chế";
    case "DELETED": return "đã đóng";
    default: return "đã được cập nhật";
  }
}

export function SellerShell({
  merchantId,
  merchantName,
  merchantStatus,
  switcher,
  children,
}: {
  merchantId: string;
  merchantName?: string;
  merchantStatus?: "ACTIVE" | "PAUSED" | "SUSPENDED" | "DELETED";
  switcher?: ReactNode;
  children: ReactNode;
}) {
  const base = sellerBasePath(merchantId);
  const restricted = merchantStatus && merchantStatus !== "ACTIVE";

  return (
    <div className="seller-shell">
      <header className="seller-mobile-header">
        <SellerPageTitle merchantId={merchantId} />
        {switcher ? <div className="seller-mobile-switcher">{switcher}</div> : null}
      </header>

      <div className="seller-mobile-tabs">
        <SellerNavLinks merchantId={merchantId} scrollActiveIntoView />
      </div>

      <aside className="seller-desktop-navigation">
        <Link className="seller-desktop-brand" href={base} aria-label="CoVie Seller Center — Tổng quan">
          <Image src="/icon.svg" alt="" aria-hidden="true" width={36} height={36} priority />
          <span>
            <strong>CoVie Seller</strong>
            <small>{merchantName ?? "Cửa hàng"}</small>
          </span>
        </Link>
        {switcher ? <div className="seller-desktop-switcher">{switcher}</div> : null}
        <SellerNavLinks merchantId={merchantId} />
      </aside>

      <main className="seller-shell__main" id="seller-main-content">
        {restricted ? (
          <section className="warning-banner seller-restriction-banner" role="status" aria-label="Trạng thái cửa hàng">
            <strong>Cửa hàng hiện {statusLabel(merchantStatus)}.</strong>
            <p>Một số dữ liệu hoặc thao tác có thể bị giới hạn. CoVie chỉ hiển thị những thao tác hiện còn khả dụng.</p>
          </section>
        ) : null}
        {children}
      </main>
    </div>
  );
}
