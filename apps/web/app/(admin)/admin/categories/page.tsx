import { AdminReadOnlyCollection } from "@/features/admin/admin-readonly-collection";

export default function CategoriesPage() {
  return (
    <AdminReadOnlyCollection
      title="Danh mục"
      subtitle="Cấu trúc danh mục hàng hóa & dịch vụ nội khu"
      path="/api/v1/categories"
      note="Contract hiện tại chỉ xác nhận endpoint đọc categories. Không có CRUD Admin được chứng minh trong OpenAPI nên giao diện cố ý không hiển thị tạo/sửa/xoá."
      columns={[
        { key: "name", label: "Tên danh mục" },
        { key: "slug", label: "Slug" },
        { key: "catalogType", label: "Loại catalog" },
        { key: "parentId", label: "Danh mục cha" },
        { key: "sortOrder", label: "Thứ tự" },
      ]}
    />
  );
}
