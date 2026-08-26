import Image from "next/image";
import Link from "next/link";
import type { ReactNode } from "react";
import { SellerNavLinks } from "@/features/seller/seller-nav";
import { sellerBasePath } from "@/features/seller/seller-context";

export function SellerShell({
  merchantId,
  merchantName,
  switcher,
  children,
}: {
  merchantId: string;
  merchantName?: string;
  switcher?: ReactNode;
  children: ReactNode;
}) {
  const base = sellerBasePath(merchantId);

  return (
    <div className="seller-shell">
      <aside className="seller-shell__navigation">
        <div className="seller-shell__appbar">
          <Link className="seller-shell__brand" href={base} aria-label="CoVie Seller Center — Tổng quan">
            <Image src="/icon.svg" alt="" aria-hidden="true" width={36} height={36} priority />
            <span>
              <strong>CoVie Seller</strong>
              <small>{merchantName ?? "Cửa hàng"}</small>
            </span>
          </Link>
          {switcher ? <div className="seller-shell__switcher">{switcher}</div> : null}
        </div>
        <SellerNavLinks merchantId={merchantId} />
      </aside>
      <main className="seller-shell__main" id="seller-main-content">
        {children}
      </main>
    </div>
  );
}
