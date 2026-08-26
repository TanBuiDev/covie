"use client";

import { usePathname } from "next/navigation";
import { sellerBasePath } from "./seller-context";

function resolveSellerTitle(pathname: string, merchantId: string) {
  const base = sellerBasePath(merchantId);
  const relative = pathname.slice(base.length);
  if (!relative || relative === "/") return "Tổng quan";
  if (relative.startsWith("/settings")) return "Cài đặt shop";
  if (relative.includes("/contacts")) return "Liên hệ";
  if (relative.includes("/delivery")) return "Phạm vi phục vụ";
  if (relative.includes("/shipping")) return "Phí giao hàng";
  if (relative.startsWith("/locations/") && relative.split("/").filter(Boolean).length >= 2) return "Sửa điểm bán";
  if (relative.startsWith("/locations")) return "Điểm bán";
  if (relative.startsWith("/catalog/new")) return "Thêm sản phẩm / dịch vụ";
  if (relative.startsWith("/catalog")) return "Danh mục";
  if (relative.startsWith("/inventory")) return "Tồn kho";
  if (relative.startsWith("/orders/") && relative.split("/").filter(Boolean).length >= 2) return "Chi tiết đơn";
  if (relative.startsWith("/orders")) return "Đơn hàng";
  if (relative.startsWith("/needs")) return "Cần mua phù hợp";
  if (relative.startsWith("/reputation")) return "Uy tín shop";
  if (relative.startsWith("/notices")) return "Thông báo nội dung";
  return "Seller Center";
}

export function SellerPageTitle({ merchantId }: { merchantId: string }) {
  const pathname = usePathname();
  return (
    <div className="seller-page-title">
      <p>TRUNG TÂM BÁN HÀNG</p>
      <h1>{resolveSellerTitle(pathname, merchantId)}</h1>
    </div>
  );
}
