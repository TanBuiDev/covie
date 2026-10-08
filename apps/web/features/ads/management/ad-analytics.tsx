"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { getAdCampaignStats, type AdCampaign, type AdScope, type AdStats } from "../ad-api";
import { CovieApiError } from "@/lib/api/api-error";
import styles from "./management.module.css";
export function AdAnalytics({ scope, campaign, onClose, onAuth }: { scope: AdScope; campaign: AdCampaign; onClose: () => void; onAuth: () => void }) {
  const [from, setFrom] = useState(() => new Date(Date.now() - 29 * 86400000).toISOString().slice(0, 10));
  const [to, setTo] = useState(() => new Date().toISOString().slice(0, 10));
  const [stats, setStats] = useState<AdStats | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const requests = useRef(new Set<string>());
  const generation = useRef(0);
  const kind = scope.kind;
  const merchantId = scope.kind === "seller" ? scope.merchantId : undefined;
  const load = useCallback(async (revision: number) => {
    const key = `${revision}:${from}:${to}`;
    if (requests.current.has(key) || document.visibilityState === "hidden") return;
    const first = Date.parse(from), last = Date.parse(to);
    if (!Number.isFinite(first) || !Number.isFinite(last) || first > last || (last - first) / 86400000 > 92) { setError("Chọn khoảng ngày hợp lệ, tối đa 93 ngày."); setLoading(false); return; }
    if (!campaign.id) { setError("Không thể xác định chiến dịch để tải thống kê."); setLoading(false); return; }
    requests.current.add(key);
    try { const data = await getAdCampaignStats(kind === "admin" ? { kind } : { kind, merchantId: merchantId! }, campaign.id, from, to); if (revision === generation.current) { setStats(data); setError(""); } }
    catch (cause) { if (revision === generation.current) { if (cause instanceof CovieApiError && cause.status === 401) onAuth(); else setError("Chưa thể cập nhật thống kê. Dữ liệu đang hiển thị có thể chưa mới nhất."); } }
    finally { requests.current.delete(key); if (revision === generation.current) setLoading(false); }
  }, [campaign.id, kind, merchantId, from, to, onAuth]);
  useEffect(() => { const revision = ++generation.current; let active = true; void Promise.resolve().then(() => { if (active) return load(revision); }); const timer = setInterval(() => void load(revision), 30000); const visible = () => { if (document.visibilityState === "visible") void load(revision); }; document.addEventListener("visibilitychange", visible); return () => { active = false; generation.current = revision + 1; clearInterval(timer); document.removeEventListener("visibilitychange", visible); }; }, [load]);
  function dateChange(value: string, start: boolean) { ++generation.current; setStats(null); setLoading(true); setError(""); if (start) setFrom(value); else setTo(value); }
  const sourceDays = stats?.days ?? [];
  const range = Math.floor((Date.parse(to) - Date.parse(from)) / 86400000) + 1;
  const days = sourceDays.length && Number.isFinite(range) && range > 0 && range <= 93 ? Array.from({ length: range }, (_, index) => {
    const date = new Date(Date.parse(from) + index * 86400000).toISOString().slice(0, 10);
    return sourceDays.find(day => day.date === date) ?? { date, impressions: 0, clicks: 0, ctr: 0 };
  }) : [];
  const maximum = Math.max(1, ...days.flatMap(day => [day.impressions ?? 0, day.clicks ?? 0]));
  const points = (metric: "impressions" | "clicks") => days.map((day, index) => `${30 + index * 540 / Math.max(1, days.length - 1)},${160 - (day[metric] ?? 0) / maximum * 140}`).join(" ");
  return <section className={styles.panel} aria-label="Thống kê quảng cáo"><div className={styles.row}><h2>Hiệu quả · {campaign.title}</h2><button onClick={onClose}>Đóng thống kê</button></div><p>Tự cập nhật mỗi 30 giây khi trang đang mở. Ngày thống kê theo UTC.</p><div className={styles.fields}><label>Từ ngày<input type="date" value={from} onChange={e => dateChange(e.target.value, true)} /></label><label>Đến ngày<input type="date" value={to} onChange={e => dateChange(e.target.value, false)} /></label></div>{loading && !stats && <p role="status">Đang tải thống kê…</p>}{error && <p role="alert">{error}</p>}<button onClick={() => void load(generation.current)}>Cập nhật thống kê</button>
    {stats && <><div className={styles.metrics}><div>Lượt xem<strong>{(stats.impressions ?? 0).toLocaleString("vi-VN")}</strong></div><div>Lượt nhấp<strong>{(stats.clicks ?? 0).toLocaleString("vi-VN")}</strong></div><div>CTR<strong>{(stats.ctr ?? 0).toFixed(2)}%</strong></div></div>
      {days.length ? <><p>Lượt xem: đường liền · Lượt nhấp: đường nét đứt</p><svg className={styles.chart} viewBox="0 0 600 180" role="img" aria-label="Biểu đồ lượt xem và lượt nhấp theo ngày"><title>Lượt xem và lượt nhấp theo ngày UTC; số liệu chi tiết trong bảng bên dưới</title><line x1="30" y1="160" x2="570" y2="160" stroke="currentColor" opacity=".25" /><polyline points={points("impressions")} fill="none" stroke="currentColor" strokeWidth="3" /><polyline points={points("clicks")} fill="none" stroke="currentColor" strokeWidth="2" strokeDasharray="6 4" />{days.map((day, index) => <g key={day.date}><circle cx={30 + index * 540 / Math.max(1, days.length - 1)} cy={160 - (day.impressions ?? 0) / maximum * 140} r="3" fill="currentColor" /><circle cx={30 + index * 540 / Math.max(1, days.length - 1)} cy={160 - (day.clicks ?? 0) / maximum * 140} r="3" fill="currentColor" /><title>{day.date}: {day.impressions} lượt xem, {day.clicks} lượt nhấp</title></g>)}</svg><div className={styles.table}><table><caption>Lượt xem và lượt nhấp theo ngày UTC</caption><thead><tr><th>Ngày</th><th>Lượt xem</th><th>Lượt nhấp</th><th>CTR</th></tr></thead><tbody>{days.map(day => <tr key={day.date}><th scope="row">{day.date}</th><td>{day.impressions}</td><td>{day.clicks}</td><td>{(day.ctr ?? 0).toFixed(2)}%</td></tr>)}</tbody></table></div></> : <p>Chưa có lượt xem trong khoảng ngày này.</p>}
    </>}
  </section>;
}
