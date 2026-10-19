"use client";

import { Info, TrendDown, TrendUp } from "@phosphor-icons/react";
import type { KpiMetric } from "./clarity-types";
import styles from "./clarity-ops.module.css";

interface ClarityKpiCardProps {
  metric: KpiMetric;
  isSelected?: boolean;
  onSelect?: (metric: KpiMetric) => void;
}

export function ClarityKpiCard({ metric, isSelected = false, onSelect }: ClarityKpiCardProps) {
  const isPositiveDelta = metric.delta >= 0;
  // Giảm hoàn tiền là tốt; tăng doanh thu/đơn là tốt
  const isBeneficial = metric.isGood;

  // Mini sparkline SVG generator
  const sparklinePoints = metric.sparkline;
  const minVal = Math.min(...sparklinePoints);
  const maxVal = Math.max(...sparklinePoints);
  const range = maxVal - minVal || 1;
  const width = 72;
  const height = 28;

  const pointsString = sparklinePoints
    .map((val, idx) => {
      const x = (idx / (sparklinePoints.length - 1)) * width;
      const y = height - ((val - minVal) / range) * (height - 6) - 3;
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(" ");

  const strokeColor = isBeneficial ? "var(--clarity-state-success)" : "var(--clarity-state-danger)";

  const handleClick = () => {
    if (onSelect) {
      onSelect(metric);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      handleClick();
    }
  };

  const formattedDeltaText = `${isPositiveDelta ? "+" : ""}${metric.delta}${
    metric.deltaType === "pp" ? " pp" : "%"
  }`;

  return (
    <div
      role="button"
      tabIndex={0}
      className={`${styles.kpiCard} ${isSelected ? styles.kpiCardSelected : ""}`}
      onClick={handleClick}
      onKeyDown={handleKeyDown}
      aria-label={`${metric.label}: ${metric.formattedValue}, ${formattedDeltaText} ${metric.comparisonPeriod}`}
    >
      <div className={styles.kpiHeader}>
        <span className={styles.kpiLabel}>{metric.label}</span>
        <span className={styles.kpiTooltipIcon} title={metric.definition} aria-label={metric.definition}>
          <Info size={16} weight="regular" />
        </span>
      </div>

      <div className={styles.kpiBody}>
        <div className={styles.kpiValue}>{metric.formattedValue}</div>
        <div className={styles.kpiSparklineContainer} aria-hidden="true">
          <svg className={styles.chartSvg} viewBox={`0 0 ${width} ${height}`}>
            <polyline
              fill="none"
              stroke={strokeColor}
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              points={pointsString}
            />
          </svg>
        </div>
      </div>

      <div className={styles.kpiFooter}>
        <span
          className={`${styles.deltaBadge} ${
            isBeneficial ? styles.deltaPositive : styles.deltaNegative
          }`}
        >
          {isPositiveDelta ? (
            <TrendUp size={12} weight="bold" />
          ) : (
            <TrendDown size={12} weight="bold" />
          )}
          <span>{formattedDeltaText}</span>
        </span>
        <span className={styles.comparisonPeriod}>{metric.comparisonPeriod}</span>
      </div>
    </div>
  );
}
