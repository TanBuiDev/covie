"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
import { AdSlot } from "./ad-slot";
import styles from "./ad-discovery-layout.module.css";

/** The rail occupies the existing outer gutter; it never steals catalog width. */
export function AdDiscoveryLayout({ children }: { children: ReactNode }) {
  const content = useRef<HTMLDivElement>(null);
  const [fits, setFits] = useState(false);
  useEffect(() => {
    const measure = () => {
      const box = content.current?.getBoundingClientRect();
      setFits(window.innerWidth >= 1440 && window.innerHeight >= 620 && !!box && box.left >= 240);
    };
    measure();
    window.addEventListener("resize", measure);
    const observer = typeof ResizeObserver !== "undefined" ? new ResizeObserver(measure) : undefined;
    if (content.current) observer?.observe(content.current);
    return () => { window.removeEventListener("resize", measure); observer?.disconnect(); };
  }, []);
  return <div className={styles.layout} ref={content}>
    {fits && <div className={styles.rail}><div className={styles.sticky}><AdSlot placement="SIDEBAR_LEFT" device="DESKTOP" /></div></div>}
    {children}
  </div>;
}
