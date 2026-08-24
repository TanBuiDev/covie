import type { CovieApiError } from "./api-error";

const ERROR_COPY: Record<string, string> = {
  AUTHENTICATION_REQUIRED: "Vui lòng đăng nhập để tiếp tục.",
  ACCESS_DENIED: "Bạn không có quyền thực hiện thao tác này.",
  CSRF_INVALID: "Phiên bảo mật đã thay đổi. Vui lòng thử lại thao tác.",
  RESOURCE_NOT_FOUND: "Nội dung không còn khả dụng.",
  RESOURCE_VERSION_CONFLICT: "Dữ liệu đã thay đổi. Hãy tải lại và thử lại.",
  ORDER_STATE_CONFLICT: "Trạng thái đơn đã thay đổi. Hãy tải lại để xem thao tác hiện có.",
  CHECKOUT_CHANGED: "Thông tin đơn đã thay đổi. Hãy kiểm tra lại trước khi đặt.",
  INVENTORY_BELOW_RESERVED_QUANTITY: "Số lượng tồn kho mới thấp hơn số lượng đang được giữ chỗ.",
  RATE_LIMIT_EXCEEDED: "Bạn thao tác quá nhanh. Vui lòng thử lại sau.",
};

export function errorCopy(code: string, requestId?: string): string {
  const known = ERROR_COPY[code];
  if (known) return known;
  return requestId
    ? `Không thể hoàn tất thao tác. Mã yêu cầu: ${requestId}`
    : "Không thể hoàn tất thao tác. Vui lòng thử lại.";
}

export function apiErrorCopy(error: Pick<CovieApiError, "code" | "requestId">): string {
  return errorCopy(error.code, error.requestId);
}
