"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { LoadingState } from "@/components/ui/loading-state";
import { listMyNeeds, type NeedStatus, type NeedSummary } from "./need-api";

const statusLabel: Record<NeedStatus, string> = {
  ACTIVE: "Đang tìm",
  FULFILLED: "Đã đáp ứng",
  EXPIRED: "Đã hết hạn",
  CANCELLED: "Đã hủy",
};

const typeLabel = { PRODUCT: "Sản phẩm", SERVICE: "Dịch vụ" } as const;

export function NeedList() {
  const [status, setStatus] = useState<NeedStatus | "">("");
  const [items, setItems] = useState<NeedSummary[]>([]);
  const [cursor, setCursor] = useState<string | undefined>();
  const [nextCursor, setNextCursor] = useState<string | null>();
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    listMyNeeds(status || undefined, cursor)
      .then((page) => {
        if (!active) return;
        setItems((current) => cursor ? [...current, ...page.items] : page.items);
        setNextCursor(page.nextCursor ?? null);
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : "Không thể tải nhu cầu.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [status, cursor]);

  function changeStatus(value: NeedStatus | "") {
    setStatus(value);
    setCursor(undefined);
    setItems([]);
  }

  return (
    <section className="need-list" aria-label="Danh sách nhu cầu">
      <div className="need-list__toolbar">
        <label className="need-list__filter">
          <span>Lọc trạng thái</span>
          <select value={status} onChange={(event) => changeStatus(event.target.value as NeedStatus | "")}>
            <option value="">Tất cả</option>
            <option value="ACTIVE">Đang tìm</option>
            <option value="FULFILLED">Đã đáp ứng</option>
            <option value="EXPIRED">Đã hết hạn</option>
            <option value="CANCELLED">Đã hủy</option>
          </select>
        </label>
        <Link className="button-primary need-list__create" href="/needs/new">Tạo nhu cầu mới</Link>
      </div>

      {error ? <p className="form-alert" role="alert">{error}</p> : null}
      {loading && !items.length ? <LoadingState label="Đang tải nhu cầu…" /> : null}

      {!loading && !items.length && !error ? (
        <div className="need-empty">
          <h2>Chưa có nhu cầu phù hợp</h2>
          <p>Đăng điều bạn đang cần để nhận phản hồi từ người bán gần bạn.</p>
          <Link className="button-primary" href="/needs/new">Đăng nhu cầu</Link>
        </div>
      ) : null}

      <div className="need-cards">
        {items.map((need) => (
          <article className="need-card" key={need.id}>
            <div className="need-card__topline">
              <span className="need-card__type">{typeLabel[need.type]}</span>
              <span className={`need-status need-status--${need.status.toLowerCase()}`}>{statusLabel[need.status]}</span>
            </div>
            <h2><Link href={`/needs/${need.id}`}>{need.title}</Link></h2>
            <div className="need-card__meta">
              <span>{need.responseCount.toLocaleString("vi-VN")} phản hồi</span>
              <span>Bán kính {(need.radiusMeters / 1000).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} km</span>
            </div>
            <small>Hết hạn {new Date(need.expiresAt).toLocaleString("vi-VN")}</small>
            <Link className="button-secondary need-card__detail" href={`/needs/${need.id}`}>Xem chi tiết</Link>
          </article>
        ))}
      </div>

      {nextCursor ? (
        <button className="button-secondary need-list__more" disabled={loading} type="button" onClick={() => setCursor(nextCursor)}>
          {loading ? "Đang tải…" : "Xem thêm"}
        </button>
      ) : null}
    </section>
  );
}
