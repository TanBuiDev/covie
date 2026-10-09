import Link from "next/link";
import { AdminPageHeader, AdminStatusChip, AdminSurface } from "@/features/admin/admin-ui";
import {
  ArrowRight,
  CheckCircle,
  GearSix,
  LockKey,
  MapPin,
  ShieldCheck,
  Storefront,
} from "@phosphor-icons/react/dist/ssr";

export default function ShopsPage() {
  return (
    <div className="admin-page-stack">
      <AdminPageHeader
        title="Cửa hàng & Điểm bán"
        subtitle="Quản trị vận hành & ranh giới dữ liệu gian hàng"
      />

      <AdminSurface className="admin-capability-state">
        <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
          <AdminStatusChip label="Ranh giới kiến trúc" tone="neutral" />
          <AdminStatusChip label="Chỉ đọc theo contract" tone="info" />
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: "10px", marginTop: "4px" }}>
          <LockKey size={24} weight="duotone" style={{ color: "#2563eb" }} />
          <h2 style={{ margin: 0 }}>Nguyên tắc phân quyền & dữ liệu gian hàng</h2>
        </div>
        <p>
          OpenAPI hiện tại chưa cung cấp endpoint đọc cửa hàng/điểm bán dành riêng cho Admin. Màn hình không tái sử dụng endpoint Seller/private và không mở mutation không có contract.
        </p>
        <div className="admin-governance-pills" style={{ marginTop: "8px" }}>
          <span className="admin-governance-pill">
            <CheckCircle size={14} weight="fill" style={{ color: "#047857" }} />
            <span>Không bypass private Seller API</span>
          </span>
          <span className="admin-governance-pill">
            <CheckCircle size={14} weight="fill" style={{ color: "#047857" }} />
            <span>Bảo vệ cấu hình điểm bán riêng tư</span>
          </span>
          <span className="admin-governance-pill">
            <CheckCircle size={14} weight="fill" style={{ color: "#047857" }} />
            <span>OpenAPI Specification v1.0 Compliant</span>
          </span>
        </div>
      </AdminSurface>

      <div className="admin-dashboard-section">
        <h2 className="admin-section-title">Kênh điều phối & Cấu hình liên quan</h2>
        <div className="admin-launchpad-grid">
          <Link href="/admin/merchants" className="admin-launchpad-card">
            <div className="admin-launchpad-card__icon" style={{ color: "#2563eb", background: "rgba(37, 99, 235, 0.08)" }}>
              <Storefront size={24} weight="duotone" />
            </div>
            <div className="admin-launchpad-card__info">
              <strong>Người bán & Nhà cung cấp</strong>
              <p>Quản lý tài khoản pháp nhân, duyệt hoặc tạm dừng tư cách đối tác.</p>
            </div>
            <ArrowRight size={16} weight="bold" className="admin-launchpad-card__arrow" />
          </Link>

          <Link href="/admin/zones" className="admin-launchpad-card">
            <div className="admin-launchpad-card__icon" style={{ color: "#047857", background: "rgba(4, 120, 87, 0.08)" }}>
              <MapPin size={24} weight="duotone" />
            </div>
            <div className="admin-launchpad-card__info">
              <strong>Khu vực & Điểm giao nhận</strong>
              <p>Cấu hình phạm vi phục vụ cụm block, tòa nhà và khu dân cư.</p>
            </div>
            <ArrowRight size={16} weight="bold" className="admin-launchpad-card__arrow" />
          </Link>

          <Link href="/admin/settings" className="admin-launchpad-card">
            <div className="admin-launchpad-card__icon" style={{ color: "#475569", background: "rgba(71, 85, 105, 0.08)" }}>
              <GearSix size={24} weight="duotone" />
            </div>
            <div className="admin-launchpad-card__info">
              <strong>Cài đặt hệ thống</strong>
              <p>Tham số toàn cục cho chính sách vận chuyển, kiểm duyệt và giới hạn shop.</p>
            </div>
            <ArrowRight size={16} weight="bold" className="admin-launchpad-card__arrow" />
          </Link>
        </div>
      </div>

      <AdminSurface>
        <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "8px" }}>
          <ShieldCheck size={20} weight="duotone" style={{ color: "#047857" }} />
          <h2 style={{ margin: 0 }}>Chỉ dẫn vận hành cho quản trị viên</h2>
        </div>
        <p>
          Khi phát hiện gian hàng có dấu hiệu vi phạm điều khoản dịch vụ (giao hàng ngoài phạm vi cho phép, sai thông tin liên hệ, tranh chấp giao dịch), quản trị viên sử dụng nghiệp vụ kiểm duyệt tại hàng đợi Báo cáo hoặc đình chỉ tài khoản Người bán đại diện tại mục Quản lý người bán.
        </p>
      </AdminSurface>
    </div>
  );
}
