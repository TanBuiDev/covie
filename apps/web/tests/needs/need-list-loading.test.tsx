import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { NeedList } from "@/features/needs/need-list";
import { CovieApiError } from "@/lib/api/api-error";

it("recognizes an expired session by status even with an opaque message", async () => {
  listMyNeeds.mockRejectedValue(new CovieApiError(401, "AUTH_REQUIRED", "opaque"));
  render(<NeedList />);
  expect(await screen.findByRole("link", { name: "Đăng nhập ngay" })).toHaveAttribute("href", "/login?returnTo=%2Fneeds");
});

it("does not mistake a server message mentioning a token for an expired session", async () => {
  listMyNeeds.mockRejectedValue(new CovieApiError(503, "INTERNAL_ERROR", "token service unavailable"));
  render(<NeedList />);
  expect(await screen.findByRole("button", { name: "Thử lại" })).toBeInTheDocument();
  expect(screen.queryByRole("link", { name: "Đăng nhập ngay" })).not.toBeInTheDocument();
});

const { listMyNeeds } = vi.hoisted(() => ({ listMyNeeds: vi.fn() }));
vi.mock("@/features/needs/need-api", async () => {
  const actual = await vi.importActual<typeof import("@/features/needs/need-api")>("@/features/needs/need-api");
  return { ...actual, listMyNeeds };
});

function page(title: string, status: "ACTIVE" | "FULFILLED" = "ACTIVE") {
  return {
    items: [{
      id: title,
      type: "PRODUCT" as const,
      status,
      categoryId: "category",
      title,
      radiusMeters: 5000,
      expiresAt: "2026-09-30T12:00:00Z",
      responseCount: 0,
      createdAt: "2026-09-12T12:00:00Z",
    }],
    hasMore: false,
    nextCursor: null,
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => { resolve = done; });
  return { promise, resolve };
}

afterEach(() => {
  cleanup();
  listMyNeeds.mockReset();
});

it("loads the active list and reloads when the status filter changes", async () => {
  listMyNeeds.mockResolvedValueOnce(page("Nhu cầu đang tìm"));
  render(<NeedList />);
  await screen.findByText("Nhu cầu đang tìm");
  expect(listMyNeeds).toHaveBeenCalledWith("ACTIVE", undefined);

  listMyNeeds.mockResolvedValueOnce(page("Nhu cầu đã tìm được", "FULFILLED"));
  fireEvent.click(screen.getByRole("button", { name: "Đã tìm được" }));
  await screen.findByText("Nhu cầu đã tìm được");
  expect(listMyNeeds).toHaveBeenLastCalledWith("FULFILLED", undefined);
});

it("offers a retry after a non-authentication failure", async () => {
  listMyNeeds.mockRejectedValueOnce(new Error("Mất kết nối"));
  render(<NeedList />);
  await screen.findByRole("alert");

  listMyNeeds.mockResolvedValueOnce(page("Đã tải lại"));
  fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
  await screen.findByText("Đã tải lại");
  expect(listMyNeeds).toHaveBeenCalledTimes(2);
});

it("does not render a stale response after the status changes or unmounts", async () => {
  const active = deferred<ReturnType<typeof page>>();
  const fulfilled = deferred<ReturnType<typeof page>>();
  listMyNeeds.mockReturnValueOnce(active.promise).mockReturnValueOnce(fulfilled.promise);
  const view = render(<NeedList />);
  await waitFor(() => expect(listMyNeeds).toHaveBeenCalledWith("ACTIVE", undefined));

  fireEvent.click(screen.getByRole("button", { name: "Đã tìm được" }));
  await waitFor(() => expect(listMyNeeds).toHaveBeenCalledWith("FULFILLED", undefined));
  fulfilled.resolve(page("Kết quả mới", "FULFILLED"));
  await screen.findByText("Kết quả mới");
  active.resolve(page("Kết quả cũ"));
  await Promise.resolve();
  expect(screen.queryByText("Kết quả cũ")).not.toBeInTheDocument();

  view.unmount();
});
