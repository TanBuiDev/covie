"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { LoadingState } from "@/components/ui/loading-state";
import { CovieApiError } from "@/lib/api/api-error";
import { ChatText, Package, Tag, Wrench } from "@phosphor-icons/react";
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
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 60_000);
    return () => window.clearInterval(timer);
  }, []);
  const [status, setStatus] = useState<NeedStatus | "">("ACTIVE");
  const [items, setItems] = useState<NeedSummary[]>([]);
  const [cursor, setCursor] = useState<string | undefined>();
  const [nextCursor, setNextCursor] = useState<string | null>();
  const [error, setError] = useState<string | null>(null);
  const [isAuthError, setIsAuthError] = useState(false);
  const [loading, setLoading] = useState(true);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => {
      if (!active) return undefined;
      setLoading(true);
      setError(null);
      setIsAuthError(false);
      return listMyNeeds(status || undefined, cursor);
    })
      .then((page) => {
        if (!active || !page) return;
        setItems((current) => cursor ? [...current, ...page.items] : page.items);
        setNextCursor(page.nextCursor ?? null);
      })
      .catch((cause) => {
        if (active) {
          setIsAuthError(cause instanceof CovieApiError && cause.status === 401);
          setError(cause instanceof Error ? cause.message : "Không thể tải nhu cầu.");
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [status, cursor, reloadKey]);

  function changeStatus(value: NeedStatus | "") {
    setStatus(value);
    setCursor(undefined);
    setItems([]);
  }


  function getExpiryText(expiresAtStr: string) {
    const diffMs = new Date(expiresAtStr).getTime() - now;
    if (diffMs <= 0) return "Đã hết hạn";
    const diffHours = Math.round(diffMs / (1000 * 60 * 60));
    if (diffHours < 1) {
      const diffMins = Math.max(1, Math.round(diffMs / (1000 * 60)));
      return `Còn ${diffMins} phút`;
    }
    if (diffHours < 24) return `Còn ${diffHours} giờ`;
    const diffDays = Math.round(diffHours / 24);
    return `Còn ${diffDays} ngày`;
  }

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
                aria-label={tab.label}
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

      {error && !isAuthError ? <div className="need-empty"><p className="form-alert" role="alert">{error}</p><button className="button-secondary" type="button" onClick={() => setReloadKey(value => value + 1)}>Thử lại</button></div> : null}

      {loading && !items.length ? (
        <div className={`need-loading ${styles.state}`}><LoadingState label="Đang tải nhu cầu…" /></div>
      ) : null}

      {isAuthError ? (
        <div className={`need-empty ${styles.state}`}>
          <h2>Đăng nhập để xem nhu cầu</h2>
          <p>Vui lòng đăng nhập tài khoản CoVie để quản lý các nhu cầu cần mua và theo dõi phản hồi từ người bán.</p>
          <Link className="button-primary" href="/login?returnTo=%2Fneeds">Đăng nhập ngay</Link>
        </div>
      ) : null}

      {!loading && !items.length && !error ? (
        <div className={`need-empty ${styles.state}`}>
          <div className="need-empty__icon-wrap" aria-hidden="true">
            <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" />
              <polyline points="3.27 6.96 12 12.01 20.73 6.96" />
              <line x1="12" y1="22.08" x2="12" y2="12" />
            </svg>
          </div>
          <h2>Chưa có nhu cầu phù hợp</h2>
          <p>Đăng điều bạn đang cần để nhận phản hồi từ người bán gần bạn.</p>
          <Link className="button-primary" href="/needs/new">Tạo nhu cầu mới</Link>

          <div className="need-empty__community-suggestions">
            <span className="need-empty__suggestions-title">Gợi ý nhu cầu phổ biến trong khu dân cư:</span>
            <div className="need-empty__chips">
              <Link href="/needs/new?q=Rau%20c%E1%BB%A7%20h%E1%BB%AFu%20c%C6%A1" className="need-empty__chip">
                <Tag size={12} weight="bold" />
                <span>Rau củ sạch giao ngay</span>
              </Link>
              <Link href="/needs/new?q=M%C6%B0%E1%BB%A3n%20thang%20nh%C3%B4m" className="need-empty__chip">
                <Tag size={12} weight="bold" />
                <span>Mượn thang nhôm</span>
              </Link>
              <Link href="/needs/new?q=S%E1%BB%ADa%20%C4%91i%E1%BB%87n%20n%C6%B0%E1%BB%9Bc" className="need-empty__chip">
                <Tag size={12} weight="bold" />
                <span>Sửa điện nước tại nhà</span>
              </Link>
            </div>
          </div>
        </div>
      ) : null}

      <div className={`need-cards ${styles.cards}`}>
        {items.map((need) => {
          const isService = need.type === "SERVICE";
          const expiryNotice = getExpiryText(need.expiresAt);
          const hasResponses = need.responseCount > 0;

          return (
            <article className={`need-card ${styles.card}`} key={need.id}>
              <div className="need-card__top">
                <div className="need-card__badge-type" aria-hidden="true">
                  <span className="need-card__type-icon">
                    {isService ? <Wrench size={13} weight="bold" /> : <Package size={13} weight="bold" />}
                  </span>
                  <span className="need-card__type-tag">{typeLabel[need.type]}</span>
                </div>
                <div className={`need-pill need-pill--${need.status.toLowerCase()} ${styles.pillStatus}`}>
                  {statusLabel[need.status]}
                </div>
              </div>

              <div className={`need-card__header ${styles.cardHeader}`}>
                <h2><Link href={`/needs/${need.id}`}>{need.title}</Link></h2>
                <span className="sr-only">{need.status} · {need.responseCount} phản hồi</span>
              </div>

              <div className="need-card__meta-row">
                <p className={`need-card__radius ${styles.radius}`}>
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
                    <circle cx="12" cy="10" r="3" />
                  </svg>
                  <span>Bán kính {(need.radiusMeters / 1000).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} km</span>
                </p>
                <span className="need-card__meta-separator" aria-hidden="true">·</span>
                <p className="need-card__expiry-text">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <circle cx="12" cy="12" r="10" />
                    <polyline points="12 6 12 12 16 14" />
                  </svg>
                  <span>{expiryNotice}</span>
                </p>
              </div>

              {hasResponses ? (
                <Link href={`/needs/${need.id}`} className="need-card__response-spotlight">
                  <div className="need-card__response-spotlight-left">
                    <ChatText size={18} weight="duotone" className="need-card__response-spotlight-icon" />
                    <div>
                      <strong>Đã có {need.responseCount.toLocaleString("vi-VN")} người bán báo giá</strong>
                      <small>Bấm để so sánh giá và đặt hàng ngay</small>
                    </div>
                  </div>
                  <span className="need-card__response-spotlight-cta" aria-hidden="true">Xem ngay →</span>
                </Link>
              ) : need.status === "ACTIVE" ? (
                <div className="need-card__waiting-spotlight">
                  <span className="need-card__radar-dot" aria-hidden="true"></span>
                  <span>Đang tìm kiếm người bán phù hợp trong khu vực...</span>
                </div>
              ) : null}

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
          );
        })}
      </div>

      {nextCursor ? (
        <button className={`button-secondary need-list__more ${styles.more}`} disabled={loading} type="button" onClick={() => setCursor(nextCursor)}>
          {loading ? "Đang tải…" : "Xem thêm"}
        </button>
      ) : null}
    </section>
  );
}
