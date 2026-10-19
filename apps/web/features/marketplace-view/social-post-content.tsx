"use client";

import Image from "next/image";
import Link from "next/link";
import { useId, useRef, useState } from "react";
import { CaretLeft, CaretRight, ImageBroken, ImageSquare } from "@phosphor-icons/react";
import type { components } from "@covie/api-client";
import styles from "./marketplace-view.module.css";
import { SocialVideo } from "./social-video";

type Media = components["schemas"]["SocialPreviewMedia"];

function Caption({ text }: { text: string }) {
  const [expanded, setExpanded] = useState(false);
  const id = useId();
  const long = text.length > 240 || text.split("\n").length > 4;
  return <div className={styles.postCaption}>
    <p id={id} className={long && !expanded ? styles.captionClamped : undefined}>{text}</p>
    {long && <button type="button" aria-controls={id} aria-expanded={expanded}
      onClick={() => setExpanded((value) => !value)}>
      {expanded ? "Thu gọn nội dung" : "Xem thêm nội dung"}
    </button>}
  </div>;
}

function Gallery({ media, name, href, kind }: { media: Media[]; name: string; href?: string; kind?: string }) {
  const [index, setIndex] = useState(0);
  const [failed, setFailed] = useState<Set<number>>(() => new Set());
  const start = useRef<{ x: number; y: number } | null>(null);
  const swipedAt = useRef(0);
  const current = media[index];
  const video = current?.mimeType?.startsWith("video/") === true;
  const mixed = media.some(entry => entry.mimeType?.startsWith("video/"));
  const label = mixed ? "Nội dung" : "Ảnh";
  const multiple = media.length > 1;
  const move = (direction: number) => setIndex((value) => Math.max(0, Math.min(media.length - 1, value + direction)));
  const content = <>
    {video && current?.url ? <SocialVideo key={current.id ?? current.url} url={current.url} name={name} /> : current?.url && !failed.has(index) ? <Image src={current.url} alt={`${name} — Ảnh ${index + 1}/${media.length}`}
      fill sizes="(min-width: 1024px) 480px, 100vw" unoptimized
      onError={() => setFailed((previous) => new Set(previous).add(index))} />
      : <span className={styles.mediaFallback}>
        {current ? <ImageBroken size={32} aria-hidden="true" /> : <ImageSquare size={32} aria-hidden="true" />}
        <span>{current ? "Không tải được ảnh" : "Chưa có ảnh"}</span>
      </span>}
    {kind && <span className={styles.kind}>{kind}</span>}
  </>;
  return <section className={styles.postGallery} role="region" aria-label={`${mixed ? "Ảnh và video" : "Ảnh"} ${name}`}
    tabIndex={multiple ? 0 : undefined}
    onKeyDown={(event) => {
      if (!multiple || event.target !== event.currentTarget || !["ArrowLeft", "ArrowRight"].includes(event.key)) return;
      event.preventDefault(); move(event.key === "ArrowRight" ? 1 : -1);
    }}
    onTouchStart={(event) => {
      if (video && (event.target as HTMLElement).closest("video,button")) { start.current = null; return; }
      const touch = event.touches[0];
      start.current = touch ? { x: touch.clientX, y: touch.clientY } : null;
    }}
    onTouchCancel={() => { start.current = null; }}
    onTouchEnd={(event) => {
      const touch = event.changedTouches[0]; const origin = start.current;
      start.current = null;
      if (!multiple || !touch || !origin) return;
      const horizontal = touch.clientX - origin.x;
      if (Math.abs(horizontal) < 40 || Math.abs(horizontal) < Math.abs(touch.clientY - origin.y) * 1.5) return;
      move(horizontal < 0 ? 1 : -1); swipedAt.current = Date.now();
    }}>
    {href && !video ? <Link className={styles.media} href={href} aria-label={`Xem ${name}`}
      onClick={(event) => { if (Date.now() - swipedAt.current < 500) event.preventDefault(); }}>{content}</Link>
      : <div className={styles.media}>{content}</div>}
    {multiple && <div className={styles.galleryControls}>
      <button type="button" aria-label={`${label} trước`} disabled={index === 0} onClick={() => move(-1)}>
        <CaretLeft size={20} aria-hidden="true" />
      </button>
      <span aria-live="polite">{label} {index + 1}/{media.length}</span>
      <button type="button" aria-label={`${label} tiếp theo`} disabled={index === media.length - 1} onClick={() => move(1)}>
        <CaretRight size={20} aria-hidden="true" />
      </button>
    </div>}
  </section>;
}

export function SocialPostContent({ caption, media, name, href, kind }: {
  caption?: string; media: Media[]; name: string; href?: string; kind?: string;
}) {
  const videoEnabled = process.env.NEXT_PUBLIC_COVIE_SOCIAL_V2_VIDEO_ENABLED === "true";
  const gallery = videoEnabled ? media : media.filter(entry => !entry.mimeType?.startsWith("video/"));
  return <>
    {caption && <Caption key={caption} text={caption} />}
    <Gallery key={JSON.stringify(gallery)} media={gallery} name={name} href={href} kind={kind} />
  </>;
}
