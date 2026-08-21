import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { CovieApiError } from "@/lib/api/api-error";
import { CheckoutPreview } from "./checkout-preview";

const { previewCheckout, commitCheckout } = vi.hoisted(() => ({ previewCheckout: vi.fn(), commitCheckout: vi.fn() }));
vi.mock("./checkout-api", () => ({ previewCheckout, commitCheckout }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));

afterEach(() => {
  cleanup();
  previewCheckout.mockReset();
  commitCheckout.mockReset();
});

const carts = [{ id: "cart", merchantLocationId: "location", cartType: "PRODUCT" as const, version: 1, items: [] }];

it("renders a negotiated delivery preview without inventing a zero fee", async () => {
  previewCheckout.mockResolvedValueOnce({ checkoutToken: "token", subtotal: 100_000, shippingFee: undefined, total: undefined, currency: "VND", fulfillmentMethod: "SELLER_DELIVERY" });
  render(<CheckoutPreview carts={carts} addresses={[{ id: "address", label: "Nhà", addressText: "1 CoVie" }]} />);
  fireEvent.click(screen.getByLabelText("SELLER_DELIVERY"));
  fireEvent.change(screen.getByLabelText("Địa chỉ"), { target: { value: "address" } });
  fireEvent.click(screen.getByRole("button", { name: "Xem trước" }));
  await waitFor(() => expect(screen.getByText("Phí giao hàng thỏa thuận")).toBeInTheDocument());
  expect(screen.getByText("Sẽ thỏa thuận với người bán")).toBeInTheDocument();
});

it("discards a changed checkout token and requires a fresh preview", async () => {
  previewCheckout.mockResolvedValueOnce({ checkoutToken: "token", subtotal: 100_000, shippingFee: 0, total: 100_000, currency: "VND", fulfillmentMethod: "PICKUP" });
  commitCheckout.mockRejectedValueOnce(new CovieApiError(409, "CHECKOUT_CHANGED", "changed"));
  render(<CheckoutPreview carts={carts} addresses={[]} />);
  fireEvent.click(screen.getByRole("button", { name: "Xem trước" }));
  await waitFor(() => expect(screen.getByRole("button", { name: "Xác nhận đặt hàng" })).toBeInTheDocument());
  fireEvent.click(screen.getByRole("button", { name: "Xác nhận đặt hàng" }));
  await waitFor(() => expect(screen.getByRole("alertdialog")).toBeInTheDocument());
  expect(screen.queryByRole("button", { name: "Xác nhận đặt hàng" })).not.toBeInTheDocument();
});
