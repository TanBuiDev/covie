"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  ChartLineUp,
  Eye,
  CursorClick,
  TrendUp,
  ArrowsClockwise,
  X,
  CalendarBlank,
} from "@phosphor-icons/react";
import { getAdCampaignStats, type AdCampaign, type AdScope, type AdStats } from "../ad-api";
import { CovieApiError } from "@/lib/api/api-error";
import styles from "./management.module.css";

export function AdAnalytics({
  scope,
  campaign,
  onClose,
  onAuth,
}: {
  scope: AdScope;
  campaign: AdCampaign;
  onClose: () => void;
  onAuth: () => void;
}) {
  const [from, setFrom] = useState(() => new Date(Date.now() - 29 * 86400000).toISOString().slice(0, 10));
  const [to, setTo] = useState(() => new Date().toISOString().slice(0, 10));
  const [stats, setStats] = useState<AdStats | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const requests = useRef(new Set<string>());
  const generation = useRef(0);
  const kind = scope.kind;
  const merchantId = scope.kind === "seller" ? scope.merchantId : undefined;

  const load = useCallback(
    async (revision: number) => {
      const key = `${revision}:${from}:${to}`;
      if (requests.current.has(key) || document.visibilityState === "hidden") return;
      const first = Date.parse(from),
        last = Date.parse(to);
      if (!Number.isFinite(first) || !Number.isFinite(last) || first > last || (last - first) / 86400000 > 92) {
        setError("Chọn khoảng ngày hợp lệ, tối đa 93 ngày.");
        setLoading(false);
        return;
      }
      if (!campaign.id) {
        setError("Không thể xác định chiến dịch để tải thống kê.");
        setLoading(false);
        return;
      }
      requests.current.add(key);
      try {
        const data = await getAdCampaignStats(
          kind === "admin" ? { kind } : { kind, merchantId: merchantId! },
          campaign.id,
          from,
          to,
        );
        if (revision === generation.current) {
          setStats(data);
          setError("");
        }
      } catch (cause) {
        if (revision === generation.current) {
          if (cause instanceof CovieApiError && cause.status === 401) onAuth();
          else setError("Chưa thể cập nhật thống kê. Dữ liệu đang hiển thị có thể chưa mới nhất.");
        }
      } finally {
        requests.current.delete(key);
        if (revision === generation.current) setLoading(false);
      }
    },
    [campaign.id, kind, merchantId, from, to, onAuth],
  );

  useEffect(() => {
    const revision = ++generation.current;
    let active = true;
    void Promise.resolve().then(() => {
      if (active) return load(revision);
    });
    const timer = setInterval(() => void load(revision), 30000);
    const visible = () => {
      if (document.visibilityState === "visible") void load(revision);
    };
    document.addEventListener("visibilitychange", visible);
    return () => {
      active = false;
      generation.current = revision + 1;
      clearInterval(timer);
      document.removeEventListener("visibilitychange", visible);
    };
  }, [load]);

  function dateChange(value: string, start: boolean) {
    ++generation.current;
    setStats(null);
    setLoading(true);
    setError("");
    if (start) setFrom(value);
    else setTo(value);
  }

  const sourceDays = stats?.days ?? [];
  const range = Math.floor((Date.parse(to) - Date.parse(from)) / 86400000) + 1;
  const days =
    sourceDays.length && Number.isFinite(range) && range > 0 && range <= 93
      ? Array.from({ length: range }, (_, index) => {
          const date = new Date(Date.parse(from) + index * 86400000).toISOString().slice(0, 10);
          return sourceDays.find(day => day.date === date) ?? { date, impressions: 0, clicks: 0, ctr: 0 };
        })
      : [];

  const [hoveredDay, setHoveredDay] = useState<{
    date: string;
    impressions: number;
    clicks: number;
    ctr: number;
    x: number;
    y: number;
  } | null>(null);

  const maximum = Math.max(1, ...days.flatMap(day => [day.impressions ?? 0, day.clicks ?? 0]));
  const points = (metric: "impressions" | "clicks") =>
    days.map((day, index) => `${30 + (index * 540) / Math.max(1, days.length - 1)},${160 - ((day[metric] ?? 0) / maximum) * 140}`).join(" ");

  return (
    <section className={styles.panel} aria-label="Thống kê quảng cáo">
      <div className={styles.row}>
        <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
          <ChartLineUp size={22} weight="bold" color="#047857" aria-hidden="true" />
          <h2 style={{ margin: 0 }}>Hiệu quả · {campaign.title}</h2>
        </div>
        <button
          onClick={onClose}
          style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
        >
          <X size={16} aria-hidden="true" />
          <span>Đóng thống kê</span>
        </button>
      </div>

      <p style={{ marginTop: "6px" }}>Tự cập nhật mỗi 30 giây khi trang đang mở. Ngày thống kê theo UTC.</p>

      <div
        className={styles.fields}
        style={{
          marginTop: "16px",
          background: "#f8fafc",
          border: "1px solid #e2e8f0",
          borderRadius: "10px",
          padding: "16px",
        }}
      >
        <label>
          <span style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}>
            <CalendarBlank size={16} color="#047857" aria-hidden="true" />
            <span>Từ ngày</span>
          </span>
          <input type="date" value={from} onChange={e => dateChange(e.target.value, true)} />
        </label>
        <label>
          <span style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}>
            <CalendarBlank size={16} color="#047857" aria-hidden="true" />
            <span>Đến ngày</span>
          </span>
          <input type="date" value={to} onChange={e => dateChange(e.target.value, false)} />
        </label>
      </div>

      {loading && !stats && <p role="status" style={{ color: "#047857", fontWeight: 600, marginTop: "12px" }}>Đang tải thống kê…</p>}
      {error && <p role="alert" className={styles.reviewAlert} style={{ marginTop: "12px" }}>{error}</p>}

      <div style={{ marginTop: "14px", display: "flex", gap: "8px" }}>
        <button
          onClick={() => void load(generation.current)}
          style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
        >
          <ArrowsClockwise size={16} aria-hidden="true" />
          <span>Cập nhật thống kê</span>
        </button>
      </div>

      {stats && (
        <>
          <div className={styles.metrics}>
            <div>
              <span style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}>
                <Eye size={16} color="#047857" aria-hidden="true" />
                <span>Lượt xem</span>
              </span>
              <strong>{(stats.impressions ?? 0).toLocaleString("vi-VN")}</strong>
            </div>
            <div>
              <span style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}>
                <CursorClick size={16} color="#0284c7" aria-hidden="true" />
                <span>Lượt nhấp</span>
              </span>
              <strong>{(stats.clicks ?? 0).toLocaleString("vi-VN")}</strong>
            </div>
            <div>
              <span style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}>
                <TrendUp size={16} color="#7c3aed" aria-hidden="true" />
                <span>CTR</span>
              </span>
              <strong>{(stats.ctr ?? 0).toFixed(2)}%</strong>
            </div>
          </div>

          {days.length ? (
            <>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", margin: "14px 0 8px" }}>
                <p style={{ margin: 0, fontSize: "13px" }}>Lượt xem: đường liền · Lượt nhấp: đường nét đứt</p>
                <span style={{ fontSize: "12px", color: "#64748b" }}>Số liệu chuẩn hóa UTC</span>
              </div>
              <div className={styles.chartWrapper}>
                <svg
                  className={styles.chart}
                  viewBox="0 0 600 180"
                  role="img"
                  aria-label="Biểu đồ lượt xem và lượt nhấp theo ngày"
                >
                  <title>Lượt xem và lượt nhấp theo ngày UTC; số liệu chi tiết trong bảng bên dưới</title>
                  <line x1="30" y1="160" x2="570" y2="160" stroke="#cbd5e1" strokeWidth="1" />
                  <line x1="30" y1="90" x2="570" y2="90" stroke="#f1f5f9" strokeDasharray="3 3" />
                  <line x1="30" y1="20" x2="570" y2="20" stroke="#f1f5f9" strokeDasharray="3 3" />
                  <polyline points={points("impressions")} fill="none" stroke="#047857" strokeWidth="3" />
                  <polyline
                    points={points("clicks")}
                    fill="none"
                    stroke="#0284c7"
                    strokeWidth="2"
                    strokeDasharray="6 4"
                  />
                  {days.map((day, index) => {
                    const cx = 30 + (index * 540) / Math.max(1, days.length - 1);
                    const cyImp = 160 - ((day.impressions ?? 0) / maximum) * 140;
                    const cyClk = 160 - ((day.clicks ?? 0) / maximum) * 140;
                    return (
                      <g
                        key={day.date}
                        className={styles.chartPointGroup}
                        onMouseEnter={() =>
                          setHoveredDay({
                            date: day.date ?? "",
                            impressions: day.impressions ?? 0,
                            clicks: day.clicks ?? 0,
                            ctr: day.ctr ?? 0,
                            x: cx,
                            y: Math.min(cyImp, cyClk),
                          })
                        }
                        onMouseLeave={() => setHoveredDay(null)}
                      >
                        <circle cx={cx} cy={cyImp} r="3" fill="#047857" className={styles.chartPoint} />
                        <circle cx={cx} cy={cyClk} r="3" fill="#0284c7" className={styles.chartPoint} />
                        <title>
                          {day.date}: {day.impressions} lượt xem, {day.clicks} lượt nhấp
                        </title>
                      </g>
                    );
                  })}
                </svg>
                {hoveredDay && (
                  <div
                    className={styles.chartTooltip}
                    style={{
                      left: `${(hoveredDay.x / 600) * 100}%`,
                      top: `${Math.max(15, (hoveredDay.y / 180) * 100)}%`,
                    }}
                    role="tooltip"
                  >
                    <strong>{hoveredDay.date}</strong>
                    <span>Lượt xem: {hoveredDay.impressions.toLocaleString("vi-VN")}</span>
                    <span>Lượt nhấp: {hoveredDay.clicks.toLocaleString("vi-VN")}</span>
                    <span>CTR: {hoveredDay.ctr.toFixed(2)}%</span>
                  </div>
                )}
              </div>
              <div className={styles.table}>
                <table>
                  <caption>Lượt xem và lượt nhấp theo ngày UTC</caption>
                  <thead>
                    <tr>
                      <th>Ngày</th>
                      <th>Lượt xem</th>
                      <th>Lượt nhấp</th>
                      <th>CTR</th>
                    </tr>
                  </thead>
                  <tbody>
                    {days.map(day => (
                      <tr key={day.date}>
                        <th scope="row">{day.date}</th>
                        <td>{day.impressions}</td>
                        <td>{day.clicks}</td>
                        <td>{(day.ctr ?? 0).toFixed(2)}%</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          ) : (
            <p style={{ marginTop: "16px" }}>Chưa có lượt xem trong khoảng ngày này.</p>
          )}
        </>
      )}
    </section>
  );
}
