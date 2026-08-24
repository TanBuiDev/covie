"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { listMyNeeds, type NeedStatus, type NeedSummary } from "./need-api";

export function NeedList() {
  const [status, setStatus] = useState<NeedStatus | "">("");
  const [items, setItems] = useState<NeedSummary[]>([]);
  const [cursor, setCursor] = useState<string | undefined>();
  const [nextCursor, setNextCursor] = useState<string | null>();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    listMyNeeds(status || undefined, cursor)
      .then((page) => {
        if (!active) return;
        setItems((current) => cursor ? [...current, ...page.items] : page.items);
        setNextCursor(page.nextCursor);
      })
      .catch((cause) => active && setError(cause instanceof Error ? cause.message : "Không thể tải nhu cầu."));
    return () => { active = false; };
  }, [status, cursor]);

  function changeStatus(value: NeedStatus | "") {
    setStatus(value);
    setCursor(undefined);
    setItems([]);
  }

  return (
    <section>
      <div style={{ display: "flex", gap: 12, alignItems: "center", marginBottom: 16 }}>
        <select aria-label="Lọc trạng thái" value={status} onChange={(event) => changeStatus(event.target.value as NeedStatus | "")}>
          <option value="">Tất cả</option>
          <option value="ACTIVE">Đang tìm</option>
          <option value="FULFILLED">Đã đáp ứng</option>
          <option value="EXPIRED">Đã hết hạn</option>
          <option value="CANCELLED">Đã hủy</option>
        </select>
        <Link href="/needs/new">Tạo nhu cầu mới</Link>
      </div>
      {error && <p role="alert">{error}</p>}
      <div style={{ display: "grid", gap: 12 }}>
        {items.map((need) => (
          <article key={need.id} style={{ border: "1px solid #ddd", padding: 16, borderRadius: 8 }}>
            <Link href={`/needs/${need.id}`}><strong>{need.title}</strong></Link>
            <p>{need.type} · {need.status} · {need.responseCount} phản hồi</p>
            <small>Hết hạn: {new Date(need.expiresAt).toLocaleString("vi-VN")}</small>
          </article>
        ))}
        {!items.length && !error && <p>Chưa có nhu cầu.</p>}
      </div>
      {nextCursor && <button type="button" onClick={() => setCursor(nextCursor)} style={{ marginTop: 16 }}>Xem thêm</button>}
    </section>
  );
}
