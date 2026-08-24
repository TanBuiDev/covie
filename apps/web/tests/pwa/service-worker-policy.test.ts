import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

describe("service worker cache policy", () => {
  it("keeps business API requests network-only", () => {
    const source = readFileSync(join(process.cwd(), "public", "sw.js"), "utf8");
    expect(source).toContain('url.pathname.startsWith("/api/v1/")');
    expect(source).toContain("event.respondWith(fetch(request))");
    expect(source).not.toContain('SHELL_ASSETS = ["/api/v1');
  });

  it("pre-caches only local shell assets", () => {
    const source = readFileSync(join(process.cwd(), "public", "sw.js"), "utf8");
    expect(source).toContain('["/icon.svg", "/offline.html"]');
  });
});
