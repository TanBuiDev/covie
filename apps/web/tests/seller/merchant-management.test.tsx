import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { MerchantForm, MerchantList } from "../../features/merchant/merchant-form";
import { invalidateCsrfToken } from "../../features/auth/csrf";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: vi.fn() }),
}));

afterEach(() => {
  cleanup();
  invalidateCsrfToken();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

it("renders generated merchant summaries", () => {
  render(<MerchantList merchants={[{
    id: "00000000-0000-0000-0000-000000000001",
    name: "Tạp hóa Minh",
    slug: "tap-hoa-minh",
    status: "ACTIVE",
    currentUserRole: "OWNER",
    version: 0,
  }]} />);

  expect(screen.getByText("Tạp hóa Minh")).toBeInTheDocument();
  expect(screen.getByText("Đang hoạt động")).toBeInTheDocument();
});

it("creates a merchant through the generated endpoint and refreshes the list", async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: "X-CSRF-Token", token: "csrf-token" })))
    .mockResolvedValueOnce(new Response(JSON.stringify({ id: "00000000-0000-0000-0000-000000000001" }), { status: 201 }));
  vi.stubGlobal("fetch", fetchMock);
  const refresh = vi.fn();
  render(<MerchantForm onCreated={refresh} />);

  fireEvent.change(screen.getByLabelText("Tên cửa hàng"), { target: { value: "Tạp hóa Minh" } });
  fireEvent.click(screen.getByRole("button", { name: "Tạo cửa hàng" }));

  await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
  const request = fetchMock.mock.calls[1]?.[0];
  expect(request).toBeInstanceOf(Request);
  if (!(request instanceof Request)) throw new Error("Expected a request");
  expect(request.method).toBe("POST");
  expect(new URL(request.url).pathname).toBe("/api/v1/me/merchants");
});

it("shows a reload action instead of resubmitting a stale merchant edit", async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: "X-CSRF-Token", token: "csrf-token" })))
    .mockResolvedValueOnce(new Response(JSON.stringify({ error: { code: "RESOURCE_VERSION_CONFLICT" } }), { status: 409 }));
  vi.stubGlobal("fetch", fetchMock);
  const reload = vi.fn();
  render(<MerchantForm merchant={{
    data: { id: "00000000-0000-0000-0000-000000000001", name: "Cũ", description: "", status: "ACTIVE", version: 2 },
    etag: "\"v2\"",
  }} onReload={reload} />);

  fireEvent.change(screen.getByLabelText("Tên shop"), { target: { value: "Mới" } });
  fireEvent.click(screen.getByRole("button", { name: "Lưu thay đổi" }));

  await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Dữ liệu đã được thay đổi"));
  expect(fetchMock).toHaveBeenCalledTimes(2);
  fireEvent.click(screen.getByRole("button", { name: "Tải dữ liệu mới" }));
  expect(reload).toHaveBeenCalledTimes(1);
});
