import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import ExplorePage from "../../app/(public)/explore/page";

afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it("requires explicit location and sends coordinates to discovery", async () => {
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ page: { items: [{ id: "item-1", name: "Cà phê", priceFrom: { amount: 25000, currency: "VND" }, resolvedLocation: { name: "Quận 1", distanceMeters: 320 } }], hasMore: false } }), { status: 200, headers: { "Content-Type": "application/json" } }));
  vi.stubGlobal("fetch", fetchMock);
  render(<ExplorePage />);
  expect(screen.queryByText("Cà phê")).toBeNull();
  fireEvent.change(screen.getByLabelText("Vĩ độ"), { target: { value: "10.77" } });
  fireEvent.change(screen.getByLabelText("Kinh độ"), { target: { value: "106.70" } });
  fireEvent.click(screen.getByRole("button", { name: "Dùng vị trí này" }));
  await waitFor(() => expect(screen.getByText("Cà phê")).toBeTruthy());
  const request = fetchMock.mock.calls[0]?.[0];
  expect(request.url).toContain("lat=10.77");
  expect(request.url).toContain("lng=106.7");
});

it("keeps the opaque cursor when requesting the next page", async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(new Response(JSON.stringify({ page: { items: [{ id: "item-1", name: "Một" }], hasMore: true, nextCursor: "opaque.cursor==" } }), { status: 200 }))
    .mockResolvedValueOnce(new Response(JSON.stringify({ page: { items: [{ id: "item-2", name: "Hai" }], hasMore: false } }), { status: 200 }));
  vi.stubGlobal("fetch", fetchMock);
  render(<ExplorePage />);
  fireEvent.change(screen.getByLabelText("Vĩ độ"), { target: { value: "10" } });
  fireEvent.change(screen.getByLabelText("Kinh độ"), { target: { value: "106" } });
  fireEvent.click(screen.getByRole("button", { name: "Dùng vị trí này" }));
  await waitFor(() => expect(screen.getByRole("button", { name: "Tải thêm" })).toBeTruthy());
  fireEvent.click(screen.getByRole("button", { name: "Tải thêm" }));
  await waitFor(() => expect(screen.getByText("Hai")).toBeTruthy());
  expect(fetchMock.mock.calls[1]?.[0].url).toContain("cursor=opaque.cursor%3D%3D");
});
