"use client";

export function CheckoutChangeDialog({ onPreviewAgain }: { onPreviewAgain(): void }) {
  return <section role="alertdialog" aria-label="Đơn hàng đã thay đổi" className="checkout-changed">
    <h2>Đơn hàng đã thay đổi</h2>
    <p>Giá, tồn kho hoặc điều kiện giao hàng vừa thay đổi. Hãy xem lại bản xem trước trước khi xác nhận lại.</p>
    <button type="button" onClick={onPreviewAgain}>Làm mới xem trước</button>
  </section>;
}
