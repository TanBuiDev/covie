import Link from "next/link";
import { AdminPageHeader, AdminStatusChip, AdminSurface } from "@/features/admin/admin-ui";
import {
  ArrowRight,
  CheckCircle,
  Flag,
  GridFour,
  Prohibit,
  ShieldCheck,
  ShieldWarning,
} from "@phosphor-icons/react/dist/ssr";

export default function ListingsPage() {
  return (
    <div className="admin-page-stack">
      <AdminPageHeader
        title="Nội dung đăng bán"
        subtitle="Kiểm duyệt nội dung & tiêu chuẩn đăng bán nội khu"
      />

      <AdminSurface className="admin-capability-state">
        <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
          <AdminStatusChip label="Kiểm duyệt qua Moderation Case API" tone="neutral" />
          <AdminStatusChip label="Chỉ đọc theo contract" tone="info" />
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: "10px", marginTop: "4px" }}>
          <ShieldWarning size={24} weight="duotone" style={{ color: "#e11d48" }} />
          <h2 style={{ margin: 0 }}>Nguyên tắc kiểm duyệt tin đăng CoVie</h2>
        </div>
        <p>
          Repository hiện có moderation case API nhưng chưa có Admin listing-read contract độc lập. Không dựng listing detail từ dữ liệu mẫu; các báo cáo thực tế được xử lý tại hàng đợi Báo cáo.
        </p>
        <div className="admin-governance-pills" style={{ marginTop: "8px" }}>
          <span className="admin-governance-pill">
            <CheckCircle size={14} weight="fill" style={{ color: "#047857" }} />
            <span>Xử lý qua /api/v1/admin/moderation-cases</span>
          </span>
          <span className="admin-governance-pill">
            <CheckCircle size={14} weight="fill" style={{ color: "#047857" }} />
            <span>Không dựng mock listing detail</span>
          </span>
          <span className="admin-governance-pill">
            <CheckCircle size={14} weight="fill" style={{ color: "#047857" }} />
            <span>Kiểm duyệt có kiểm toán Audit Trail</span>
          </span>
        </div>
      </AdminSurface>

      <div className="admin-dashboard-section">
        <h2 className="admin-section-title">Kênh điều phối kiểm duyệt trực tiếp</h2>
        <div className="admin-launchpad-grid">
          <Link href="/admin/reports" className="admin-launchpad-card">
            <div className="admin-launchpad-card__icon" style={{ color: "#e11d48", background: "rgba(225, 29, 72, 0.08)" }}>
              <Flag size={24} weight="duotone" />
            </div>
            <div className="admin-launchpad-card__info">
              <strong>Hàng đợi Báo cáo vi phạm</strong>
              <p>Tiếp nhận, xử lý ẩn hoặc duyệt tin đăng theo báo cáo từ cư dân.</p>
            </div>
            <ArrowRight size={16} weight="bold" className="admin-launchpad-card__arrow" />
          </Link>

          <Link href="/admin/categories" className="admin-launchpad-card">
            <div className="admin-launchpad-card__icon" style={{ color: "#047857", background: "rgba(4, 120, 87, 0.08)" }}>
              <GridFour size={24} weight="duotone" />
            </div>
            <div className="admin-launchpad-card__info">
              <strong>Danh mục sản phẩm</strong>
              <p>Quản lý các ngành hàng và cấu trúc danh mục cho phép đăng tải.</p>
            </div>
            <ArrowRight size={16} weight="bold" className="admin-launchpad-card__arrow" />
          </Link>

          <Link href="/admin/settings" className="admin-launchpad-card">
            <div className="admin-launchpad-card__icon" style={{ color: "#2563eb", background: "rgba(37, 99, 235, 0.08)" }}>
              <ShieldCheck size={24} weight="duotone" />
            </div>
            <div className="admin-launchpad-card__info">
              <strong>Quy chuẩn & Từ khóa cấm</strong>
              <p>Thiết lập tham số phát hiện từ khóa nhạy cảm và quy tắc an toàn.</p>
            </div>
            <ArrowRight size={16} weight="bold" className="admin-launchpad-card__arrow" />
          </Link>
        </div>
      </div>

      <AdminSurface>
        <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "8px" }}>
          <ShieldCheck size={20} weight="duotone" style={{ color: "#047857" }} />
          <h2 style={{ margin: 0 }}>4 Trụ cột tiêu chuẩn nội dung đăng bán CoVie</h2>
        </div>
        <p>Mọi nội dung hàng hóa và dịch vụ được trao đổi trên CoVie bắt buộc tuân thủ 4 quy chuẩn văn minh cư dân:</p>

        <div style={{ display: "grid", gap: "10px", marginTop: "12px", gridTemplateColumns: "repeat(auto-fit, minmax(260px, 1fr))" }}>
          <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: "8px", padding: "12px 14px" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "8px", color: "#047857", fontWeight: "600", fontSize: "13px" }}>
              <CheckCircle size={18} weight="fill" />
              <span>An toàn & Nguồn gốc thực phẩm</span>
            </div>
            <small style={{ color: "#64748b", display: "block", marginTop: "4px", fontSize: "12px" }}>
              Nêu rõ hạn sử dụng, cách thức chế biến và chứng nhận an toàn vệ sinh thực phẩm nội khu.
            </small>
          </div>

          <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: "8px", padding: "12px 14px" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "8px", color: "#2563eb", fontWeight: "600", fontSize: "13px" }}>
              <CheckCircle size={18} weight="fill" />
              <span>Giá cả & Tồn kho minh bạch</span>
            </div>
            <small style={{ color: "#64748b", display: "block", marginTop: "4px", fontSize: "12px" }}>
              Niêm yết đúng giá bán VND, không phát sinh phụ phí ẩn và cập nhật số lượng có sẵn chuẩn xác.
            </small>
          </div>

          <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: "8px", padding: "12px 14px" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "8px", color: "#dc2626", fontWeight: "600", fontSize: "13px" }}>
              <Prohibit size={18} weight="bold" />
              <span>Nghiêm cấm hàng quốc cấm & độc hại</span>
            </div>
            <small style={{ color: "#64748b", display: "block", marginTop: "4px", fontSize: "12px" }}>
              Tuyệt đối không đăng bán chất kích thích, vũ khí, hàng lậu hoặc động vật hoang dã.
            </small>
          </div>

          <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: "8px", padding: "12px 14px" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "8px", color: "#047857", fontWeight: "600", fontSize: "13px" }}>
              <CheckCircle size={18} weight="fill" />
              <span>Giao nhận văn minh nội khu</span>
            </div>
            <small style={{ color: "#64748b", display: "block", marginTop: "4px", fontSize: "12px" }}>
              Giao hàng đúng sảnh/căn hộ thỏa thuận, tuân thủ giờ giấc yên tĩnh theo nội quy tòa nhà.
            </small>
          </div>
        </div>
      </AdminSurface>
    </div>
  );
}
