import Link from "next/link";
import { requireUser } from "@/lib/auth/require-user";

export default async function MePage() {
  const user = await requireUser();
  return <main>
    <h1>Tài khoản</h1>
    <p>{user.displayName}</p>
    <p>{user.email}</p>
    <p>Trạng thái: {user.accountStatus}</p>
    <nav>
      <Link href="/me/addresses">Địa chỉ đã lưu</Link>
      <Link href="/me/security/sessions">Bảo mật và phiên đăng nhập</Link>
    </nav>
  </main>;
}
