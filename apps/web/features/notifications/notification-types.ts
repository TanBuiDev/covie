const labels: Record<string,string>={ORDER_NEW:"Có đơn hàng mới",ORDER_CONFIRMED:"Đơn hàng đã được xác nhận",ORDER_REJECTED:"Đơn hàng bị từ chối",ORDER_EXPIRED:"Đơn hàng đã hết hạn",ORDER_CANCELLED:"Đơn hàng đã được hủy",ORDER_CANCELLATION_REQUESTED:"Có yêu cầu hủy đơn",ORDER_CANCELLATION_RESPONDED:"Yêu cầu hủy đơn đã được phản hồi",ORDER_FULFILLMENT_UPDATED:"Đơn hàng được cập nhật",ORDER_ISSUE_REPORTED:"Có vấn đề cần xử lý",ORDER_COMPLETED:"Đơn hàng hoàn tất",ORDER_CLOSED_WITH_ISSUE:"Đơn hàng đã đóng",NEED_MATCH_AVAILABLE:"Có nhu cầu phù hợp",NEED_RESPONSE_RECEIVED:"Có phản hồi mới",NEED_RESPONSE_UPDATED:"Phản hồi được cập nhật",NEED_RESPONSE_WITHDRAWN:"Phản hồi đã rút",NEED_FULFILLED:"Nhu cầu đã hoàn thành",NEED_EXPIRED:"Nhu cầu đã hết hạn",REVIEW_RECEIVED:"Bạn nhận được đánh giá mới"};
export function notificationLabel(type: string, data?: Record<string, unknown>) {
  return type === "BILLING_RENEWAL_REMINDER" && data?.noticeKind === "PRICE_CHANGED"
    ? "Bảng giá thuê bao được cập nhật" : labels[type] ?? "Có cập nhật mới";
}

export function notificationBillingSummary(type: string | undefined, data: Record<string, unknown> | undefined): string | null {
  if (type !== "BILLING_RENEWAL_REMINDER" || data?.noticeKind !== "PRICE_CHANGED") return null;
  const { planCode, revision, priceVnd, cycleMonths, effectiveAt } = data;
  if (typeof planCode !== "string" || !/^[A-Z0-9_]{1,60}$/.test(planCode)
    || typeof revision !== "number" || !Number.isSafeInteger(revision) || revision < 1
    || typeof priceVnd !== "number" || !Number.isSafeInteger(priceVnd) || priceVnd < 0 || priceVnd > 1_000_000_000
    || typeof cycleMonths !== "number" || !Number.isInteger(cycleMonths) || cycleMonths < 1 || cycleMonths > 120
    || typeof effectiveAt !== "string" || !Number.isFinite(Date.parse(effectiveAt))) return null;
  const amount = new Intl.NumberFormat("vi-VN").format(priceVnd);
  const effective = new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short",
    timeZone: "Asia/Ho_Chi_Minh" }).format(new Date(effectiveAt));
  return `Gói ${planCode}, phiên bản ${revision}: ${amount}đ / ${cycleMonths} tháng, áp dụng từ ${effective} (giờ Việt Nam). Kỳ đã thanh toán không thay đổi; không tự động thu phí.`;
}
