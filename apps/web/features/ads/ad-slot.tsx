"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Eye, X } from "@phosphor-icons/react";
import { AdCreative } from "@/components/ads/ad-creative";
import { listActiveAds, trackAdEvent, type AdPlacement, type PublicAd } from "./ad-api";
import styles from "./ad-slot.module.css";

export function safeAdTarget(target?: string): boolean {
  if (!target || /[\\\s\u0000-\u001f]/.test(target)) return false;
  if (target.startsWith("/") && !target.startsWith("//")) return true;
  try { const url = new URL(target); return url.protocol === "https:" && !url.username && !url.password; } catch { return false; }
}

function usable(ad: PublicAd) {
  return !!ad.id && !!ad.deliveryToken && !!ad.title && safeAdTarget(ad.targetUrl)
    && !!ad.endsAt && Date.parse(ad.endsAt) > Date.now()
    && (!ad.deliveryExpiresAt || Date.parse(ad.deliveryExpiresAt) > Date.now())
    && (ad.creativeType === "CSS" || !!ad.mediaUrl);
}

function AdDelivery({ ad, paused, countedDeliveries, onUnavailable }: { ad: PublicAd; paused: boolean; countedDeliveries: Set<string>; onUnavailable: (ad: PublicAd) => void }) {
  const element = useRef<HTMLDivElement>(null);
  const counted = useRef(false);
  const [inView, setInView] = useState(false);
  const [visible, setVisible] = useState(true);
  useEffect(() => {
    const update = () => setVisible(document.visibilityState !== "hidden");
    update();
    document.addEventListener("visibilitychange", update);
    return () => document.removeEventListener("visibilitychange", update);
  }, []);
  useEffect(() => {
    const node = element.current;
    if (!node || typeof IntersectionObserver === "undefined") return;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let intersects = false;
    const clear = () => { if (timer) clearTimeout(timer); timer = undefined; };
    const schedule = () => {
      clear();
      if (!intersects || counted.current || countedDeliveries.has(ad.deliveryToken ?? "") || document.visibilityState === "hidden") return;
      timer = setTimeout(() => {
        if (document.visibilityState === "hidden" || !intersects || !usable(ad)) return;
        counted.current = true;
        countedDeliveries.add(ad.deliveryToken ?? "");
        void trackAdEvent(ad, "IMPRESSION", crypto.randomUUID());
      }, 1000);
    };
    const observer = new IntersectionObserver(entries => {
      const entry = entries[0];
      intersects = !!entry?.isIntersecting && entry.intersectionRatio >= 0.5;
      setInView(intersects);
      schedule();
    }, { threshold: [0, 0.5] });
    observer.observe(node);
    document.addEventListener("visibilitychange", schedule);
    return () => { clear(); observer.disconnect(); document.removeEventListener("visibilitychange", schedule); };
  }, [ad, countedDeliveries]);
  return <div ref={element}>
    <AdCreative ad={ad} paused={paused || !visible || !inView} onMediaFailure={() => onUnavailable(ad)} onClick={() => { void trackAdEvent(ad, "CLICK", crypto.randomUUID()); }} />
  </div>;
}

