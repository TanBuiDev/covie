"use client";

import { usePathname } from "next/navigation";
import { sellerBasePath } from "./seller-context";

function resolveSellerTitle(pathname: string, merchantId: string) {
  const base = sellerBasePath(merchantId);
  const relative = pathname.slice(base.length);
  if (!relative || relative === "/") return { eyebrow: "TRUNG TÂM BÁN HÀNG", title: "Tổng quan" };
  if (relative.startsWith("/settings")) return { eyebrow: "TRUNG TÂM BÁN HÀNG", title: "Cài đặt shop" };
  if (relative.includes("/delivery")) return { eyebrow: "TRUNG TÂM BÁN HÀNG", title: "Phạm vi phục vụ" };
  if (relative.includes("/shipping")) return { eyebrow: "TRUNG TÂM BÁN HÀNG", title: "Phí giao hàng" };
  if (relative.includes("/minimum-order")) return { eyebrow: "TRUNG TÂM BÁN HÀNG", title: "Đơn tối thiểu" };
  if (relative.includes("/contacts")) return { eyebrow: "TRUNG TÂM BÁN HÀNG", title: "Liên hệ" };
  if (relative === "/locations/new") return { eyebrow: "TRUNG TÂM BÁN HÀNG", title: "Thêm điểm bán" };
  if (relative.startsWith("/locations/") && relative.split("/").filter(Boolean).length >= 2) return { eyebrow: "TRUNG TÂM BÁN HÀNG", title: "Sửa điểm bán" };
  if (relative.startsWith("/locations")) return { eyebrow: "TRUNG TÂM BÁN HÀNG", title: "Điểm bán" };
  if (relative.startsWith("/catalog/new")) return { eyebrow: "TRUNG TÂM BÁN HÀNG", title: "Thêm sản phẩm / dịch vụ" };
  if (relative.startsWith("/catalog")) return { eyebrow: "TRUNG TÂM BÁN HÀNG", title: "Danh mục" };
  if (relative.startsWith("/inventory")) return { eyebrow: "TRUNG TÂM BÁN HÀNG", title: "Tồn kho" };
  if (relative.startsWith("/orders/") && relative.split("/").filter(Boolean).length >= 2) return { eyebrow: "TRUNG TÂM BÁN HÀNG", title: "Chi tiết đơn" };
  if (relative.startsWith("/orders")) return { eyebrow: "TRUNG TÂM BÁN HÀNG", title: "Đơn hàng" };
  if (relative.startsWith("/needs")) return { eyebrow: "TRUNG TÂM BÁN HÀNG", title: "Nhu cầu phù hợp" };
  if (relative.startsWith("/reputation")) return { eyebrow: "TRUNG TÂM BÁN HÀNG", title: "Uy tín Seller" };
  if (relative.startsWith("/notices")) return { eyebrow: "TRUNG TÂM BÁN HÀNG", title: "Thông báo kiểm duyệt" };
  return { eyebrow: "TRUNG TÂM BÁN HÀNG", title: "Seller Center" };
}

export function SellerPageTitle({ merchantId }: { merchantId: string }) {
  const pathname = usePathname();
  const copy = resolveSellerTitle(pathname, merchantId);
  return (
    <div className="seller-page-title">
      <p>{copy.eyebrow}</p>
      <h1>{copy.title}</h1>
    </div>
  );
}
