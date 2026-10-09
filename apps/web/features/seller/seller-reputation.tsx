import type { components } from "@covie/api-client";
import { EmptyState } from "@/components/ui/empty-state";
import {
  ArrowsClockwise,
  CheckCircle,
  Clock,
  LockKey,
  Package,
  ShieldCheck,
} from "@phosphor-icons/react";

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
        description="Chỉ số uy tín tổng hợp sẽ xuất hiện khi có dữ liệu có thể hiển thị."
      />
    );
  }

  const completionLabel = `${completionRate.toLocaleString("vi-VN", { maximumFractionDigits: 2 })}%`;

  return (
    <section className="seller-trust-stack" aria-labelledby="seller-reputation-title">
      <article className="seller-reputation-summary">
        <div className="seller-trust-badge" style={{ display: "inline-flex", alignItems: "center", gap: "6px", background: "rgba(4, 120, 87, 0.08)", color: "#047857", padding: "4px 10px", borderRadius: "9999px", fontSize: "12px", fontWeight: "600", width: "fit-content" }}>
          <ShieldCheck size={16} weight="fill" />
          <span>Hồ sơ tín nhiệm CoVie</span>
        </div>
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
        <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
          <LockKey size={18} weight="duotone" style={{ color: "#047857" }} />
          <h2 style={{ margin: 0 }}>Tóm tắt uy tín</h2>
        </div>
        <p>Chỉ hiển thị các chỉ số uy tín tổng hợp được hệ thống cho phép công khai.</p>
        <ul className="seller-trust-pill-list" style={{ listStyle: "none", padding: 0, margin: "8px 0 0", display: "grid", gap: "8px" }}>
          <li style={{ display: "flex", alignItems: "center", gap: "8px", fontSize: "13px", color: "var(--seller-text-secondary, #64748b)" }}>
            <CheckCircle size={16} weight="fill" style={{ color: "#047857", flexShrink: 0 }} />
            <span>Tỷ lệ hoàn tất tính toán dựa trên toàn bộ đơn hàng hợp lệ hoàn tất thành công.</span>
          </li>
          <li style={{ display: "flex", alignItems: "center", gap: "8px", fontSize: "13px", color: "var(--seller-text-secondary, #64748b)" }}>
            <LockKey size={16} weight="fill" style={{ color: "#047857", flexShrink: 0 }} />
            <span>Dữ liệu cá nhân và chi tiết riêng tư của người mua luôn được ẩn danh bảo vệ.</span>
          </li>
        </ul>
      </article>

      <article className="seller-trust-note">
        <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
          <ShieldCheck size={18} weight="duotone" style={{ color: "#047857" }} />
          <h2 style={{ margin: 0 }}>Cải thiện trải nghiệm</h2>
        </div>
        <p>Xác nhận đơn đúng lúc, cập nhật trạng thái khi cần và cung cấp thông tin nội dung đăng bán chính xác.</p>
        <div className="seller-trust-guide-grid" style={{ display: "grid", gap: "10px", marginTop: "10px" }}>
          <div style={{ display: "flex", alignItems: "flex-start", gap: "10px", background: "#ffffff", padding: "10px 12px", borderRadius: "8px", border: "1px solid var(--seller-border, #e2e8f0)" }}>
            <div style={{ width: "28px", height: "28px", borderRadius: "6px", background: "rgba(234, 88, 12, 0.1)", color: "#ea580c", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
              <Clock size={16} weight="bold" />
            </div>
            <div>
              <strong style={{ display: "block", fontSize: "13px", color: "var(--seller-text-primary, #0f172a)" }}>Xác nhận nhanh</strong>
              <small style={{ color: "var(--seller-text-secondary, #64748b)", fontSize: "12px" }}>Tiếp nhận và phản hồi đơn ngay khi nhận được thông báo để giảm tỷ lệ hủy.</small>
            </div>
          </div>
          <div style={{ display: "flex", alignItems: "flex-start", gap: "10px", background: "#ffffff", padding: "10px 12px", borderRadius: "8px", border: "1px solid var(--seller-border, #e2e8f0)" }}>
            <div style={{ width: "28px", height: "28px", borderRadius: "6px", background: "rgba(37, 99, 235, 0.08)", color: "#2563eb", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
              <ArrowsClockwise size={16} weight="bold" />
            </div>
            <div>
              <strong style={{ display: "block", fontSize: "13px", color: "var(--seller-text-primary, #0f172a)" }}>Cập nhật tiến trình</strong>
              <small style={{ color: "var(--seller-text-secondary, #64748b)", fontSize: "12px" }}>Chuyển trạng thái sang đang giao hoặc sẵn sàng lấy để cư dân an tâm theo dõi.</small>
            </div>
          </div>
          <div style={{ display: "flex", alignItems: "flex-start", gap: "10px", background: "#ffffff", padding: "10px 12px", borderRadius: "8px", border: "1px solid var(--seller-border, #e2e8f0)" }}>
            <div style={{ width: "28px", height: "28px", borderRadius: "6px", background: "rgba(4, 120, 87, 0.08)", color: "#047857", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
              <Package size={16} weight="bold" />
            </div>
            <div>
              <strong style={{ display: "block", fontSize: "13px", color: "var(--seller-text-primary, #0f172a)" }}>Tồn kho chính xác</strong>
              <small style={{ color: "var(--seller-text-secondary, #64748b)", fontSize: "12px" }}>Duy trì số lượng và giá bán đúng thực tế để tránh trường hợp thiếu hàng.</small>
            </div>
          </div>
        </div>
      </article>
    </section>
  );
}
