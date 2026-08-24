"use client";

import { useMemo, useState } from "react";
import { CovieApiError } from "@/lib/api/api-error";
import { errorCopy } from "@/lib/api/error-copy";
import { OrderActionDialog } from "./order-action-dialog";
import { orderCommand, type OrderAction, type OrderDetail } from "./order-api";

const actionUi: Record<OrderAction, { label: string; priority?: "primary" | "danger" }> = {
  CONFIRM: { label: "Xác nhận đơn", priority: "primary" },
  REJECT: { label: "Từ chối", priority: "danger" },
  CANCEL: { label: "Hủy đơn", priority: "danger" },
  REQUEST_CANCELLATION: { label: "Yêu cầu hủy", priority: "danger" },
  ACCEPT_CANCELLATION: { label: "Chấp nhận hủy", priority: "danger" },
  REJECT_CANCELLATION: { label: "Từ chối hủy" },
  MARK_OUT_FOR_DELIVERY: { label: "Bắt đầu giao", priority: "primary" },
  MARK_DELIVERED: { label: "Đã giao", priority: "primary" },
  MARK_READY_FOR_PICKUP: { label: "Sẵn sàng nhận", priority: "primary" },
  REPORT_BUYER_NO_SHOW: { label: "Báo không đến", priority: "danger" },
  CONTEST_NO_SHOW: { label: "Phản hồi báo no-show" },
  MARK_SERVICE_PERFORMED: { label: "Đã thực hiện dịch vụ", priority: "primary" },
  CONFIRM_RECEIVED: { label: "Đã nhận hàng", priority: "primary" },
  CONFIRM_COMPLETED: { label: "Dịch vụ đã hoàn thành", priority: "primary" },
  REPORT_DELIVERY_ISSUE: { label: "Chưa nhận được hàng", priority: "danger" },
  REPORT_SERVICE_ISSUE: { label: "Có vấn đề với dịch vụ", priority: "danger" },
};

export function OrderActions({ order, onChanged }: { order: OrderDetail; onChanged: () => Promise<void> }) {
  const [selected, setSelected] = useState<OrderAction>();
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState<string>();
  const [intentKey, setIntentKey] = useState<string>();

  const actions = useMemo(
    () => (order.allowedActions ?? []).filter((action): action is OrderAction => action in actionUi),
    [order.allowedActions],
  );

  function open(action: OrderAction) {
    setMessage(undefined);
    setSelected(action);
    setIntentKey(crypto.randomUUID());
  }

  async function submit(input: { reasonCode?: string; note?: string }) {
    if (!order.id || !selected || !intentKey || pending) return;
    setPending(true);
    setMessage(undefined);

    try {
      await orderCommand(order.id, selected, intentKey, input);
      setSelected(undefined);
      setIntentKey(undefined);
      await onChanged();
    } catch (error) {
      await onChanged().catch(() => undefined);
      if (error instanceof CovieApiError) {
        if (error.code === "ORDER_STATE_CONFLICT" || error.code === "RESOURCE_VERSION_CONFLICT") {
          setMessage("Đơn hàng đã thay đổi. CoVie đã tải lại trạng thái mới nhất; hãy kiểm tra các thao tác còn khả dụng.");
          setSelected(undefined);
          setIntentKey(undefined);
        } else if (error.status === 403) {
          setMessage("Thao tác này hiện không còn được phép với đơn hàng.");
        } else {
          setMessage(errorCopy(error.code, error.requestId));
        }
      } else {
        setMessage("Không thể cập nhật đơn hàng. Vui lòng kiểm tra trạng thái mới nhất rồi thử lại.");
      }
    } finally {
      setPending(false);
    }
  }

  if (!actions.length && !message) return null;

  return (
    <section className="order-actions" aria-label="Thao tác đơn hàng">
      {message ? <p role="status">{message}</p> : null}
      <div className="action-row">
        {actions.map((action) => {
          const ui = actionUi[action];
          return (
            <button
              key={action}
              type="button"
              className={ui.priority === "danger" ? "button-danger" : ui.priority === "primary" ? "button-primary" : undefined}
              disabled={pending}
              onClick={() => open(action)}
            >
              {ui.label}
            </button>
          );
        })}
      </div>
      <OrderActionDialog
        action={selected}
        pending={pending}
        onCancel={() => {
          if (pending) return;
          setSelected(undefined);
          setIntentKey(undefined);
        }}
        onSubmit={submit}
      />
    </section>
  );
}