export function AdSlot({ placement, className = "", device: requestedDevice }: { placement: AdPlacement; className?: string; device?: "DESKTOP" | "MOBILE" }) {
  const [device, setDevice] = useState<"DESKTOP" | "MOBILE">();
  const [ads, setAds] = useState<PublicAd[]>([]);
  const [index, setIndex] = useState(0);
  const [paused] = useState(false);
  const [reducedMotion, setReducedMotion] = useState(false);
  const [pageVisible, setPageVisible] = useState(true);
  const [interacting, setInteracting] = useState(false);
  const [dismissed, setDismissed] = useState(() => {
    if (placement === "SIDEBAR_LEFT" && typeof window !== "undefined") {
      try {
        return window.sessionStorage?.getItem("covie_ad_rail_dismissed") === "true";
      } catch {
        // Safe fallback for restricted environments
      }
    }
    return false;
  });
  const generation = useRef(0);
  const [countedDeliveries] = useState(() => new Set<string>());

  useEffect(() => {
    if (typeof window.matchMedia !== "function") return;
    const screen = window.matchMedia("(min-width: 1024px)");
    const motion = window.matchMedia("(prefers-reduced-motion: reduce)");
    const update = () => setDevice(requestedDevice ?? (screen.matches ? "DESKTOP" : "MOBILE"));
    const updateMotion = () => setReducedMotion(motion.matches);
    const updateVisibility = () => setPageVisible(document.visibilityState !== "hidden");
    update(); updateMotion(); updateVisibility();
    screen.addEventListener("change", update); motion.addEventListener("change", updateMotion);
    document.addEventListener("visibilitychange", updateVisibility);
    return () => { screen.removeEventListener("change", update); motion.removeEventListener("change", updateMotion); document.removeEventListener("visibilitychange", updateVisibility); };
  }, [requestedDevice]);
  useEffect(() => {
    if (!device) return;
    const current = ++generation.current;
    let alive = true;
    const refresh = async () => {
      try {
        const result = await listActiveAds(placement, device);
        if (alive && current === generation.current) {
          setAds(result.filter(usable)); setIndex(value => value < result.length ? value : 0);
        }
      } catch { if (alive && current === generation.current) setAds([]); }
    };
    void refresh();
    const timer = setInterval(() => { if (document.visibilityState !== "hidden") void refresh(); }, 60_000);
    return () => { alive = false; clearInterval(timer); };
  }, [device, placement]);
  const step = useCallback((delta: number) => setIndex(value => (value + delta + ads.length) % ads.length), [ads.length]);
  useEffect(() => {
    if (paused || reducedMotion || !pageVisible || interacting || ads.length < 2) return;
    const timer = setInterval(() => step(1), 10_000);
    return () => clearInterval(timer);
  }, [paused, reducedMotion, pageVisible, interacting, ads.length, step]);
  useEffect(() => {
    if (!ads.length) return;
    const expires = Math.min(...ads.map(ad => Math.min(Date.parse(ad.endsAt ?? ""), ad.deliveryExpiresAt ? Date.parse(ad.deliveryExpiresAt) : Infinity)));
    const timer = setTimeout(() => setAds(previous => previous.filter(usable)), Math.max(0, Math.min(expires - Date.now(), 2_147_483_647)));
    return () => clearTimeout(timer);
  }, [ads]);
  const unavailable = useCallback((failed: PublicAd) => setAds(previous => previous.filter(ad => ad.deliveryToken !== failed.deliveryToken)), []);
  const ad = ads[index] ?? ads[0];

  if (dismissed && placement === "SIDEBAR_LEFT") {
    return (
      <div className={styles.railDismissed}>
        <button
          type="button"
          className={styles.restoreBtn}
          aria-label="Mở lại quảng cáo bên trái"
          title="Mở lại quảng cáo"
          onClick={() => {
            setDismissed(false);
            try { window.sessionStorage?.removeItem("covie_ad_rail_dismissed"); } catch {}
          }}
        >
          <Eye size={14} weight="bold" />
          <span>Quảng cáo</span>
        </button>
      </div>
    );
  }

  if (!ad || !usable(ad)) return null;
  return (
    <aside
      className={`${styles.slot} ${placement === "SIDEBAR_LEFT" ? styles.railSlot : ""} ${className}`}
      aria-label="Quảng cáo"
      data-ad-placement={placement}
      onMouseEnter={() => setInteracting(true)}
      onMouseLeave={() => setInteracting(false)}
      onFocus={() => setInteracting(true)}
      onBlur={event => {
        if (!event.currentTarget.contains(event.relatedTarget)) setInteracting(false);
      }}
      onKeyDown={event => {
        if (ads.length > 1) {
          if (event.key === "ArrowLeft") { event.preventDefault(); step(-1); }
          if (event.key === "ArrowRight") { event.preventDefault(); step(1); }
        }
      }}
    >
      {placement === "SIDEBAR_LEFT" && (
        <button
          type="button"
          className={styles.dismissBtn}
          aria-label="Tạm ẩn quảng cáo"
          title="Tạm ẩn quảng cáo"
          onClick={() => {
            setDismissed(true);
            try { window.sessionStorage?.setItem("covie_ad_rail_dismissed", "true"); } catch {}
          }}
        >
          <X size={12} weight="bold" />
        </button>
      )}
      <AdDelivery key={`${ad.id}:${ad.deliveryToken}`} ad={ad} paused={paused || reducedMotion} countedDeliveries={countedDeliveries} onUnavailable={unavailable} />
      {ads.length > 1 && (
        <div className={styles.bannerDots} role="tablist" aria-label="Chuyển quảng cáo">
          {ads.map((_, i) => (
            <button
              key={i}
              type="button"
              role="tab"
              aria-selected={i === index}
              aria-label={`Chuyển đến quảng cáo ${i + 1}`}
              className={`${styles.dotBtn} ${i === index ? styles.activeDotBtn : ""}`}
              onClick={e => {
                e.preventDefault();
                e.stopPropagation();
                setIndex(i);
              }}
            />
          ))}
        </div>
      )}
    </aside>
  );
}
