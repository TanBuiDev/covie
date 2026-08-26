"use client";

import type { components } from "@covie/api-client";
import { useMemo, useState } from "react";
import { StatusChip } from "@/components/ui/status-chip";
import { CoverageForm } from "./coverage-form";

type Coverage = components["schemas"]["DeliveryCoverageResponse"];

function coverageStatus(status?: Coverage["status"]) {
  if (status === "ACTIVE") return { label: "Đang hoạt động", tone: "success" as const };
  if (status === "INACTIVE") return { label: "Tạm ngưng", tone: "neutral" as const };
  return { label: "Trạng thái đã được cập nhật", tone: "neutral" as const };
}

export function CoverageSettings({
  merchantId,
  locationId,
  coverages,
}: {
  merchantId: string;
  locationId: string;
  coverages: Coverage[];
}) {
  const [editingId, setEditingId] = useState<string>();
  const editing = useMemo(() => coverages.find((coverage) => coverage.id === editingId), [coverages, editingId]);

  if (coverages.length <= 1) {
    const coverage = coverages[0];
    return (
      <CoverageForm
        key={`${coverage?.id ?? "new"}-${coverage?.version ?? "version"}`}
        merchantId={merchantId}
        locationId={locationId}
        coverage={coverage}
      />
    );
  }

  return (
    <div className="seller-stack">
      <section className="seller-section" aria-labelledby="coverage-list-heading">
        <div className="seller-section__heading"><h2 id="coverage-list-heading">Coverage records</h2></div>
        <p className="seller-contract-note">Backend đang có nhiều coverage record. Chọn đúng record để chỉnh; frontend không tự gộp hoặc suy diễn record ưu tiên.</p>
        <div className="seller-row-list">
          {coverages.map((coverage, index) => {
            const status = coverageStatus(coverage.status);
            return (
              <article className="seller-row" key={coverage.id ?? `${coverage.coverageType ?? "coverage"}-${index}`}>
                <div>
                  <strong>{coverage.coverageType === "SERVICE_AREA" ? "Khu vực dịch vụ" : "Giao sản phẩm"}</strong>
                  {coverage.radiusEnabled && coverage.radiusMeters != null ? <small>Bán kính: {coverage.radiusMeters.toLocaleString("vi-VN")} m</small> : null}
                  {coverage.areaEnabled ? <small>Khu vực đã chọn: {coverage.locationAreaIds?.length ?? 0}</small> : null}
                </div>
                <div className="seller-row__actions">
                  <StatusChip label={status.label} tone={status.tone} />
                  {coverage.id ? <button type="button" onClick={() => setEditingId(coverage.id)}>Sửa</button> : null}
                </div>
              </article>
            );
          })}
        </div>
      </section>

      {editing ? (
        <CoverageForm
          key={`${editing.id ?? "coverage"}-${editing.version ?? "version"}`}
          merchantId={merchantId}
          locationId={locationId}
          coverage={editing}
          onCancelEdit={() => setEditingId(undefined)}
        />
      ) : (
        <details className="seller-technical-fields">
          <summary>Thêm coverage record</summary>
          <CoverageForm merchantId={merchantId} locationId={locationId} />
        </details>
      )}
    </div>
  );
}
