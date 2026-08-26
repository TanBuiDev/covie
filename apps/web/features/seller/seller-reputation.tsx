import type { components } from "@covie/api-client";
import { EmptyState } from "@/components/ui/empty-state";

type MerchantReputation = components["schemas"]["MerchantReputationSummary"];

function rateFromBasisPoints(value?: number) {
  if (value == null || !Number.isFinite(value)) return undefined;
  return Math.min(100, Math.max(0, value / 100));
}

export function SellerReputation({ reputation }: { reputation?: MerchantReputation }) {
  const completionRate = rateFromBasisPoints(reputation?.completionRateBasisPoints);

  if (completionRate == null) {
    return (
      <EmptyState
        title="Chưa có dữ liệu uy tín"
        description="CoVie chỉ hiển thị chỉ số uy tín tổng hợp khi API hiện tại cho phép công khai."
      />
    );
  }

  const completionLabel = `${completionRate.toLocaleString("vi-VN", { maximumFractionDigits: 2 })}%`;

  return (
    <section className="seller-trust-stack" aria-labelledby="seller-reputation-title">
      <article className="seller-reputation-summary">
        <h2 id="seller-reputation-title">Uy tín giao dịch</h2>
        <div className="seller-reputation-rate">
          <span>Tỷ lệ hoàn thành</span>
          <strong>{completionLabel}</strong>
        </div>
        <div
          className="seller-reputation-progress"
          role="progressbar"
          aria-label="Tỷ lệ hoàn thành"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={completionRate}
        >
          <span style={{ width: `${completionRate}%` }} />
        </div>
        <p>Chỉ hiển thị chỉ số uy tín được hệ thống cho phép công khai.</p>
      </article>

      <article className="seller-trust-note">
        <h2>Tóm tắt uy tín</h2>
        <p>Chỉ hiển thị các chỉ số uy tín tổng hợp được hệ thống cho phép công khai.</p>
      </article>

      <article className="seller-trust-note">
        <h2>Cải thiện trải nghiệm</h2>
        <p>Xác nhận đơn đúng lúc, cập nhật trạng thái khi cần và cung cấp thông tin nội dung đăng bán chính xác.</p>
      </article>
    </section>
  );
}
