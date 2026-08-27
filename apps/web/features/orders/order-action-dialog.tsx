"use client";

import { useState } from "react";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import type { OrderAction } from "./order-api";

export type OrderActorView = "BUYER" | "SELLER";

type ActionCopy = {
  title: string;
  confirm: string;
  description: string;
  dangerous?: boolean;
  reason?: "required" | "optional";
};

const actionCopy: Record<OrderAction, ActionCopy> = {
  CONFIRM: { title: "Xác nhận đơn", confirm: "Xác nhận", description: "Xác nhận rằng bạn có thể thực hiện đơn này." },
  REJECT: { title: "Từ chối đơn", confirm: "Từ chối", description: "Hãy nhập lý do trước khi từ chối đơn.", dangerous: true, reason: "required" },
  CANCEL: { title: "Hủy đơn", confirm: "Hủy đơn", description: "Yêu cầu hủy sẽ được xử lý theo trạng thái và quyền hiện tại của đơn.", dangerous: true, reason: "optional" },
  REQUEST_CANCELLATION: { title: "Yêu cầu hủy", confirm: "Gửi yêu cầu", description: "Gửi yêu cầu hủy cùng lý do để phía còn lại xử lý.", dangerous: true, reason: "required" },
  ACCEPT_CANCELLATION: { title: "Chấp nhận hủy", confirm: "Chấp nhận", description: "Xác nhận chấp nhận yêu cầu hủy đơn.", dangerous: true, reason: "optional" },
  REJECT_CANCELLATION: { title: "Từ chối hủy", confirm: "Từ chối", description: "Từ chối yêu cầu hủy nếu thao tác này vẫn còn khả dụng.", reason: "optional" },
  MARK_OUT_FOR_DELIVERY: { title: "Bắt đầu giao hàng", confirm: "Bắt đầu giao", description: "Cập nhật tiến độ giao hàng cho người mua." },
  MARK_DELIVERED: { title: "Đã giao hàng", confirm: "Đánh dấu đã giao", description: "Chỉ xác nhận khi hàng đã được giao theo quy trình của đơn." },
  MARK_READY_FOR_PICKUP: { title: "Sẵn sàng nhận hàng", confirm: "Đã sẵn sàng", description: "Thông báo đơn đã sẵn sàng để người mua đến nhận." },
  REPORT_BUYER_NO_SHOW: { title: "Báo người mua không đến", confirm: "Gửi báo cáo", description: "Hệ thống sẽ kiểm tra thời gian chờ và điều kiện áp dụng trước khi ghi nhận.", dangerous: true },
  CONTEST_NO_SHOW: { title: "Phản hồi báo cáo no-show", confirm: "Gửi phản hồi", description: "Gửi phản hồi để hệ thống xử lý theo trạng thái hiện tại.", reason: "optional" },
  MARK_SERVICE_PERFORMED: { title: "Đã thực hiện dịch vụ", confirm: "Xác nhận đã thực hiện", description: "Cập nhật rằng dịch vụ đã được thực hiện." },
  CONFIRM_RECEIVED: { title: "Xác nhận đã nhận hàng", confirm: "Hoàn tất đơn", description: "Xác nhận này hoàn tất đơn và không thể hoàn tác.", dangerous: true },
  CONFIRM_COMPLETED: { title: "Xác nhận dịch vụ hoàn thành", confirm: "Hoàn tất đơn", description: "Xác nhận này hoàn tất đơn và không thể hoàn tác.", dangerous: true },
  REPORT_DELIVERY_ISSUE: { title: "Báo sự cố giao hàng", confirm: "Gửi sự cố", description: "Mô tả ngắn vấn đề để hệ thống ghi nhận sự cố của đơn.", dangerous: true, reason: "required" },
  REPORT_SERVICE_ISSUE: { title: "Báo sự cố dịch vụ", confirm: "Gửi sự cố", description: "Mô tả ngắn vấn đề để hệ thống ghi nhận sự cố của đơn.", dangerous: true, reason: "required" },
};

function copyFor(action: OrderAction, actorView: OrderActorView): ActionCopy {
  const base = actionCopy[action];
  if (action === "CANCEL" && actorView === "SELLER") {
    return { ...base, reason: "required", description: "Hủy đơn từ Seller cần lý do. Thao tác chỉ được thực hiện nếu đơn vẫn cho phép hủy." };
  }
  return base;
}

export function OrderActionDialog({
  action,
  actorView = "BUYER",
  pending,
  onCancel,
  onSubmit,
}: {
  action?: OrderAction;
  actorView?: OrderActorView;
  pending?: boolean;
  onCancel(): void;
  onSubmit(input: { reasonCode?: string; note?: string }): void;
}) {
  if (!action) return null;
  return <OrderActionDialogForm key={`${actorView}-${action}`} action={action} actorView={actorView} pending={pending} onCancel={onCancel} onSubmit={onSubmit} />;
}

function OrderActionDialogForm({
  action,
  actorView,
  pending,
  onCancel,
  onSubmit,
}: {
  action: OrderAction;
  actorView: OrderActorView;
  pending?: boolean;
  onCancel(): void;
  onSubmit(input: { reasonCode?: string; note?: string }): void;
}) {
  const [reason, setReason] = useState("");
  const copy = copyFor(action, actorView);
  const trimmed = reason.trim();
  const reasonRequired = copy.reason === "required";

  return (
    <ConfirmDialog
      open
      title={copy.title}
      description={copy.description}
      confirmLabel={pending ? "Đang xử lý…" : copy.confirm}
      dangerous={copy.dangerous}
      confirmDisabled={Boolean(pending || (reasonRequired && !trimmed))}
      cancelDisabled={Boolean(pending)}
      onCancel={onCancel}
      onConfirm={() => {
        if (pending || (reasonRequired && !trimmed)) return;
        onSubmit({
          reasonCode: copy.reason && trimmed ? trimmed.slice(0, 60) : undefined,
          note: copy.reason && trimmed ? trimmed : undefined,
        });
      }}
    >
      {copy.reason ? (
        <label>
          Lý do {reasonRequired ? "(bắt buộc)" : "(không bắt buộc)"}
          <textarea
            value={reason}
            maxLength={1000}
            required={reasonRequired}
            disabled={pending}
            onChange={(event) => setReason(event.target.value)}
          />
        </label>
      ) : null}
      {reasonRequired && !trimmed ? <p className="field-hint">Nhập lý do để tiếp tục.</p> : null}
    </ConfirmDialog>
  );
}
