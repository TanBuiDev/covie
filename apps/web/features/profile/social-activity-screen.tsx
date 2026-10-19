"use client";

import Image from "next/image";
import Link from "next/link";
import { useState, type KeyboardEvent } from "react";
import { ArrowLeft, BookmarkSimple, ChatCircleText, Heart, ImageSquare } from "@phosphor-icons/react";
import { ErrorState } from "@/components/ui/error-state";
import { formatDateTime } from "@/lib/format/time";
import type { SocialActivityEntry, SocialActivityType } from "./social-activity-api";
import { useSocialActivity } from "./use-social-activity";
import styles from "./social-activity-screen.module.css";

const tabs: { type: SocialActivityType; label: string; empty: string; icon: typeof Heart }[] = [
  { type: "LIKED", label: "Đã thích", empty: "Bạn chưa thích nội dung nào.", icon: Heart },
  { type: "SAVED", label: "Đã lưu", empty: "Bạn chưa lưu nội dung nào.", icon: BookmarkSimple },
  { type: "COMMENTED", label: "Bình luận", empty: "Bạn chưa có bình luận nào đang hiển thị.", icon: ChatCircleText },
];

function moveTabFocus(event: KeyboardEvent<HTMLAnchorElement>) {
  const controls = Array.from(event.currentTarget.parentElement?.querySelectorAll<HTMLAnchorElement>('[role="tab"]') ?? []);
  const index = controls.indexOf(event.currentTarget);
  const next = event.key === "ArrowRight" ? (index + 1) % controls.length
    : event.key === "ArrowLeft" ? (index + controls.length - 1) % controls.length
    : event.key === "Home" ? 0 : event.key === "End" ? controls.length - 1 : undefined;
  if (next !== undefined) { event.preventDefault(); controls[next]?.focus(); }
}

function ActivityRow({ entry, type }: { entry: SocialActivityEntry; type: SocialActivityType }) {
  const [broken, setBroken] = useState(false);
  return (
    <li className={styles.row}>
      <div className={styles.thumbnail}>
        {entry.itemAvailable && entry.coverUrl && !broken
          ? <Image src={entry.coverUrl} alt="" width={80} height={80} unoptimized onError={() => setBroken(true)} />
          : <ImageSquare size={28} weight="duotone" aria-hidden="true" />}
      </div>
      <div className={styles.content}>
        {entry.itemAvailable && entry.itemId
          ? <Link className={styles.itemName} href={`/items/${encodeURIComponent(entry.itemId)}`}>{entry.itemName}</Link>
          : <strong>Nội dung hiện không khả dụng</strong>}
        {entry.itemAvailable && entry.merchantName && <p className={styles.meta}>{entry.merchantName}</p>}
        {entry.activityAt && <time className={styles.meta} dateTime={entry.activityAt}>{formatDateTime(entry.activityAt)}</time>}
        {entry.itemAvailable && type === "COMMENTED" && entry.commentExcerpt && <p className={styles.excerpt}>{entry.commentExcerpt}</p>}
      </div>
    </li>
  );
}

export function SocialActivityScreen({ accountId, type }: { accountId?: string; type: SocialActivityType }) {
  const activity = useSocialActivity({ accountId, type, enabled: true });
  const active = tabs.find(tab => tab.type === type)!;
  const returnTo = encodeURIComponent(`/me/activity?type=${type}`);
  return (
    <section className={styles.screen} aria-labelledby="activity-title">
      <header className={styles.header}>
        <Link href="/me" className={styles.back} aria-label="Quay lại hồ sơ"><ArrowLeft size={22} aria-hidden="true" /></Link>
        <div><h1 id="activity-title">Hoạt động của tôi</h1><p>Nội dung bạn đang thích, lưu và các bình luận còn hiển thị.</p></div>
      </header>
      <div className={styles.tabs} role="tablist" aria-label="Loại hoạt động">
        {tabs.map(tab => {
          const Icon = tab.icon;
          return <Link key={tab.type} id={`activity-tab-${tab.type}`} role="tab" aria-selected={type === tab.type}
            aria-controls="activity-panel" tabIndex={type === tab.type ? 0 : -1} onKeyDown={moveTabFocus}
            href={`/me/activity?type=${tab.type}`} scroll={false}>
            <Icon size={20} weight={type === tab.type ? "fill" : "regular"} aria-hidden="true" />{tab.label}
          </Link>;
        })}
      </div>
      <div id="activity-panel" role="tabpanel" aria-labelledby={`activity-tab-${type}`} tabIndex={0}
        aria-busy={activity.status === "loading" || activity.status === "loadingMore"}>
        {activity.status === "unauthorized" ? <ErrorState title="Vui lòng đăng nhập lại"
          description="Không thể truy cập hoạt động của tài khoản."
          action={<Link className="button-primary" href={`/login?returnTo=${returnTo}`}>Đăng nhập lại</Link>} /> : <>
          {activity.status === "loading" && <div role="status" className="state-card">Đang tải hoạt động…<div className={styles.skeleton} aria-hidden="true" /></div>}
          {activity.items.length > 0 && <ul className={styles.list}>{activity.items.map(entry =>
            <ActivityRow key={type === "COMMENTED" ? entry.commentId : entry.itemId} entry={entry} type={type} />)}</ul>}
          {activity.status === "ready" && activity.items.length === 0 && <p className="state-card" role="status">{active.empty}</p>}
          {activity.status === "error" && <ErrorState title="Không thể tải hoạt động" description="Vui lòng thử lại. Nội dung đã tải vẫn được giữ lại."
            action={<button type="button" className="button-secondary" onClick={activity.retry}>Thử lại</button>} />}
          {activity.status === "loadingMore" && <p role="status">Đang tải thêm hoạt động…</p>}
          {activity.nextCursor && (activity.status === "ready" || activity.status === "loadingMore") &&
            <button type="button" className={`button-secondary ${styles.more}`} disabled={activity.status === "loadingMore"} onClick={activity.loadMore}>Xem thêm</button>}
          {activity.status === "ready" && activity.items.length > 0 && <p role="status" className={styles.meta}>Đã tải {activity.items.length} hoạt động.</p>}
        </>}
      </div>
    </section>
  );
}
