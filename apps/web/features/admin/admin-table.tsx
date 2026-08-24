"use client";
import type { ReactNode } from "react";
export function AdminTable({ title, children }: { title: string; children: ReactNode }) { return <section><h1 style={{ fontFamily: "Georgia, serif" }}>{title}</h1><div style={{ overflowX: "auto", border: "1px solid #d6d3d1", borderRadius: 10, background: "#fff" }}>{children}</div></section>; }
