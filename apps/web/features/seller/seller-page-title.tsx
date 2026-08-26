"use client";

import { usePathname } from "next/navigation";
import { sellerBasePath } from "./seller-context";

function resolveSellerTitle(pathname: string, merchantId: string) {
  const base = sellerBasePath(merchantId);
  const relative = pathname.slice(base.length);
  if (!relative || relative === "/") return { eyebrow: "TRUNG TÂM BÁN HÀNG", title: "Tổng quan" };
  if (relative.startsWith("/settings")) return { title: "Cài đặt shop" };
  if (relative.includes("/delivery")) return { title: "Phạm vi phục vụ" };
  if (relative.includes("/shipping")) return { title: "Vận chuyển" };
  if (relative.startsWith("/locations/") && relative.split("/").filter(Boolean).length >= 2) return { title: "Sửa điểm bán" };
  if (relative.startsWith("/locations")) return { title: "Điểm bán" };
  if (relative.startsWith("/catalog/new")) return { title: "Thêm sản phẩm / dịch vụ" };
  if (relative.startsWith("/catalog")) return { title: "Danh mục" };
  if (relative.startsWith("/inventory")) return { title: "Tồn kho" };
  if (relative.startsWith("/orders/") && relative.split("/").filter(Boolean).length >= 2) return { title: "Chi tiết đơn" };
  if (relative.startsWith("/orders")) return { title: "Đơn hàng" };
  if (relative.startsWith("/needs")) return { title: "Nhu cầu phù hợp" };
  if (relative.startsWith("/reputation")) return { title: "Uy tín Seller" };
  if (relative.startsWith("/notices")) return { title: "Thông báo kiểm duyệt" };
  return { title: "Seller Center" };
}

export function SellerPageTitle({ merchantId }: { merchantId: string }) {
  const pathname = usePathname();
  const copy = resolveSellerTitle(pathname, merchantId);
  return (
    <div className="seller-page-title">
      {copy.eyebrow ? <p>{copy.eyebrow}</p> : null}
      <h1>{copy.title}</h1>
    </div>
  );
}
