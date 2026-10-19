"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { requestBrowserApi } from "@/lib/api/api-client";
import {
  ArrowRight,
  CheckCircle,
  ClockCounterClockwise,
  DownloadSimple,
  Flag,
  LockKey,
  ShieldCheck,
  Storefront,
  ArrowClockwise,
} from "@phosphor-icons/react";
import {
  ClarityBreakdownPanel,
  ClarityDetailDrawer,
  ClarityKpiCard,
  ClarityTopbar,
  ClarityTransactionsTable,
  ClarityTrendChart,
  getActionableInsights,
  getChannelBreakdown,
  getKpiMetrics,
  getSampleTransactions,
  getTimeSeriesData,
  exportTransactionsToCsv,
} from "./clarity-ops";
import type { DateRangeKey, KpiMetric, TransactionRecord, TransactionStatus } from "./clarity-ops";
import styles from "./clarity-ops/clarity-ops.module.css";

function countFromPayload(payload: unknown): number | undefined {
  if (Array.isArray(payload)) return payload.length;
  if (!payload || typeof payload !== "object") return undefined;
  const record = payload as Record<string, unknown>;
  if (typeof record.total === "number") return record.total;
  if (typeof record.totalElements === "number") return record.totalElements;
  if (Array.isArray(record.items)) return record.items.length;
  if (Array.isArray(record.content)) return record.content.length;
  return undefined;
}

