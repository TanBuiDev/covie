"use client";

import { useEffect, useState } from "react";
import { requestBrowserApi } from "@/lib/api/api-client";
import type { components } from "@covie/api-client";

type SellerOnboarding = components["schemas"]["Onboarding"];

const money = (value: number) => new Intl.NumberFormat("vi-VN").format(value) + "đ";

export function SellerBillingOnboarding({ onRegistrationReady }: { onRegistrationReady?: (ready: boolean) => void }) {
  const [policy, setPolicy] = useState<SellerOnboarding>();
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const abort = new AbortController();
    // API may be unavailable in local development: never invent a fee or a trial.
    requestBrowserApi("GET", "/api/v1/billing/onboarding")
      .then(async response => {
        if (!response.ok) throw new Error("billing unavailable");
        const data = await response.json() as SellerOnboarding;
        if (!abort.signal.aborted) {
          setPolicy(data);
          onRegistrationReady?.(data.registrationEnabled === true);
        }
      })
      .catch(() => { if (!abort.signal.aborted) setFailed(true); });
    return () => abort.abort();
  }, [attempt, onRegistrationReady]);

  return (
    <section className="admin-surface" aria-label="Phí thuê bao Seller" style={{ marginBottom: 16 }}>
      <h3>Phí mở bán và chính sách cửa hàng</h3>
      {onRegistrationReady ? <ol aria-label="Các bước đăng ký Seller">
        <li aria-current="step">Tạo hồ sơ và xem trước cửa hàng</li>
        <li>Đọc giá, chu kỳ và điều kiện thuê bao</li>
        <li>Thanh toán QR/link nếu chính sách yêu cầu</li>
        <li>Kiểm tra kết quả xác minh từ máy chủ</li>
      </ol> : null}
      {!policy ? <p>{failed
        ? "Chưa tải được chính sách thu phí hiện hành. Vui lòng kiểm tra lại trước khi đăng ký."
        : "Đang lấy thông tin phí và chính sách từ máy chủ…"}</p> : (
        <>
          <p>Đăng ký tài khoản CoVie và mua hàng hoàn toàn miễn phí. Chỉ cửa hàng kinh doanh sản phẩm/dịch vụ mới thuộc chính sách Seller Billing.</p>
          {policy.enforcementEnabled ? (
            <>
              {policy.onboardingMode === "TRIAL" && (policy.trialDays ?? 0) > 0 ?
                <p role="status">Chính sách hiện hành: dùng thử {policy.trialDays} ngày cho chủ cửa hàng đủ điều kiện.</p> :
                <p role="status">Chính sách hiện hành: thanh toán thuê bao trước khi mở bán.</p>}
              <p>Thời gian ân hạn (nếu áp dụng): {policy.graceDays} ngày. Quyền đăng bán được xác minh ở backend.</p>
            </>
          ) : <p role="status">CoVie chưa kích hoạt yêu cầu thu phí Seller. Việc tạo cửa hàng chưa phát sinh thanh toán.</p>}
          {policy.plans?.length ? (
            <ul>{policy.plans.map(plan => (
              <li key={plan.code}><strong>{plan.name}</strong>: {typeof plan.priceVnd === "number" ? money(plan.priceVnd) : "Chưa có giá"} / {plan.cycleMonths} tháng</li>
            ))}</ul>
          ) : <p>Hiện chưa có gói thuê bao được mở bán.</p>}
          {!policy.registrationEnabled ?
            <p role="alert">Hệ thống đang tạm dừng tiếp nhận cửa hàng mới. Các tài khoản mua hàng không bị ảnh hưởng.</p> : null}
          <small>Giá niêm yết và điều kiện thực tế được xác nhận lại khi tạo hóa đơn; Admin có thể công bố phiên bản giá mới.</small>
          <p>Thanh toán từng lần qua QR/link payOS; không tự động trừ tiền gia hạn. Chỉ kết quả xác minh từ máy chủ mới xác nhận thanh toán và quyền mở bán.</p>
        </>
      )}
      {failed ? <button type="button" onClick={() => { setFailed(false); setAttempt(current => current + 1); }}>Tải lại chính sách thuê bao</button> : null}
    </section>
  );
}
