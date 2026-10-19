"use client";

import { useId, useRef, useState } from "react";
import { ChatCircle } from "@phosphor-icons/react";
import { SocialComments } from "@/features/marketplace-view/social-comments";

export function SellerSocialComments({ itemId }: { itemId: string }) {
  const [open, setOpen] = useState(false);
  const trigger = useRef<HTMLButtonElement>(null);
  const panelId = useId();
  return <section className="seller-catalog-editor__panel seller-catalog-editor__feedback" aria-label="Phản hồi về tin đăng">
    <div className="seller-catalog-editor__panel-title">
      <ChatCircle size={20} weight="duotone" aria-hidden="true" />
      <h2>Phản hồi về tin đăng</h2>
    </div>
    <p>Bình luận được tải theo quyền hiển thị hiện tại của tin đăng. Tin chưa công khai hoặc không khả dụng có thể không tải được bình luận.</p>
    <button ref={trigger} type="button" className="seller-button seller-button--secondary"
      aria-expanded={open} aria-controls={panelId} onClick={() => setOpen(true)}>Xem bình luận</button>
    <div id={panelId}>
      {open && <SocialComments key={itemId} itemId={itemId} onClose={() => {
        setOpen(false);
        trigger.current?.focus();
      }} />}
    </div>
  </section>;
}
