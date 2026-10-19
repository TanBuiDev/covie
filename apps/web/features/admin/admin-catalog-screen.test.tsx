import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AdminCatalogScreen } from "./admin-catalog-screen";

const mocks = vi.hoisted(() => ({ request: vi.fn(), replace: vi.fn(), query: "" }));
vi.mock("@/lib/api/api-client", () => ({ requestBrowserApi: mocks.request }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mocks.replace }),
  usePathname: () => "/admin/listings",
  useSearchParams: () => new URLSearchParams(mocks.query),
}));
const firstId = "11111111-1111-4111-8111-111111111111";
const nextId = "22222222-2222-4222-8222-222222222222";
const caseId = "33333333-3333-4333-8333-333333333333";
const item = { id: firstId, name: "Máy lạnh đã qua sử dụng", type: "PRODUCT", listingKind: "RESALE",
  merchantName: "Cửa hàng CoVie", listingStatus: "ACTIVE", moderationStatus: "HIDDEN", updatedAt: "2026-10-15T00:00:00Z" };
const json = (value: unknown) => new Response(JSON.stringify(value), { status: 200, headers: { "Content-Type": "application/json" } });

describe("Admin catalog screen", () => {
  beforeEach(() => { vi.clearAllMocks(); mocks.query = ""; });

  it("offers manual playback only for READY video in the authorized detail", async () => {
    vi.stubEnv("NEXT_PUBLIC_COVIE_SOCIAL_V2_VIDEO_ENABLED", "true");
    try {
      mocks.query = "item=" + firstId;
      mocks.request.mockImplementation((_method: string, path: string) => Promise.resolve(json(path.includes(firstId) ? {
        item, media: [
          { id: "ready", contentType: "video/mp4", status: "READY", url: "/api/v1/media/ready/content" },
          { id: "pending", contentType: "video/webm", status: "PENDING_UPLOAD" },
        ], moderationCases: [], locations: [],
      } : { items: [item], total: 1 })));
      const view = render(<AdminCatalogScreen />);
      await screen.findByRole("button", { name: `Phát video ${item.name}` });
      expect(view.container.querySelector('img[src*="/content"]')).toBeNull();
      expect(view.container.querySelector("video")).toBeNull();
      expect(screen.getByLabelText("Video chưa sẵn sàng")).toBeInTheDocument();
      expect(mocks.request.mock.calls.every(([method]) => method === "GET")).toBe(true);
    } finally { vi.unstubAllEnvs(); }
  });

  it("shows only server-confirmed caption presence and tolerates older summaries", async () => {
    mocks.request.mockResolvedValue(json({ items: [
      { ...item, hasCaption: true },
      { ...item, id: nextId, name: "Chỉ có mô tả", hasCaption: false },
      { ...item, id: caseId, name: "Dữ liệu phiên bản trước" },
    ], total: 3 }));
    render(<AdminCatalogScreen />);
    await screen.findByText("Dữ liệu phiên bản trước");
    expect(within(screen.getByRole("row", { name: /Máy lạnh đã qua sử dụng/ })).getByText("Có caption", { exact: true })).toBeVisible();
    expect(within(screen.getByRole("row", { name: /Chỉ có mô tả/ })).getByText("Chưa có caption", { exact: true })).toBeVisible();
    expect(within(screen.getByRole("row", { name: /Dữ liệu phiên bản trước/ })).queryByText(/caption/)).toBeNull();
    expect(screen.queryByText(/video/i)).toBeNull();
  });

  it("shows server-confirmed video presence and tolerates summaries without the new field", async () => {
    mocks.request.mockResolvedValue(json({ items: [
      { ...item, hasVideo: true },
      { ...item, id: nextId, name: "Không có video", hasVideo: false },
      { ...item, id: caseId, name: "Dữ liệu phiên bản trước" },
    ], total: 3 }));
    render(<AdminCatalogScreen />);
    await screen.findByText("Dữ liệu phiên bản trước");
    expect(within(screen.getByRole("row", { name: /Máy lạnh đã qua sử dụng/ })).getByText("Có video", { exact: true })).toBeVisible();
    expect(within(screen.getByRole("row", { name: /Không có video/ })).getByText("Chưa có video", { exact: true })).toBeVisible();
    expect(within(screen.getByRole("row", { name: /Dữ liệu phiên bản trước/ })).queryByText(/video/)).toBeNull();
    expect(document.querySelector("video")).toBeNull();
    expect(mocks.request.mock.calls.every(([method]) => method === "GET")).toBe(true);
  });

  it("loads real rows, appends another page and opens only the actual moderation case", async () => {
    mocks.request.mockImplementation((_method: string, path: string) => {
      if (path.includes(firstId)) return Promise.resolve(json({ item, description: "Mô tả có thật", socialCaption: "<script>caption</script>\nDòng tiếp theo", media: [], locations: [],
        moderationCases: [{ id: caseId, status: "OPEN", reasonCode: "SPAM" }], moderationCaseTotal: 1 }));
      if (path.includes("page=1")) return Promise.resolve(json({ items: [{ ...item, id: nextId, name: "Nồi chiên" }], total: 2 }));
      return Promise.resolve(json({ items: [item], total: 2 }));
    });
    const view = render(<AdminCatalogScreen />);
    expect(screen.getByRole("status")).toHaveTextContent("Đang tải");
    await screen.findByRole("button", { name: "Xem Máy lạnh đã qua sử dụng" });
    fireEvent.click(screen.getByRole("button", { name: "Xem thêm" }));
    await screen.findByRole("button", { name: "Xem Nồi chiên" });
    expect(screen.getByRole("button", { name: "Xem Máy lạnh đã qua sử dụng" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Xem Máy lạnh đã qua sử dụng" }));
    expect(mocks.replace).toHaveBeenCalledWith(expect.stringContaining("item=" + firstId), { scroll: false });
    mocks.query = "item=" + firstId;
    view.rerender(<AdminCatalogScreen />);
    await screen.findByText("Mô tả có thật");
    const caption = await screen.findByText("<script>caption</script> Dòng tiếp theo");
    expect(caption.textContent).toBe("<script>caption</script>\nDòng tiếp theo");
    expect(caption.querySelector("script")).toBeNull();
    expect(screen.getByRole("link", { name: /Mở hồ sơ/ })).toHaveAttribute("href", "/admin/moderation/" + caseId);
    expect(mocks.request.mock.calls.every(([method]) => method === "GET")).toBe(true);
  });

  it("shows unauthorized and retries a failed list without claiming data loaded", async () => {
    mocks.request.mockResolvedValueOnce(new Response(JSON.stringify({ error: { code: "ADMIN_ROLE_REQUIRED" } }), { status: 403 }));
    mocks.request.mockResolvedValueOnce(json({ items: [], total: 0 }));
    render(<AdminCatalogScreen />);
    expect(await screen.findByRole("alert")).toHaveTextContent("quyền");
    expect(screen.queryByRole("button", { name: "Xem thêm" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    await screen.findByText("Không có nội dung phù hợp.");
  });

  it("preserves filters in URL and explicitly handles an item without a moderation case", async () => {
    mocks.query = "q=CoVie&listingKind=RESALE&item=" + firstId;
    mocks.request.mockImplementation((_method: string, path: string) => Promise.resolve(json(path.includes(firstId)
      ? { item, description: "Nội dung chưa có case", media: [], locations: [], moderationCases: [], moderationCaseTotal: 0 }
      : { items: [item], total: 1 })));
    render(<AdminCatalogScreen />);
    await screen.findByText("Chưa có hồ sơ kiểm duyệt cho nội dung này.");
    expect(screen.getByText("Chưa có caption.")).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Tìm nội dung" })).toHaveValue("CoVie");
    fireEvent.change(screen.getByRole("textbox", { name: "Tìm nội dung" }), { target: { value: "Máy lạnh" } });
    fireEvent.click(screen.getByRole("button", { name: "Áp dụng" }));
    await waitFor(() => expect(mocks.replace).toHaveBeenCalledWith(expect.stringContaining("q=M%C3%A1y+l%E1%BA%A1nh"), { scroll: false }));
    expect(screen.queryByRole("button", { name: /Ẩn nội dung/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Xác nhận mở hồ sơ" })).not.toBeInTheDocument();
  });

  it("requires a reason and confirmation, retries with the same key and navigates to the returned case", async () => {
    mocks.query = "item=" + firstId;
    let commands = 0;
    mocks.request.mockImplementation((method: string, path: string) => {
      if (method === "POST") {
        commands++;
        return Promise.resolve(commands === 1 ? new Response("", { status: 503 }) : json({ caseId, created: false }));
      }
      return Promise.resolve(json(path.includes(firstId)
        ? { item, media: [], locations: [], moderationCases: [] } : { items: [item], total: 1 }));
    });
    render(<AdminCatalogScreen caseOpeningEnabled />);
    const submit = await screen.findByRole("button", { name: "Xác nhận mở hồ sơ" });
    expect(submit).toBeDisabled();
    fireEvent.change(screen.getByRole("textbox", { name: "Mã lý do" }), { target: { value: "SPAM" } });
    fireEvent.change(screen.getByRole("textbox", { name: "Ghi chú" }), { target: { value: "Nội dung cần kiểm tra" } });
    expect(submit).toBeDisabled();
    fireEvent.click(screen.getByRole("checkbox", { name: "Tôi xác nhận mở hồ sơ; thao tác này không đổi trạng thái nội dung." }));
    fireEvent.click(submit);
    await screen.findByRole("alert");
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận mở hồ sơ" }));
    await waitFor(() => expect(mocks.replace).toHaveBeenCalledWith("/admin/moderation/" + caseId));
    const requests = mocks.request.mock.calls.filter(([method]) => method === "POST");
    expect(requests).toHaveLength(2);
    expect(requests[0]).toEqual(["POST", "/api/v1/admin/catalog-items/" + firstId + "/moderation-cases",
      { reasonCode: "SPAM", note: "Nội dung cần kiểm tra" }, { "Idempotency-Key": expect.any(String) }]);
    expect(requests[1]?.[3]).toEqual(requests[0]?.[3]);
  });

  it("refreshes authoritative item and cases after an opening conflict without inventing a successful case", async () => {
    mocks.query = "item=" + firstId;
    let detailReads = 0;
    mocks.request.mockImplementation((method: string, path: string) => {
      if (method === "POST") return Promise.resolve(new Response(JSON.stringify({ error: {
        code: "MODERATION_TARGET_STATE_CONFLICT", message: "Nội dung vừa thay đổi." } }), { status: 409 }));
      if (path.includes(firstId)) {
        detailReads++;
        return Promise.resolve(json({ item, media: [], locations: [], moderationCases: detailReads > 1
          ? [{ id: caseId, status: "RESOLVED" }] : [] }));
      }
      return Promise.resolve(json({ items: [item], total: 1 }));
    });
    render(<AdminCatalogScreen caseOpeningEnabled />);
    await screen.findByRole("button", { name: "Xác nhận mở hồ sơ" });
    fireEvent.change(screen.getByRole("textbox", { name: "Mã lý do" }), { target: { value: "SPAM" } });
    fireEvent.click(screen.getByRole("checkbox"));
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận mở hồ sơ" }));
    await screen.findByRole("link", { name: "Mở hồ sơ " + caseId });
    expect(detailReads).toBe(2);
    expect(mocks.replace).not.toHaveBeenCalled();
  });

  it("retries the failed next page while retaining previously loaded rows", async () => {
    let nextAttempts = 0;
    mocks.request.mockImplementation((_method: string, path: string) => {
      if (path.includes("page=1")) {
        nextAttempts++;
        return Promise.resolve(nextAttempts === 1 ? new Response("", { status: 503 })
          : json({ items: [{ ...item, id: nextId, name: "Nồi chiên" }], total: 2 }));
      }
      return Promise.resolve(json({ items: [item], total: 2 }));
    });
    render(<AdminCatalogScreen />);
    await screen.findByRole("button", { name: "Xem Máy lạnh đã qua sử dụng" });
    fireEvent.click(screen.getByRole("button", { name: "Xem thêm" }));
    await screen.findByRole("alert");
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    await screen.findByRole("button", { name: "Xem Nồi chiên" });
    expect(screen.getByRole("button", { name: "Xem Máy lạnh đã qua sử dụng" })).toBeInTheDocument();
  });

  it.each([401, 403])("clears the entire private catalog after case history denies access with %s", async status => {
    mocks.query = "item=" + firstId;
    mocks.request.mockImplementation((_method: string, path: string) => Promise.resolve(
      path.includes("/moderation-cases?") ? new Response(JSON.stringify({ error: { code: "ADMIN_ROLE_REQUIRED" } }), { status })
        : json(path.includes(firstId)
          ? { item, socialCaption: "Private caption", media: [], locations: [], moderationCases: [{ id: caseId, status: "OPEN" }],
            moderationCaseTotal: 51, moderationCaseNextCursor: "older-cases" }
          : { items: [item], total: 1 })));
    render(<AdminCatalogScreen caseOpeningEnabled />);
    await screen.findByText("Private caption");
    await screen.findByRole("button", { name: "Xem Máy lạnh đã qua sử dụng" });
    fireEvent.click(screen.getByRole("button", { name: "Xem thêm hồ sơ" }));
    await screen.findByRole("alert");
    expect(screen.queryByRole("table")).toBeNull();
    expect(screen.queryByText("Private caption")).toBeNull();
    expect(screen.queryByRole("link", { name: "Mở hồ sơ " + caseId })).toBeNull();
    expect(screen.queryByRole("button", { name: "Xác nhận mở hồ sơ" })).toBeNull();
    expect(mocks.request.mock.calls.every(([method]) => method === "GET")).toBe(true);
  });

  it("does not restore denied private rows from an older successful page and can explicitly retry", async () => {
    mocks.query = "item=" + firstId;
    let finishPage!: (response: Response) => void;
    let denied = false;
    mocks.request.mockImplementation((_method: string, path: string) => {
      if (path.includes("page=1")) return new Promise<Response>(resolve => { finishPage = resolve; });
      if (path.includes("/moderation-cases?")) {
        denied = true;
        return Promise.resolve(new Response("", { status: 403 }));
      }
      return Promise.resolve(json(path.includes(firstId)
        ? { item, socialCaption: "Private caption", media: [], locations: [], moderationCases: [{ id: caseId, status: "OPEN" }],
          moderationCaseTotal: 51, moderationCaseNextCursor: "older-cases" }
        : { items: [item], total: denied ? 1 : 2 }));
    });
    render(<AdminCatalogScreen caseOpeningEnabled />);
    await screen.findByText("Private caption");
    fireEvent.click(await screen.findByRole("button", { name: "Xem thêm" }));
    fireEvent.click(screen.getByRole("button", { name: "Xem thêm hồ sơ" }));
    await screen.findByRole("alert");
    finishPage(json({ items: [{ ...item, id: nextId, name: "Older private row" }], total: 2 }));
    await waitFor(() => expect(screen.queryByRole("button", { name: "Đang tải…" })).toBeNull());
    expect(screen.queryByRole("table")).toBeNull();
    expect(screen.queryByText("Older private row")).toBeNull();
    expect(screen.queryByText("Private caption")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    await screen.findByText("Private caption");
    expect(screen.getByRole("button", { name: "Xem Máy lạnh đã qua sử dụng" })).toBeVisible();
    expect(screen.queryByText("Older private row")).toBeNull();
  });

  it("loads older cases by cursor and retains existing case links after a retry", async () => {
    mocks.query = "item=" + firstId;
    const olderCaseId = "55555555-5555-4555-8555-555555555555";
    const recentCases = Array.from({ length: 50 }, (_, index) => ({
      id: index === 0 ? caseId : "44444444-4444-4444-8444-" + String(index).padStart(12, "0"), status: "OPEN",
    }));
    let attempts = 0;
    mocks.request.mockImplementation((_method: string, path: string) => {
      if (path.includes("/moderation-cases?")) {
        attempts++;
        return Promise.resolve(attempts === 1 ? new Response("", { status: 503 })
          : json({ items: [{ id: olderCaseId, status: "CLOSED" }], total: 51, hasMore: false }));
      }
      return Promise.resolve(json(path.includes(firstId)
        ? { item, media: [], locations: [], moderationCases: recentCases, moderationCaseTotal: 51, moderationCaseNextCursor: "oldest-cursor" }
        : { items: [item], total: 1 }));
    });
    render(<AdminCatalogScreen />);
    await screen.findByRole("link", { name: "Mở hồ sơ " + caseId });
    fireEvent.click(screen.getByRole("button", { name: "Xem thêm hồ sơ" }));
    await screen.findByRole("button", { name: "Thử lại hồ sơ" });
    expect(screen.getByRole("link", { name: "Mở hồ sơ " + caseId })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Thử lại hồ sơ" }));
    await screen.findByRole("link", { name: "Mở hồ sơ " + olderCaseId });
    expect(screen.getAllByRole("link", { name: /^Mở hồ sơ / })).toHaveLength(51);
    expect(screen.queryByRole("button", { name: "Xem thêm hồ sơ" })).not.toBeInTheDocument();
    expect(mocks.request.mock.calls.filter(([, path]) => String(path).includes("/moderation-cases?"))
      .every(([, path]) => String(path).includes("cursor=oldest-cursor"))).toBe(true);
  });
});
