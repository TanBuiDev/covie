import Link from "next/link";
import { AdminPageHeader, AdminStatusChip, AdminSurface } from "@/features/admin/admin-ui";
import {
  ArrowRight,
  CheckCircle,
  Flag,
  GearSix,
  ShieldCheck,
  ShieldWarning,
} from "@phosphor-icons/react/dist/ssr";

export default function ModerationPage() {
  return (
    <div className="admin-page-stack">
      <AdminPageHeader
        title="Kiểm duyệt nội dung"
        subtitle="CoVie Admin · Bảng điều phối thẩm định & hàng đợi xử lý vi phạm"
        action={<AdminStatusChip label="Hệ thống vận hành" tone="success" />}
      />

      <div className="admin-dashboard-section">
        <h2 className="admin-section-title">Phân loại hồ sơ kiểm duyệt</h2>
        <div className="admin-launchpad-grid">
          <Link href="/admin/reports" className="admin-launchpad-card">
            <div className="admin-launchpad-card__icon" style={{ color: "#047857", background: "rgba(4, 120, 87, 0.08)" }}>
              <ShieldCheck size={24} weight="duotone" />
            </div>
            <div className="admin-launchpad-card__info">
              <strong>Kiểm duyệt trước xuất bản</strong>
              <p>Hồ sơ đối tác hoặc hàng hóa yêu cầu xác thực trước khi hiển thị cho cư dân.</p>
            </div>
            <ArrowRight size={16} weight="bold" className="admin-launchpad-card__arrow" />
          </Link>

          <Link href="/admin/reports" className="admin-launchpad-card">
            <div className="admin-launchpad-card__icon" style={{ color: "#e11d48", background: "rgba(225, 29, 72, 0.08)" }}>
              <Flag size={24} weight="duotone" />
            </div>
            <div className="admin-launchpad-card__info">
              <strong>Báo cáo từ cư dân</strong>
              <p>Tiếp nhận khiếu nại về an toàn thực phẩm, sai lệch giá hoặc vi phạm nội quy.</p>
            </div>
            <ArrowRight size={16} weight="bold" className="admin-launchpad-card__arrow" />
          </Link>

          <Link href="/admin/reports" className="admin-launchpad-card">
            <div className="admin-launchpad-card__icon" style={{ color: "#ea580c", background: "rgba(234, 88, 12, 0.08)" }}>
              <ShieldWarning size={24} weight="duotone" />
            </div>
            <div className="admin-launchpad-card__info">
              <strong>Cảnh báo tự động hệ thống</strong>
              <p>Phát hiện từ khóa nhạy cảm, spam hoặc nội dung nghi vấn vi phạm danh mục cấm.</p>
            </div>
            <ArrowRight size={16} weight="bold" className="admin-launchpad-card__arrow" />
          </Link>
        </div>
      </div>

      <div className="admin-dashboard-grid">
        <AdminSurface>
          <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "8px" }}>
            <ShieldCheck size={20} weight="duotone" style={{ color: "#047857" }} />
            <h2 style={{ margin: 0 }}>Quy trình thẩm định 3 bước</h2>
          </div>
          <p>
            Mọi quyết định kiểm duyệt đều được lưu trữ vào Audit Trail kèm mã lý do bắt buộc và chữ ký quản trị viên:
          </p>
          <div style={{ display: "grid", gap: "10px", marginTop: "12px" }}>
            <div style={{ display: "flex", alignItems: "flex-start", gap: "10px", background: "#f8fafc", padding: "10px 12px", borderRadius: "8px", border: "1px solid #e2e8f0" }}>
              <span style={{ background: "#047857", color: "#fff", width: "22px", height: "22px", borderRadius: "50%", display: "flex", alignItems: "center", justifyContent: "center", fontSize: "12px", fontWeight: "700", flexShrink: 0 }}>1</span>
              <div>
                <strong style={{ fontSize: "13px", display: "block" }}>Tiếp nhận hàng đợi</strong>
                <small style={{ color: "#64748b", fontSize: "12px" }}>Nhận hồ sơ mới từ cư dân hoặc bộ lọc hệ thống tại hàng đợi báo cáo mở.</small>
              </div>
            </div>
            <div style={{ display: "flex", alignItems: "flex-start", gap: "10px", background: "#f8fafc", padding: "10px 12px", borderRadius: "8px", border: "1px solid #e2e8f0" }}>
              <span style={{ background: "#047857", color: "#fff", width: "22px", height: "22px", borderRadius: "50%", display: "flex", alignItems: "center", justifyContent: "center", fontSize: "12px", fontWeight: "700", flexShrink: 0 }}>2</span>
              <div>
                <strong style={{ fontSize: "13px", display: "block" }}>Đối soát ngữ cảnh</strong>
                <small style={{ color: "#64748b", fontSize: "12px" }}>Kiểm tra nội dung đăng bán, gian hàng hoặc tin nhắn liên quan theo tiêu chuẩn CoVie.</small>
              </div>
            </div>
            <div style={{ display: "flex", alignItems: "flex-start", gap: "10px", background: "#f8fafc", padding: "10px 12px", borderRadius: "8px", border: "1px solid #e2e8f0" }}>
              <span style={{ background: "#047857", color: "#fff", width: "22px", height: "22px", borderRadius: "50%", display: "flex", alignItems: "center", justifyContent: "center", fontSize: "12px", fontWeight: "700", flexShrink: 0 }}>3</span>
              <div>
                <strong style={{ fontSize: "13px", display: "block" }}>Ban hành quyết định</strong>
                <small style={{ color: "#64748b", fontSize: "12px" }}>Thực hiện Phê duyệt, Ẩn nội dung, Từ chối hoặc Đóng hồ sơ kèm lý do kiểm toán.</small>
              </div>
            </div>
          </div>
        </AdminSurface>

        <AdminSurface>
          <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "8px" }}>
            <GearSix size={20} weight="duotone" style={{ color: "#2563eb" }} />
            <h2 style={{ margin: 0 }}>Hành lang điều phối</h2>
          </div>
          <p>
            Quản trị viên có thể truy cập trực tiếp hàng đợi danh sách vụ việc hoặc cấu hình các tham số phát hiện tự động:
          </p>
          <div style={{ display: "flex", flexDirection: "column", gap: "10px", marginTop: "14px" }}>
            <Link
              href="/admin/reports"
              className="admin-button admin-button--primary"
              style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", gap: "8px" }}
            >
              <Flag size={16} weight="bold" />
              <span>Mở Hàng đợi Báo cáo vi phạm</span>
            </Link>
            <Link
              href="/admin/settings"
              className="admin-button admin-button--secondary"
              style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", gap: "8px" }}
            >
              <GearSix size={16} weight="bold" />
              <span>Cài đặt từ khóa & Chính sách kiểm duyệt</span>
            </Link>
          </div>
          <div className="admin-governance-pills" style={{ marginTop: "16px" }}>
            <span className="admin-governance-pill">
              <CheckCircle size={14} weight="fill" style={{ color: "#047857" }} />
              <span>Audit Trail cho mọi hành động</span>
            </span>
            <span className="admin-governance-pill">
              <CheckCircle size={14} weight="fill" style={{ color: "#047857" }} />
              <span>Chống gửi lặp Idempotency-Key</span>
            </span>
          </div>
        </AdminSurface>
      </div>
    </div>
  );
}
