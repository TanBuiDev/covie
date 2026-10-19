"use client";

import { useEffect, useRef, useState } from "react";
import { PlayCircle, VideoCameraSlash } from "@phosphor-icons/react";
import styles from "./marketplace-view.module.css";

let activePlayer: HTMLVideoElement | null = null;

export function SocialVideo({ url, name }: { url: string; name: string }) {
  const [requested, setRequested] = useState(false);
  const [failed, setFailed] = useState(false);
  const player = useRef<HTMLVideoElement>(null);

  useEffect(() => {
    const video = player.current;
    if (!requested || failed || !video) return;
    let active = true;
    void video.play().catch(() => { if (active) setFailed(true); });
    const pause = () => video.pause();
    const visibility = () => { if (document.visibilityState === "hidden") pause(); };
    const observer = typeof IntersectionObserver === "undefined" ? undefined : new IntersectionObserver(entries => {
      if (entries.some(entry => !entry.isIntersecting)) pause();
    });
    observer?.observe(video);
    document.addEventListener("visibilitychange", visibility);
    return () => {
      active = false;
      observer?.disconnect();
      document.removeEventListener("visibilitychange", visibility);
      pause();
      if (activePlayer === video) activePlayer = null;
      video.removeAttribute("src");
      video.load();
    };
  }, [requested, failed, url]);

  if (failed) return <span className={styles.mediaFallback} role="alert">
    <VideoCameraSlash size={32} aria-hidden="true" />
    <span>Không thể phát video. Vui lòng thử lại khi tải lại nội dung.</span>
  </span>;
  if (!requested) return <button type="button" className={styles.videoPlay} aria-label={`Phát video ${name}`}
    onClick={() => setRequested(true)}>
    <PlayCircle size={40} aria-hidden="true" />
    <span>Video · Nhấn để phát</span>
  </button>;
  return <video ref={player} className={styles.postVideo} src={url} aria-label={`Video ${name}`}
    controls muted playsInline preload="none" onError={() => setFailed(true)} onPlay={event => {
      if (activePlayer && activePlayer !== event.currentTarget) activePlayer.pause();
      activePlayer = event.currentTarget;
    }}>
    Trình duyệt không hỗ trợ video.
  </video>;
}
