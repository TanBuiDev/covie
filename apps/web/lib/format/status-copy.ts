const STATUS_COPY: Record<string, string> = {
  DRAFT: "Bản nháp",
  ACTIVE: "Đang hoạt động",
  PAUSED: "Tạm dừng",
  PENDING: "Đang chờ xử lý",
  PENDING_CONFIRMATION: "Chờ xác nhận",
  CONFIRMED: "Đã xác nhận",
  PREPARING: "Đang chuẩn bị",
  READY_FOR_PICKUP: "Sẵn sàng nhận",
  OUT_FOR_DELIVERY: "Đang giao",
  AWAITING_BUYER_CONFIRMATION: "Chờ người mua xác nhận",
  CANCELLATION_REQUESTED: "Đang yêu cầu hủy",
  DELIVERY_ISSUE_REPORTED: "Đã báo vấn đề giao hàng",
  SERVICE_ISSUE_REPORTED: "Đã báo vấn đề dịch vụ",
  BUYER_NO_SHOW: "Người mua không đến",
  NO_SHOW_CONTESTED: "Đang phản hồi no-show",
  DELIVERED: "Đã giao",
  COMPLETED: "Đã hoàn thành",
  CANCELLED: "Đã hủy",
  CANCELLED_BEFORE_CONFIRMATION: "Đã hủy trước xác nhận",
  CANCELLED_BY_BUYER_AFTER_CONFIRMATION: "Đã hủy theo yêu cầu người mua",
  CANCELLED_BY_SELLER_AFTER_CONFIRMATION: "Người bán đã hủy",
  EXPIRED: "Đã hết hạn",
  REJECTED_BY_SELLER: "Người bán từ chối",
  CLOSED_WITH_ISSUE: "Đã đóng với vấn đề",
  FULFILLED: "Đã đáp ứng",
  PENDING_MODERATION: "Đang chờ duyệt",
  APPROVED: "Đã duyệt",
  REJECTED: "Không được duyệt",
  HIDDEN: "Đã ẩn",
  SUSPENDED: "Tạm đình chỉ",
};

export function statusCopy(status: string | null | undefined): string {
  if (!status) return "Trạng thái đang được cập nhật";
  return STATUS_COPY[status] ?? "Trạng thái đã được cập nhật";
}

export function contentTypeCopy(value: string | null | undefined): string {
  if (value === "PRODUCT") return "Sản phẩm";
  if (value === "SERVICE") return "Dịch vụ";
  if (value === "RESALE") return "Đồ bán lại";
  return "Nội dung";
}
