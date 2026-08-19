import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { CatalogCreateForm } from "../../features/catalog/catalog-create-form";
import { invalidateCsrfToken } from "../../features/auth/csrf";

afterEach(() => {
  cleanup();
  invalidateCsrfToken();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

it("submits the default catalog variant with an Idempotency-Key", async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: "X-CSRF-Token", token: "csrf-token" })))
    .mockResolvedValueOnce(new Response(JSON.stringify({ id: "00000000-0000-0000-0000-000000000003" }), { status: 201 }));
  vi.stubGlobal("fetch", fetchMock);
  const complete = vi.fn();
  render(<CatalogCreateForm merchantId="00000000-0000-0000-0000-000000000001" onCreated={complete} />);

  fireEvent.change(screen.getByLabelText("Tên sản phẩm hoặc dịch vụ"), { target: { value: "Cà phê sữa" } });
  fireEvent.change(screen.getByLabelText("Category ID"), { target: { value: "00000000-0000-0000-0000-000000000002" } });
  fireEvent.change(screen.getByLabelText("Location ID"), { target: { value: "00000000-0000-0000-0000-000000000004" } });
  fireEvent.change(screen.getByLabelText("Giá (VND)"), { target: { value: "25000" } });
  fireEvent.click(screen.getByRole("button", { name: "Tạo catalog" }));

  await waitFor(() => expect(complete).toHaveBeenCalledTimes(1));
  const request = fetchMock.mock.calls[1]?.[0];
  expect(request).toBeInstanceOf(Request);
  if (!(request instanceof Request)) throw new Error("Expected a request");
  expect(request.headers.get("Idempotency-Key")).toBeTruthy();
  expect(JSON.parse(await request.text())).toMatchObject({
    name: "Cà phê sữa",
    options: [],
    variants: [],
    locationOffers: [{ variantClientKey: "__default__", price: 25000, inventoryMode: "UNTRACKED" }],
  });
});
