import Link from "next/link";
import { EmptyState } from "@/components/ui/empty-state";
import { StatusChip } from "@/components/ui/status-chip";
import { CoverageForm } from "@/features/shipping/coverage-form";
import { readApiError } from "@/lib/api/api-error";
import { createServerReadClient } from "@/lib/api/server-api-client";

export default async function DeliveryPage({ params }: { params: Promise<{ merchantId: string; locationId: string }> }) {
  const { merchantId, locationId } = await params;
  const client = await createServerReadClient();
  const { data, response } = await client.GET("/api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages", {
    params: { path: { merchantId, locationId } },
  });
  if (!response.ok || !data) throw await readApiError(response);

  return (
    <section className="seller-stack">
      <p><Link href={`/seller/${encodeURIComponent(merchantId)}/locations/${encodeURIComponent(locationId)}`}>Quay lại điểm bán</Link></p>
      <header className="surface-header">
        <p className="eyebrow">Seller Center / Vùng phục vụ</p>
        <h1>Vùng giao hàng & phục vụ</h1>
        <p>CoVie chỉ lưu các kiểu vùng backend hỗ trợ: bán kính và/hoặc khu vực đã định nghĩa.</p>
      </header>

      <section className="seller-section" aria-labelledby="coverage-list-heading">
        <div className="seller-section__heading"><h2 id="coverage-list-heading">Vùng hiện có</h2></div>
        {data.length ? (
          <div className="seller-row-list">
            {data.map((coverage, index) => (
              <div className="seller-row" key={coverage.id ?? `${coverage.coverageType ?? "coverage"}-${index}`}>
                <div>
                  <strong>{coverage.coverageType === "SERVICE_AREA" ? "Khu vực dịch vụ" : "Giao sản phẩm"}</strong>
                  {coverage.radiusEnabled && coverage.radiusMeters != null ? <small>Bán kính: {coverage.radiusMeters.toLocaleString("vi-VN")} m</small> : null}
                  {coverage.areaEnabled ? <small>Khu vực đã chọn: {coverage.locationAreaIds?.length ?? 0}</small> : null}
                </div>
                <StatusChip status={coverage.status} />
              </div>
            ))}
          </div>
        ) : (
          <EmptyState title="Chưa có vùng phục vụ" description="Thêm vùng mới để backend có thể dùng khi xác định khả năng phục vụ." />
        )}
      </section>

      <CoverageForm merchantId={merchantId} locationId={locationId} />
    </section>
  );
}
