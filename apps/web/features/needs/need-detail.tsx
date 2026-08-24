"use client";

import { useEffect, useState } from "react";
import { cancelNeed, getMyNeed, type NeedDetail as NeedDetailModel } from "./need-api";
import { NeedResponseList } from "./need-response-list";

export function NeedDetail({ needId }: { needId: string }) {
  const [need, setNeed] = useState<NeedDetailModel | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getMyNeed(needId).then(setNeed).catch((cause) => setError(cause instanceof Error ? cause.message : "Không thể tải nhu cầu."));
  }, [needId]);

  async function cancel() {
    try {
      setNeed(await cancelNeed(needId));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể hủy nhu cầu.");
    }
  }

  if (error && !need) return <p role="alert">{error}</p>;
  if (!need) return <p>Đang tải…</p>;

  return (
    <div style={{ display: "grid", gap: 24 }}>
      <section>
        <h1>{need.title}</h1>
        <p>{need.type} · {need.status}</p>
        <p>{need.description || need.queryText}</p>
        <p>Bán kính: {need.radiusMeters.toLocaleString("vi-VN")} m</p>
        <p>Hết hạn: {new Date(need.expiresAt).toLocaleString("vi-VN")}</p>
        {need.fulfilledOrderId && <p>Đơn đáp ứng: {need.fulfilledOrderId}</p>}
        {need.status === "ACTIVE" && <button type="button" onClick={cancel}>Hủy nhu cầu</button>}
        {error && <p role="alert">{error}</p>}
      </section>
      <NeedResponseList needId={needId} />
    </div>
  );
}
