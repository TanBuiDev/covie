import Link from "next/link";
import { AdminPageHeader, AdminStatusChip, AdminSurface } from "@/features/admin/admin-ui";
import {
  ArrowRight,
  CheckCircle,
  Flag,
  GearSix,
  HandHeart,
  LockKey,
  Prohibit,
  ShieldCheck,
  Users,
} from "@phosphor-icons/react/dist/ssr";

export default function NeedsPage() {
  return (
    <div className="admin-page-stack">
      <AdminPageHeader
        title="Cần mua & Nhu cầu cư dân"
        subtitle="Quản trị vận hành & ranh giới dữ liệu nhu cầu nội khu"
      />

      <AdminSurface className="admin-capability-state">
        <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
          <AdminStatusChip label="Ranh giới kiến trúc" tone="neutral" />
          <AdminStatusChip label="Chỉ đọc theo contract" tone="info" />
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: "10px", marginTop: "4px" }}>
          <HandHeart size={24} weight="duotone" style={{ color: "#047857" }} />
          <h2 style={{ margin: 0 }}>Nguyên tắc dữ liệu nhu cầu cư dân</h2>
        </div>
        <p>
          OpenAPI hiện tại có flow Cần mua cho user/merchant nhưng chưa có endpoint đọc hoặc moderation dành riêng cho Admin. Không dùng private participant API làm nguồn dữ liệu quản trị.
        </p>
        <div className="admin-governance-pills" style={{ marginTop: "8px" }}>
          <span className="admin-governance-pill">
            <CheckCircle size={14} weight="fill" style={{ color: "#047857" }} />
            <span>Không bypass private participant API</span>
          </span>
          <span className="admin-governance-pill">
            <CheckCircle size={14} weight="fill" style={{ color: "#047857" }} />
            <span>Bảo vệ quyền riêng tư nhu cầu cá nhân</span>
          </span>
          <span className="admin-governance-pill">
            <CheckCircle size={14} weight="fill" style={{ color: "#047857" }} />
            <span>OpenAPI Specification v1.0 Compliant</span>
          </span>
        </div>
      </AdminSurface>

      <div className="admin-dashboard-section">
        <h2 className="admin-section-title">Kênh điều phối nghiệp vụ liên quan</h2>
        <div className="admin-launchpad-grid">
          <Link href="/admin/reports" className="admin-launchpad-card">
            <div className="admin-launchpad-card__icon" style={{ color: "#e11d48", background: "rgba(225, 29, 72, 0.08)" }}>
              <Flag size={24} weight="duotone" />
            </div>
            <div className="admin-launchpad-card__info">
              <strong>Hàng đợi Báo cáo vi phạm</strong>
              <p>Tiếp nhận và xử lý báo cáo về tin cần mua không phù hợp hoặc vi phạm.</p>
            </div>
            <ArrowRight size={16} weight="bold" className="admin-launchpad-card__arrow" />
          </Link>

          <Link href="/admin/users" className="admin-launchpad-card">
            <div className="admin-launchpad-card__icon" style={{ color: "#047857", background: "rgba(4, 120, 87, 0.08)" }}>
              <Users size={24} weight="duotone" />
            </div>
            <div className="admin-launchpad-card__info">
              <strong>Quản lý người dùng</strong>
              <p>Kiểm tra tài khoản cư dân tạo yêu cầu và xác thực tính hợp lệ của người tạo.</p>
            </div>
            <ArrowRight size={16} weight="bold" className="admin-launchpad-card__arrow" />
          </Link>

          <Link href="/admin/settings" className="admin-launchpad-card">
            <div className="admin-launchpad-card__icon" style={{ color: "#2563eb", background: "rgba(37, 99, 235, 0.08)" }}>
              <GearSix size={24} weight="duotone" />
            </div>
            <div className="admin-launchpad-card__info">
              <strong>Cài đặt hệ thống</strong>
              <p>Thiết lập tham số chính sách và từ khóa nhạy cảm áp dụng cho tin cần mua.</p>
            </div>
            <ArrowRight size={16} weight="bold" className="admin-launchpad-card__arrow" />
          </Link>
        </div>
      </div>

      <AdminSurface>
        <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "8px" }}>
          <ShieldCheck size={20} weight="duotone" style={{ color: "#047857" }} />
          <h2 style={{ margin: 0 }}>4 Trụ cột quản trị nhu cầu cư dân CoVie</h2>
        </div>
        <p>Mục Cần mua hỗ trợ cư dân tìm kiếm trợ giúp nội khu, tuân theo các quy tắc an toàn:</p>

        <div style={{ display: "grid", gap: "10px", marginTop: "12px", gridTemplateColumns: "repeat(auto-fit, minmax(260px, 1fr))" }}>
          <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: "8px", padding: "12px 14px" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "8px", color: "#047857", fontWeight: "600", fontSize: "13px" }}>
              <CheckCircle size={18} weight="fill" />
              <span>Tương thân tương ái & Phi thương mại</span>
            </div>
            <small style={{ color: "#64748b", display: "block", marginTop: "4px", fontSize: "12px" }}>
              Hỗ trợ cư dân chia sẻ và tìm kiếm vật dụng cần gấp, không sử dụng làm kênh quảng cáo spam.
            </small>
          </div>

          <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: "8px", padding: "12px 14px" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "8px", color: "#2563eb", fontWeight: "600", fontSize: "13px" }}>
              <LockKey size={18} weight="fill" />
              <span>Bảo vệ quyền riêng tư liên lạc</span>
            </div>
            <small style={{ color: "#64748b", display: "block", marginTop: "4px", fontSize: "12px" }}>
              Không công khai số điện thoại, mật khẩu căn hộ hoặc thông tin nhạy cảm trên bài đăng.
            </small>
          </div>

          <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: "8px", padding: "12px 14px" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "8px", color: "#dc2626", fontWeight: "600", fontSize: "13px" }}>
              <Prohibit size={18} weight="bold" />
              <span>Nghiêm cấm yêu cầu hàng hóa cấm</span>
            </div>
            <small style={{ color: "#64748b", display: "block", marginTop: "4px", fontSize: "12px" }}>
              Tuyệt đối không yêu cầu chất kích thích, thuốc kê đơn, hóa chất nguy hiểm hoặc vật phẩm bất hợp pháp.
            </small>
          </div>

          <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: "8px", padding: "12px 14px" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "8px", color: "#047857", fontWeight: "600", fontSize: "13px" }}>
              <CheckCircle size={18} weight="fill" />
              <span>Ưu tiên đối tác nội khu xác thực</span>
            </div>
            <small style={{ color: "#64748b", display: "block", marginTop: "4px", fontSize: "12px" }}>
              Khuyến khích các gian hàng và người bán đã được CoVie phê duyệt tiếp nhận và phản hồi nhu cầu.
            </small>
          </div>
        </div>
      </AdminSurface>
    </div>
  );
}