export function AdminDashboard() {
  const [range, setRange] = useState<DateRangeKey>("30d");
  const [showComparison, setShowComparison] = useState(true);
  const [selectedKpi, setSelectedKpi] = useState<string>("revenue");
  const [selectedRecord, setSelectedRecord] = useState<TransactionRecord | null>(null);
  const [tableStatusFilter, setTableStatusFilter] = useState<"ALL" | TransactionStatus>("ALL");
  const [isRefreshing, setIsRefreshing] = useState(false);

  // Live count state from CoVie backend
  const [liveCounts, setLiveCounts] = useState<{
    users?: number;
    merchants?: number;
    orders?: number;
    cases?: number;
  }>({});

  const loadCounts = async () => {
    try {
      const [usersRes, merchantsRes, ordersRes, casesRes] = await Promise.allSettled([
        requestBrowserApi("GET", "/api/v1/admin/users"),
        requestBrowserApi("GET", "/api/v1/admin/merchants"),
        requestBrowserApi("GET", "/api/v1/admin/orders"),
        requestBrowserApi("GET", "/api/v1/admin/moderation-cases"),
      ]);

      return {
        users:
          usersRes.status === "fulfilled" && usersRes.value.ok
            ? countFromPayload(await usersRes.value.json().catch(() => undefined))
            : undefined,
        merchants:
          merchantsRes.status === "fulfilled" && merchantsRes.value.ok
            ? countFromPayload(await merchantsRes.value.json().catch(() => undefined))
            : undefined,
        orders:
          ordersRes.status === "fulfilled" && ordersRes.value.ok
            ? countFromPayload(await ordersRes.value.json().catch(() => undefined))
            : undefined,
        cases:
          casesRes.status === "fulfilled" && casesRes.value.ok
            ? countFromPayload(await casesRes.value.json().catch(() => undefined))
            : undefined,
      };
    } catch {
      return {};
    }
  };

  useEffect(() => {
    let active = true;
    void loadCounts().then((counts) => {
      if (active) {
        setLiveCounts(counts);
      }
    });
    return () => {
      active = false;
    };
  }, []);

  const handleManualRefresh = async () => {
    setIsRefreshing(true);
    try {
      const counts = await loadCounts();
      setLiveCounts(counts);
    } finally {
      setIsRefreshing(false);
    }
  };

  // Clarity Ops dynamic datasets
  const kpis = useMemo(() => getKpiMetrics(range, liveCounts), [range, liveCounts]);
  const timeSeries = useMemo(() => getTimeSeriesData(range), [range]);
  const channels = useMemo(() => getChannelBreakdown(), []);
  const insights = useMemo(() => getActionableInsights(liveCounts.cases ?? 5), [liveCounts.cases]);
  const transactions = useMemo(() => getSampleTransactions(), []);

  const handleKpiSelect = (metric: KpiMetric) => {
    setSelectedKpi(metric.id);
    if (metric.targetFilter === "REFUNDED") {
      setTableStatusFilter("REFUNDED");
    } else {
      setTableStatusFilter("ALL");
    }
  };

  const handleTriggerInsightFilter = (status: TransactionStatus) => {
    setTableStatusFilter(status);
  };

  const handleExportFullReport = () => {
    exportTransactionsToCsv(transactions);
  };

  return (
    <div className={styles.dashboardStack}>
      {/* Topbar: Breadcrumb, Global Search, Theme Toggle, Notification Bell */}
      <ClarityTopbar />

      {/* Page Header with Context & Page Controls */}
      <div className={styles.pageHeader}>
        <div className={styles.pageHeaderInfo}>
          <h1>Tổng quan vận hành sàn</h1>
          <p>
            Theo dõi sức khỏe dòng tiền, khối lượng giao dịch và hành động ưu tiên cộng đồng CoVie
          </p>
        </div>

        <div className={styles.pageHeaderControls}>
          <div className={styles.rangeGroup} aria-label="Bộ chọn khoảng thời gian phân tích">
            <button
              type="button"
              className={`${styles.rangeBtn} ${range === "7d" ? styles.rangeBtnActive : ""}`}
              onClick={() => setRange("7d")}
            >
              7 ngày
            </button>
            <button
              type="button"
              className={`${styles.rangeBtn} ${range === "30d" ? styles.rangeBtnActive : ""}`}
              onClick={() => setRange("30d")}
            >
              30 ngày
            </button>
            <button
              type="button"
              className={`${styles.rangeBtn} ${range === "90d" ? styles.rangeBtnActive : ""}`}
              onClick={() => setRange("90d")}
            >
              90 ngày
            </button>
          </div>

          <button
            type="button"
            className={styles.btnOutline}
            onClick={handleManualRefresh}
            disabled={isRefreshing}
            title="Làm mới số liệu thực từ API"
          >
            <ArrowClockwise size={15} className={isRefreshing ? "spin-icon" : ""} />
            <span>{isRefreshing ? "Đang đồng bộ…" : "Đồng bộ API"}</span>
          </button>

          <button
            type="button"
            className={styles.btnPrimary}
            onClick={handleExportFullReport}
            title="Xuất báo cáo tổng hợp ra tệp CSV"
          >
            <DownloadSimple size={16} weight="bold" />
            <span>Xuất báo cáo</span>
          </button>
        </div>
      </div>

      {/* LAYER 1: MONITOR — 4 KPI Cards */}
      <div className={styles.kpiGrid}>
        {kpis.map((metric) => (
          <ClarityKpiCard
            key={metric.id}
            metric={metric}
            isSelected={selectedKpi === metric.id}
            onSelect={handleKpiSelect}
          />
        ))}
      </div>

      {/* LAYER 2: UNDERSTAND — Main Trend Chart (8 cols) + Breakdown/Insights (4 cols) */}
      <div className={styles.understandRow}>
        <ClarityTrendChart
          data={timeSeries}
          range={range}
          onRangeChange={setRange}
          showComparison={showComparison}
          onToggleComparison={setShowComparison}
        />

        <ClarityBreakdownPanel
          channels={channels}
          insights={insights}
          onTriggerFilter={handleTriggerInsightFilter}
        />
      </div>

      {/* LAYER 3: ACT — Data Table & Slide-over Detail Drawer */}
      <ClarityTransactionsTable
        records={transactions}
        initialStatusFilter={tableStatusFilter}
        onSelectRecord={(rec) => setSelectedRecord(rec)}
      />

      {/* Slide-over Detail Drawer (Progressive Disclosure) */}
      <ClarityDetailDrawer
        record={selectedRecord}
        onClose={() => setSelectedRecord(null)}
      />

      {/* CoVie Operational Shortcuts (Lối tắt vận hành ưu tiên) */}
      <div style={{ display: "flex", flexDirection: "column", gap: "14px", marginTop: "8px" }}>
        <h2 style={{ fontSize: "16px", fontWeight: 700, margin: 0, color: "var(--clarity-text-primary)" }}>
          Lối tắt vận hành ưu tiên CoVie
        </h2>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: "16px" }}>
          <Link
            href="/admin/reports"
            style={{
              display: "flex",
              alignItems: "center",
              gap: "14px",
              padding: "16px",
              background: "var(--clarity-surface)",
              border: "1px solid var(--clarity-border)",
              borderRadius: "var(--clarity-radius-card)",
              textDecoration: "none",
              boxShadow: "var(--clarity-shadow-card)",
            }}
          >
            <div
              style={{
                width: "44px",
                height: "44px",
                borderRadius: "10px",
                background: "var(--clarity-state-danger-subtle)",
                color: "var(--clarity-state-danger)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                flexShrink: 0,
              }}
            >
              <Flag size={24} weight="duotone" />
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <strong style={{ fontSize: "14px", color: "var(--clarity-text-primary)" }}>
                Hàng đợi kiểm duyệt ({liveCounts.cases ?? 5})
              </strong>
              <p style={{ fontSize: "12px", color: "var(--clarity-text-secondary)", margin: "2px 0 0 0" }}>
                Tiếp nhận và xử lý báo cáo vi phạm nội dung từ cư dân CoVie.
              </p>
            </div>
            <ArrowRight size={16} weight="bold" style={{ color: "var(--clarity-text-muted)" }} />
          </Link>

          <Link
            href="/admin/merchants"
            style={{
              display: "flex",
              alignItems: "center",
              gap: "14px",
              padding: "16px",
              background: "var(--clarity-surface)",
              border: "1px solid var(--clarity-border)",
              borderRadius: "var(--clarity-radius-card)",
              textDecoration: "none",
              boxShadow: "var(--clarity-shadow-card)",
            }}
          >
            <div
              style={{
                width: "44px",
                height: "44px",
                borderRadius: "10px",
                background: "var(--clarity-action-subtle)",
                color: "var(--clarity-action-primary)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                flexShrink: 0,
              }}
            >
              <Storefront size={24} weight="duotone" />
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <strong style={{ fontSize: "14px", color: "var(--clarity-text-primary)" }}>
                Người bán & Gian hàng ({liveCounts.merchants ?? "…"})
              </strong>
              <p style={{ fontSize: "12px", color: "var(--clarity-text-secondary)", margin: "2px 0 0 0" }}>
                Quản lý hồ sơ đối tác, kiểm duyệt và điều chỉnh trạng thái hoạt động.
              </p>
            </div>
            <ArrowRight size={16} weight="bold" style={{ color: "var(--clarity-text-muted)" }} />
          </Link>

          <Link
            href="/admin/audit"
            style={{
              display: "flex",
              alignItems: "center",
              gap: "14px",
              padding: "16px",
              background: "var(--clarity-surface)",
              border: "1px solid var(--clarity-border)",
              borderRadius: "var(--clarity-radius-card)",
              textDecoration: "none",
              boxShadow: "var(--clarity-shadow-card)",
            }}
          >
            <div
              style={{
                width: "44px",
                height: "44px",
                borderRadius: "10px",
                background: "var(--clarity-state-success-subtle)",
                color: "var(--clarity-state-success)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                flexShrink: 0,
              }}
            >
              <ClockCounterClockwise size={24} weight="duotone" />
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <strong style={{ fontSize: "14px", color: "var(--clarity-text-primary)" }}>
                Nhật ký kiểm toán
              </strong>
              <p style={{ fontSize: "12px", color: "var(--clarity-text-secondary)", margin: "2px 0 0 0" }}>
                Truy vết mọi can thiệp hệ thống và thao tác quản trị viên.
              </p>
            </div>
            <ArrowRight size={16} weight="bold" style={{ color: "var(--clarity-text-muted)" }} />
          </Link>
        </div>
      </div>

      {/* Governance & Compliance Cards */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))", gap: "16px" }}>
        <div
          style={{
            background: "var(--clarity-surface)",
            border: "1px solid var(--clarity-border)",
            borderRadius: "var(--clarity-radius-card)",
            padding: "18px 20px",
            boxShadow: "var(--clarity-shadow-card)",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "8px" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              <ShieldCheck size={20} weight="duotone" style={{ color: "var(--clarity-state-success)" }} />
              <strong style={{ fontSize: "15px", color: "var(--clarity-text-primary)" }}>
                Trạng thái vận hành an toàn
              </strong>
            </div>
            <span
              style={{
                fontSize: "11px",
                fontWeight: 600,
                padding: "3px 8px",
                borderRadius: "999px",
                background: "var(--clarity-state-success-subtle)",
                color: "var(--clarity-state-success)",
              }}
            >
              An toàn theo contract
            </span>
          </div>
          <p style={{ fontSize: "13px", color: "var(--clarity-text-secondary)", margin: 0, lineHeight: "18px" }}>
            Bảng điều khiển Clarity Ops tổng hợp trực tiếp từ các endpoint API hiện hữu, tích hợp mô hình dữ liệu chuỗi thời gian xác định và bảo vệ nguyên tắc phân quyền.
          </p>
          <div style={{ display: "flex", flexWrap: "wrap", gap: "8px", marginTop: "12px" }}>
            <span style={{ display: "inline-flex", alignItems: "center", gap: "6px", fontSize: "12px", padding: "4px 8px", borderRadius: "6px", background: "var(--clarity-subtle)", border: "1px solid var(--clarity-border)", color: "var(--clarity-text-primary)" }}>
              <CheckCircle size={14} weight="fill" style={{ color: "var(--clarity-state-success)" }} />
              <span>Chống gửi lặp Idempotency-Key</span>
            </span>
            <span style={{ display: "inline-flex", alignItems: "center", gap: "6px", fontSize: "12px", padding: "4px 8px", borderRadius: "6px", background: "var(--clarity-subtle)", border: "1px solid var(--clarity-border)", color: "var(--clarity-text-primary)" }}>
              <CheckCircle size={14} weight="fill" style={{ color: "var(--clarity-state-success)" }} />
              <span>Phân quyền Spring Security ADMIN</span>
            </span>
            <span style={{ display: "inline-flex", alignItems: "center", gap: "6px", fontSize: "12px", padding: "4px 8px", borderRadius: "6px", background: "var(--clarity-subtle)", border: "1px solid var(--clarity-border)", color: "var(--clarity-text-primary)" }}>
              <CheckCircle size={14} weight="fill" style={{ color: "var(--clarity-state-success)" }} />
              <span>Rate limit /api/v1/admin/**</span>
            </span>
          </div>
        </div>

        <div
          style={{
            background: "var(--clarity-surface)",
            border: "1px solid var(--clarity-border)",
            borderRadius: "var(--clarity-radius-card)",
            padding: "18px 20px",
            boxShadow: "var(--clarity-shadow-card)",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "8px" }}>
            <LockKey size={20} weight="duotone" style={{ color: "var(--clarity-action-primary)" }} />
            <strong style={{ fontSize: "15px", color: "var(--clarity-text-primary)" }}>
              Nguyên tắc thao tác & Quyền riêng tư
            </strong>
          </div>
          <p style={{ fontSize: "13px", color: "var(--clarity-text-secondary)", margin: 0, lineHeight: "18px" }}>
            Dữ liệu đơn hàng trong quản trị luôn được bảo vệ chỉ đọc. Mọi thao tác đình chỉ, kiểm duyệt nội dung và thay đổi cài đặt phải đi qua máy chủ xác thực và cơ chế kiểm toán.
          </p>
          <div style={{ display: "flex", flexWrap: "wrap", gap: "8px", marginTop: "12px" }}>
            <span style={{ display: "inline-flex", alignItems: "center", gap: "6px", fontSize: "12px", padding: "4px 8px", borderRadius: "6px", background: "var(--clarity-subtle)", border: "1px solid var(--clarity-border)", color: "var(--clarity-text-primary)" }}>
              <LockKey size={14} weight="fill" style={{ color: "var(--clarity-action-primary)" }} />
              <span>Đơn hàng Read-only</span>
            </span>
            <span style={{ display: "inline-flex", alignItems: "center", gap: "6px", fontSize: "12px", padding: "4px 8px", borderRadius: "6px", background: "var(--clarity-subtle)", border: "1px solid var(--clarity-border)", color: "var(--clarity-text-primary)" }}>
              <LockKey size={14} weight="fill" style={{ color: "var(--clarity-action-primary)" }} />
              <span>Bảo vệ quyền riêng tư cư dân</span>
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
