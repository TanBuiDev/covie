"use client";

import Link from "next/link";
import { ArrowRight, Lightning, ChartPieSlice } from "@phosphor-icons/react";
import type { ActionableInsight, ChannelShare, TransactionStatus } from "./clarity-types";
import { formatVnd } from "./clarity-data";
import styles from "./clarity-ops.module.css";

interface ClarityBreakdownPanelProps {
  channels: ChannelShare[];
  insights: ActionableInsight[];
  onTriggerFilter?: (status: TransactionStatus) => void;
}

export function ClarityBreakdownPanel({
  channels,
  insights,
  onTriggerFilter,
}: ClarityBreakdownPanelProps) {
  return (
    <div className={styles.cardContainer}>
      <div className={styles.cardHeader}>
        <div className={styles.cardTitleGroup}>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <ChartPieSlice size={20} weight="duotone" style={{ color: "var(--clarity-action-primary)" }} />
            <h2>Phân bổ & Hành động</h2>
          </div>
          <p>Tỷ trọng kênh doanh thu & Đề xuất hành động ưu tiên</p>
        </div>
      </div>

      <div className={styles.breakdownStack}>
        {/* Channel Breakdown */}
        <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
          {channels.map((ch) => (
            <div key={ch.id} className={styles.channelItem}>
              <div className={styles.channelInfo}>
                <span className={styles.channelName}>{ch.name}</span>
                <span className={styles.channelStats}>
                  <span>{formatVnd(ch.amount)}</span>
                  <span style={{ color: "var(--clarity-text-muted)" }}>({ch.percentage}%)</span>
                </span>
              </div>
              <div className={styles.channelTrack}>
                <div
                  className={styles.channelFill}
                  style={{
                    width: `${ch.percentage}%`,
                    backgroundColor: ch.color,
                  }}
                />
              </div>
            </div>
          ))}
        </div>

        {/* Actionable Insights Section */}
        <div style={{ display: "flex", flexDirection: "column", gap: "10px", marginTop: "8px" }}>
          <div style={{ display: "flex", alignItems: "center", gap: "6px", fontSize: "13px", fontWeight: 600, color: "var(--clarity-text-primary)" }}>
            <Lightning size={16} weight="fill" style={{ color: "var(--clarity-state-warning)" }} />
            <span>Phát hiện & Hành động ưu tiên</span>
          </div>

          {insights.map((ins) => (
            <div key={ins.id} className={styles.insightCard}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                <strong className={styles.insightCardTitle}>{ins.title}</strong>
                <span style={{ fontSize: "10px", padding: "2px 6px", borderRadius: "4px", background: "var(--clarity-surface)", border: "1px solid var(--clarity-border)", color: "var(--clarity-text-muted)" }}>
                  {ins.tag}
                </span>
              </div>
              <p className={styles.insightCardDesc}>{ins.description}</p>
              {ins.ctaHref ? (
                <Link href={ins.ctaHref} className={styles.insightCardBtn}>
                  <span>{ins.ctaText}</span>
                  <ArrowRight size={12} weight="bold" />
                </Link>
              ) : ins.filterStatus && onTriggerFilter ? (
                <button
                  type="button"
                  className={styles.insightCardBtn}
                  onClick={() => onTriggerFilter(ins.filterStatus!)}
                >
                  <span>{ins.ctaText}</span>
                  <ArrowRight size={12} weight="bold" />
                </button>
              ) : null}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
