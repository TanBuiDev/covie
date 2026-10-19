"use client";

import { SocialPostContent } from "@/features/marketplace-view/social-post-content";
import type { useCatalogGallery } from "./catalog-media-editor";
import styles from "@/features/marketplace-view/marketplace-view.module.css";

export function CatalogSocialPreview({ name, caption, entries }: {
  name: string; caption: string; entries: ReturnType<typeof useCatalogGallery>["entries"];
}) {
  const visible = entries.filter(entry => Boolean(entry.file) || entry.status === "READY").slice(0, 6);
  const cover = visible.findIndex(entry => !entry.contentType?.startsWith("video/"));
  const media = visible.map((entry, index) => ({ id: entry.key, url: entry.url, mimeType: entry.contentType ?? entry.file?.type, sortOrder: index, isCover: index === cover }));
  return <section className="seller-catalog-editor__panel" aria-label="Xem trước bài đăng Social">
    <h2>Xem trước bài đăng Social</h2>
    <p className="seller-contract-note">Bản xem trước cục bộ, không phải xác nhận đã lưu hoặc đã công khai. Hiển thị tối đa 6 media.</p>
    <article className={`${styles.card} ${styles.social}`}>
      <h3 className={styles.author}>{name.trim() || "Tên nội dung"}</h3>
      <SocialPostContent caption={caption.trim()} media={media} name={name.trim() || "Tên nội dung"} />
    </article>
  </section>;
}
