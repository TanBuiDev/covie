const labels: Record<string, string> = {
  PENDING_CONFIRMATION: "Chờ xác nhận", CONFIRMED: "Đã xác nhận", OUT_FOR_DELIVERY: "Đang giao",
  READY_FOR_PICKUP: "Sẵn sàng nhận", AWAITING_BUYER_CONFIRMATION: "Chờ bạn xác nhận",
  CANCELLATION_REQUESTED: "Đang yêu cầu hủy", COMPLETED: "Hoàn thành", EXPIRED: "Hết hạn",
  REJECTED_BY_SELLER: "Người bán từ chối", CANCELLED_BEFORE_CONFIRMATION: "Đã hủy",
  CANCELLED_BY_BUYER_AFTER_CONFIRMATION: "Đã hủy", CANCELLED_BY_SELLER_AFTER_CONFIRMATION: "Người bán đã hủy",
  DELIVERY_ISSUE_REPORTED: "Đang có vấn đề giao hàng", SERVICE_ISSUE_REPORTED: "Đang có vấn đề dịch vụ",
  BUYER_NO_SHOW: "Không đến nhận hàng", NO_SHOW_CONTESTED: "Đang phản hồi no-show", CLOSED_WITH_ISSUE: "Đóng với vấn đề",
};

export function OrderStatusLabel({ status }: { status: string }) {
  return <span className={`order-status order-status--${status.toLowerCase()}`}>{labels[status] ?? "Trạng thái mới"}</span>;
}
