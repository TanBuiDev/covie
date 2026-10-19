"use client";

import { useState } from "react";
import { Table, ChartLine } from "@phosphor-icons/react";
import type { DateRangeKey, TimePoint } from "./clarity-types";
import { formatVnd } from "./clarity-data";
import styles from "./clarity-ops.module.css";

interface ClarityTrendChartProps {
  data: TimePoint[];
  range: DateRangeKey;
  onRangeChange: (r: DateRangeKey) => void;
  showComparison: boolean;
  onToggleComparison: (val: boolean) => void;
}

export function ClarityTrendChart({
  data,
  range,
  onRangeChange,
  showComparison,
  onToggleComparison,
}: ClarityTrendChartProps) {
  const [hoverIndex, setHoverIndex] = useState<number | null>(null);
  const [showTableView, setShowTableView] = useState(false);

  // SVG dimensions
  const svgWidth = 800;
  const svgHeight = 260;
  const paddingX = 40;
  const paddingY = 30;
  const plotWidth = svgWidth - paddingX * 2;
  const plotHeight = svgHeight - paddingY * 2;

  const allValues = [
    ...data.map((d) => d.primary),
    ...(showComparison ? data.map((d) => d.compare) : []),
  ];
  const maxVal = Math.max(...allValues) * 1.15 || 10000000;
  const minVal = 0;

  const getCoordinates = (index: number, val: number) => {
    const x = paddingX + (index / (data.length - 1)) * plotWidth;
    const y = paddingY + plotHeight - ((val - minVal) / (maxVal - minVal)) * plotHeight;
    return { x, y };
  };

  const primaryPoints = data.map((d, i) => getCoordinates(i, d.primary));
  const comparePoints = data.map((d, i) => getCoordinates(i, d.compare));

  const primaryPolyline = primaryPoints.map((p) => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(" ");
  const comparePolyline = comparePoints.map((p) => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(" ");

  // Gradient area
  const firstPt = primaryPoints[0] ?? { x: paddingX, y: paddingY + plotHeight };
  const lastPt = primaryPoints[primaryPoints.length - 1] ?? { x: svgWidth - paddingX, y: paddingY + plotHeight };
  const areaPolygon = `${firstPt.x},${paddingY + plotHeight} ${primaryPolyline} ${lastPt.x},${paddingY + plotHeight}`;

  const currentHoverItem = hoverIndex !== null && hoverIndex < data.length ? data[hoverIndex] : null;
  const currentHoverCoord = hoverIndex !== null && hoverIndex < primaryPoints.length ? primaryPoints[hoverIndex] : null;

  return (
    <div className={styles.cardContainer}>
      <div className={styles.cardHeader}>
        <div className={styles.cardTitleGroup}>
          <h2>Xu hướng doanh thu thuần</h2>
          <p>Dữ liệu chuỗi thời gian so sánh kỳ liên kế hoạch định</p>
        </div>

        <div className={styles.cardControls}>
          <div className={styles.rangeGroup}>
            <button
              type="button"
              className={`${styles.rangeBtn} ${range === "7d" ? styles.rangeBtnActive : ""}`}
              onClick={() => onRangeChange("7d")}
            >
              7 ngày
            </button>
            <button
              type="button"
              className={`${styles.rangeBtn} ${range === "30d" ? styles.rangeBtnActive : ""}`}
              onClick={() => onRangeChange("30d")}
            >
              30 ngày
            </button>
            <button
              type="button"
              className={`${styles.rangeBtn} ${range === "90d" ? styles.rangeBtnActive : ""}`}
              onClick={() => onRangeChange("90d")}
            >
              90 ngày
            </button>
          </div>

          <label className={styles.compareToggle}>
            <input
              type="checkbox"
              checked={showComparison}
              onChange={(e) => onToggleComparison(e.target.checked)}
            />
            <span>So sánh kỳ trước</span>
          </label>

          <button
            type="button"
            className={styles.btnOutline}
            style={{ padding: "6px 10px", fontSize: "12px", minHeight: "32px" }}
            onClick={() => setShowTableView(!showTableView)}
            title={showTableView ? "Xem dạng biểu đồ" : "Xem dạng bảng (WCAG Accessible Table)"}
            aria-label={showTableView ? "Xem dạng biểu đồ" : "Xem dạng bảng"}
          >
            {showTableView ? <ChartLine size={16} /> : <Table size={16} />}
            <span>{showTableView ? "Biểu đồ" : "Bảng số liệu"}</span>
          </button>
        </div>
      </div>

      {showTableView ? (
        <div className={styles.tableWrapper} style={{ maxHeight: "280px" }}>
          <table className={styles.dataTable} aria-label="Bảng số liệu xu hướng doanh thu">
            <thead>
              <tr>
                <th scope="col">Ngày / Kỳ</th>
                <th scope="col">Kỳ này (VND)</th>
                {showComparison && <th scope="col">Kỳ trước (VND)</th>}
                {showComparison && <th scope="col">Chênh lệch</th>}
              </tr>
            </thead>
            <tbody>
              {data.map((item) => {
                const diff = item.primary - item.compare;
                return (
                  <tr key={item.date}>
                    <td><strong>{item.label}</strong> ({item.date})</td>
                    <td>{formatVnd(item.primary)}</td>
                    {showComparison && <td>{formatVnd(item.compare)}</td>}
                    {showComparison && (
                      <td style={{ color: diff >= 0 ? "var(--clarity-state-success)" : "var(--clarity-state-danger)" }}>
                        {diff >= 0 ? "+" : ""}{formatVnd(diff)}
                      </td>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      ) : (
        <div
          className={styles.chartCanvasWrapper}
          onMouseLeave={() => setHoverIndex(null)}
        >
          <svg
            className={styles.chartSvg}
            viewBox={`0 0 ${svgWidth} ${svgHeight}`}
            preserveAspectRatio="none"
          >
            <defs>
              <linearGradient id="clarityAreaGradient" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="var(--clarity-chart-primary)" stopOpacity="0.28" />
                <stop offset="100%" stopColor="var(--clarity-chart-primary)" stopOpacity="0.0" />
              </linearGradient>
            </defs>

            {/* Gridlines */}
            {[0, 0.25, 0.5, 0.75, 1].map((pct, i) => {
              const y = paddingY + plotHeight * pct;
              return (
                <line
                  key={i}
                  x1={paddingX}
                  y1={y}
                  x2={svgWidth - paddingX}
                  y2={y}
                  stroke="var(--clarity-border)"
                  strokeDasharray="4 4"
                  strokeWidth="1"
                  opacity="0.6"
                />
              );
            })}

            {/* Area polygon */}
            <polygon points={areaPolygon} fill="url(#clarityAreaGradient)" />

            {/* Compare line (dashed) */}
            {showComparison && (
              <polyline
                fill="none"
                stroke="var(--clarity-chart-compare)"
                strokeWidth="2"
                strokeDasharray="5 5"
                strokeLinecap="round"
                strokeLinejoin="round"
                points={comparePolyline}
              />
            )}

            {/* Primary line */}
            <polyline
              fill="none"
              stroke="var(--clarity-chart-primary)"
              strokeWidth="2.5"
              strokeLinecap="round"
              strokeLinejoin="round"
              points={primaryPolyline}
            />

            {/* Interactive Points and Vertical Guidance */}
            {primaryPoints.map((pt, idx) => {
              const isHovered = hoverIndex === idx;
              return (
                <g key={idx}>
                  {isHovered && (
                    <line
                      x1={pt.x}
                      y1={paddingY}
                      x2={pt.x}
                      y2={paddingY + plotHeight}
                      stroke="var(--clarity-action-primary)"
                      strokeWidth="1.5"
                      strokeDasharray="2 2"
                    />
                  )}

                  {/* Circle on primary */}
                  <circle
                    cx={pt.x}
                    cy={pt.y}
                    r={isHovered ? 6 : 3.5}
                    fill="var(--clarity-surface)"
                    stroke="var(--clarity-chart-primary)"
                    strokeWidth={isHovered ? 3 : 2}
                  />

                  {/* Circle on compare */}
                  {showComparison && comparePoints[idx] && (
                    <circle
                      cx={comparePoints[idx]!.x}
                      cy={comparePoints[idx]!.y}
                      r={isHovered ? 4.5 : 2.5}
                      fill="var(--clarity-surface)"
                      stroke="var(--clarity-chart-compare)"
                      strokeWidth="2"
                    />
                  )}

                  {/* Invisible broad hitbox for touch/mouse */}
                  <rect
                    x={pt.x - plotWidth / data.length / 2}
                    y={paddingY}
                    width={plotWidth / data.length}
                    height={plotHeight}
                    fill="transparent"
                    style={{ cursor: "pointer" }}
                    onMouseEnter={() => setHoverIndex(idx)}
                    onFocus={() => setHoverIndex(idx)}
                    tabIndex={0}
                    aria-label={data[idx] ? `${data[idx]!.label}: ${formatVnd(data[idx]!.primary)}` : undefined}
                  />
                </g>
              );
            })}
          </svg>

          {/* Floating Tooltip */}
          {currentHoverItem && currentHoverCoord && (
            <div
              className={styles.chartTooltip}
              style={{
                left: `${(currentHoverCoord.x / svgWidth) * 100}%`,
                top: `${(currentHoverCoord.y / svgHeight) * 100}%`,
              }}
            >
              <div style={{ fontWeight: 600, marginBottom: "4px" }}>
                {currentHoverItem.label} ({currentHoverItem.date})
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: "6px", color: "var(--clarity-chart-primary)" }}>
                <span style={{ width: 8, height: 8, borderRadius: "50%", background: "var(--clarity-chart-primary)" }} />
                <span>Kỳ này: <strong>{formatVnd(currentHoverItem.primary)}</strong></span>
              </div>
              {showComparison && (
                <div style={{ display: "flex", alignItems: "center", gap: "6px", color: "var(--clarity-text-secondary)", marginTop: "2px" }}>
                  <span style={{ width: 8, height: 8, borderRadius: "50%", background: "var(--clarity-chart-compare)" }} />
                  <span>Kỳ trước: <strong>{formatVnd(currentHoverItem.compare)}</strong></span>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
