"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { LoadingState } from "@/components/ui/loading-state";
import { listMyNeeds, type NeedStatus, type NeedSummary } from "./need-api";
import styles from "./buyer-needs-mobile.module.css";

const statusLabel: Record<NeedStatus, string> = {
  ACTIVE: "Đang tìm",
  FULFILLED: "Đã đáp ứng",
  EXPIRED: "Đã hết hạn",
  CANCELLED: "Đã hủy",
};

const typeLabel = { PRODUCT: "Sản phẩm", SERVICE: "Dịch vụ" } as const;

const filterTabs: Array<{ status: NeedStatus; label: string }> = [
  { status: "ACTIVE", label: "Đang cần" },
  { status: "FULFILLED", label: "Đã tìm được" },
  { status: "EXPIRED", label: "Hết hạn" },
];

export function NeedList() {
  const [status, setStatus] = useState<NeedStatus | "">("ACTIVE");
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

  const isAuthError = Boolean(
    error && (
      error.toLowerCase().includes("token") ||
      error.toLowerCase().includes("unauthorized") ||
      error.toLowerCase().includes("401") ||
      error.toLowerCase().includes("đăng nhập")
    )
  );

  return (
    <section className={`need-list ${styles.list}`} aria-label="Danh sách nhu cầu">
      <div className={`need-list__toolbar ${styles.toolbar}`}>
        <div className={`need-tabs ${styles.segments}`} role="group" aria-label="Lọc nhu cầu theo trạng thái">
          {filterTabs.map((tab) => {
            const isSelected = status === tab.status;
            return (
              <button
                className={`need-chip ${styles.segment} ${isSelected ? `need-chip--active ${styles.segmentActive}` : ""}`}
                type="button"
                key={tab.status}
                aria-pressed={isSelected}
                onClick={() => changeStatus(tab.status)}
              >
                {isSelected ? (
                  <svg className={styles.chipCheck} width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
                    <path d="M3.52 8.32L6.88 11.52L12.64 4.8" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                ) : null}
                <span>{tab.label}</span>
              </button>
            );
          })}
        </div>

        <Link className={`button-primary need-list__create ${styles.createButton}`} href="/needs/new">
          <svg width="16" height="16" viewBox="0 0 20 20" fill="none" aria-hidden="true">
            <path d="M10 4V16M4 10H16" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          <span>Tạo nhu cầu mới</span>
        </Link>
      </div>

      {error && !isAuthError ? <p className="form-alert" role="alert">{error}</p> : null}

      {loading && !items.length ? (
        <div className={`need-loading ${styles.state}`}><LoadingState label="Đang tải nhu cầu…" /></div>
      ) : null}

      {isAuthError ? (
        <div className={`need-empty ${styles.state}`}>
          <h2>Đăng nhập để xem nhu cầu</h2>
          <p>Vui lòng đăng nhập tài khoản CoVie để quản lý các nhu cầu cần mua và theo dõi phản hồi từ người bán.</p>
          <Link className="button-primary" href="/login?redirect=/needs">Đăng nhập ngay</Link>
        </div>
      ) : null}

      {!loading && !items.length && !error ? (
        <div className={`need-empty ${styles.state}`}>
          <h2>Chưa có nhu cầu phù hợp</h2>
          <p>Đăng điều bạn đang cần để nhận phản hồi từ người bán gần bạn.</p>
          <Link className="button-primary" href="/needs/new">Tạo nhu cầu mới</Link>
        </div>
      ) : null}

      <div className={`need-cards ${styles.cards}`}>
        {items.map((need) => (
          <article className={`need-card ${styles.card}`} key={need.id}>
            <div className={`need-card__header ${styles.cardHeader}`}>
              <h2><Link href={`/needs/${need.id}`}>{need.title}</Link></h2>
              <span className="sr-only">{need.status} · {need.responseCount} phản hồi</span>
            </div>

            <p className={`need-card__type ${styles.type}`}>
              {typeLabel[need.type]}
            </p>

            <p className={`need-card__radius ${styles.radius}`}>
              Bán kính {(need.radiusMeters / 1000).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} km
            </p>

            <div className={`need-card__pills ${styles.pills}`}>
              <span className={`need-pill need-pill--muted ${styles.pillMuted}`}>
                Hết hạn {new Date(need.expiresAt).toLocaleString("vi-VN")}
              </span>
              <span className={`need-pill need-pill--muted ${styles.pillMuted}`}>
                {need.responseCount.toLocaleString("vi-VN")} phản hồi
              </span>
              <span className={`need-pill need-pill--${need.status.toLowerCase()} ${styles.pillStatus}`}>
                {statusLabel[need.status]}
              </span>
            </div>

            <div className={`need-card__actions ${styles.cardActions}`}>
              <Link className={`button-secondary need-card__detail ${styles.detail}`} href={`/needs/${need.id}`}>
                Xem chi tiết
              </Link>
            </div>
          </article>
        ))}
      </div>

      {nextCursor ? (
        <button className={`button-secondary need-list__more ${styles.more}`} disabled={loading} type="button" onClick={() => setCursor(nextCursor)}>
          {loading ? "Đang tải…" : "Xem thêm"}
        </button>
      ) : null}
    </section>
  );
}
