import Link from "next/link";
import { requireUser } from "@/lib/auth/require-user";
import { ArrowLeft, ShieldCheck } from "@phosphor-icons/react/dist/ssr";

/**
 * payOS return/cancel is UX only, never proof of settled payment.
 * The seller billing dashboard must poll authenticated invoice status from backend.
 */
export default async function BillingReturnPage() {
  await requireUser();
  return (
    <main className="seller-entry seller-stack">
      <section className="seller-onboarding-section">
        <div className="seller-onboarding-hero">
          <ShieldCheck size={32} weight="duotone" aria-hidden="true" />
          <div>
            <h1>Đang xác minh thanh toán</h1>
            <p>CoVie sẽ chỉ kích hoạt quyền mở bán sau khi máy chủ xác nhận giao dịch từ payOS.
              Việc trở lại trang này không có nghĩa thanh toán đã thành công.</p>
          </div>
        </div>
        <Link href="/seller"><ArrowLeft size={18} aria-hidden="true" /> Trở về Seller Center</Link>
      </section>
    </main>
  );
}
