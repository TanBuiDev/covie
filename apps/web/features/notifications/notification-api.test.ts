import { afterEach, expect, it, vi } from "vitest";
import { inbox, markAllRead, markRead } from "./notification-api";
import { invalidateCsrfToken } from "@/features/auth/csrf";

afterEach(() => { invalidateCsrfToken(); vi.unstubAllGlobals(); });
it.each(["inbox", "item", "all"])("retains typed notification error for %s", async operation => {
  const fetch = vi.fn();
  if (operation !== "inbox") fetch.mockResolvedValueOnce(new Response(JSON.stringify({ headerName: "X-CSRF-Token", token: "fixture" })));
  fetch.mockResolvedValue(new Response(JSON.stringify({ error: { code: "ACCESS_DENIED", requestId: "fixture-request" } }), { status: 403 }));
  vi.stubGlobal("fetch", fetch);
  await expect(operation === "inbox" ? inbox() : operation === "item" ? markRead("notification") : markAllRead())
    .rejects.toMatchObject({ status: 403, code: "ACCESS_DENIED", requestId: "fixture-request" });
});
