import { NeedList } from "@/features/needs/need-list";

export default function NeedsPage() {
  return (
    <main className="needs-surface">
      <header>
        <h1>Nhu cầu của tôi</h1>
        <p>Đăng điều bạn cần và theo dõi phản hồi từ người bán.</p>
      </header>
      <NeedList />
    </main>
  );
}
