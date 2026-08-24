const STATUS_COPY: Record<string, string> = {
  DRAFT: "Bản nháp",
  ACTIVE: "Đang hoạt động",
  PAUSED: "Tạm dừng",
  PENDING: "Đang chờ xử lý",
  CONFIRMED: "Đã xác nhận",
  PREPARING: "Đang chuẩn bị",
  READY_FOR_PICKUP: "Sẵn sàng nhận",
  OUT_FOR_DELIVERY: "Đang giao",
  DELIVERED: "Đã giao",
  COMPLETED: "Đã hoàn thành",
  CANCELLED: "Đã hủy",
  EXPIRED: "Đã hết hạn",
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
