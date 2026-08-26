import { NeedCreateForm } from "@/features/needs/need-create-form";

export default function NewNeedPage() {
  return (
    <main className="needs-surface needs-surface--form">
      <header>
        <h1>Đăng nhu cầu</h1>
        <p>Cho người bán gần bạn biết món đồ hoặc dịch vụ bạn đang tìm.</p>
      </header>
      <NeedCreateForm />
    </main>
  );
}
