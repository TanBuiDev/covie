"use client";

import { useEffect, useRef, useState } from "react";
import { ArrowUpRight, Lightning, MapPin, ShoppingBag, Storefront } from "@phosphor-icons/react";
import type { PublicAd } from "@/features/ads/ad-api";
import styles from "./ad-creative.module.css";

export function AdCreative({ ad, paused = true, onClick, onMediaFailure }: { ad: PublicAd; paused?: boolean; onClick?: () => void; onMediaFailure?: () => void }) {
  const video = useRef<HTMLVideoElement>(null);
  const [mediaFailed, setMediaFailed] = useState(false);
  const vertical = ad.placement === "SIDEBAR_LEFT";
  useEffect(() => {
    const element = video.current;
    if (!element) return;
    if (paused) element.pause();
    else { const playing = element.play(); playing?.catch(() => undefined); }
  }, [paused]);
  const variant = (ad.template ?? "HERO").toLowerCase();
  const Icon = variant === "flash" ? Lightning : variant === "nearby" ? MapPin : variant === "spotlight" ? Storefront : ShoppingBag;
  const label = `${ad.title} — ${ad.ctaLabel || "Xem ngay"}`;
  if (ad.creativeType === "CSS") return <a href={ad.targetUrl} aria-label={label} onClick={onClick} className={`${styles.banner} ${styles[variant]} ${vertical ? styles.vertical : styles.horizontal} ${paused ? styles.paused : ""}`}>
    <span className={styles.disclosure}>Quảng cáo</span>
    {variant === "flash" && <><span className={styles.flashHalo} aria-hidden="true" /><span className={styles.flashBulbs} aria-hidden="true">{Array.from({ length: 10 }, (_, i) => <i key={i} />)}</span></>}
    {variant === "nearby" && <span className={styles.nearbyPattern} aria-hidden="true" />}
    {variant === "spotlight" && <span className={styles.spotlightBeam} aria-hidden="true" />}
    {variant === "hero" && <span className={styles.heroGrid} aria-hidden="true" />}
    <strong className={styles.dynamicTitle}>{ad.title}</strong>
    <Icon className={styles.art} size={vertical ? 84 : 100} weight="duotone" aria-hidden="true" />
    {ad.body && <p>{ad.body}</p>}
    <span className={styles.cta}>{ad.ctaLabel || "Xem ngay"}<ArrowUpRight size={16} aria-hidden="true" /></span>
  </a>;
  if (mediaFailed) return null;
  return <div className={`${styles.mediaCreative} ${vertical ? styles.vertical : ""}`}>
    <span className={styles.label}>Quảng cáo</span>
    {ad.creativeType === "VIDEO" ? <video ref={video} src={ad.mediaUrl} muted playsInline controls loop preload="metadata" aria-label={ad.title} onError={() => { setMediaFailed(true); onMediaFailure?.(); }} /> :
      <a href={ad.targetUrl} aria-label={label} onClick={onClick}>
        {/* Uploaded verified media has dynamic origins and dimensions. */}
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={ad.mediaUrl} alt={ad.title ?? "Quảng cáo"} loading="lazy" onError={() => { setMediaFailed(true); onMediaFailure?.(); }} />
      </a>}
    <a className={styles.mediaCopy} href={ad.targetUrl} aria-label={label} onClick={onClick}>
      <strong>{ad.title}</strong>{ad.body && <p>{ad.body}</p>}<span className={styles.cta}>{ad.ctaLabel || "Xem ngay"}<ArrowUpRight size={16} aria-hidden="true" /></span>
    </a>
  </div>;
}
