import type { components } from "@covie/api-client";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { MerchantContactSettings } from "./contact-settings";

type MerchantContact = components["schemas"]["MerchantContactResponse"];

const mocks = vi.hoisted(() => ({
  refresh: vi.fn(),
  create: vi.fn(),
  remove: vi.fn(),
  upload: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: mocks.refresh }),
}));

vi.mock("./contact-api", () => ({
  createMerchantContact: mocks.create,
  deleteMerchantContact: mocks.remove,
}));

vi.mock("@/features/media/direct-image-upload", () => ({
  uploadImage: mocks.upload,
}));

beforeEach(() => {
  mocks.refresh.mockReset();
  mocks.create.mockReset().mockResolvedValue({});
  mocks.remove.mockReset().mockResolvedValue(undefined);
  mocks.upload.mockReset().mockResolvedValue({ mediaId: "media-qr-1" });
});

it("renders existing frozen contact channels read-only instead of guessing PATCH concurrency", () => {
  const contacts: MerchantContact[] = [
    { id: "zalo-1", merchantLocationId: "location-1", contactType: "ZALO_LINK", value: "https://zalo.me/shop", publicContact: true },
    { id: "messenger-1", merchantLocationId: "location-1", contactType: "MESSENGER_LINK", value: "https://m.me/shop", publicContact: true },
    { id: "qr-1", merchantLocationId: "location-1", contactType: "ZALO_QR", qrMediaId: "media-existing", publicContact: true },
  ];

  render(<MerchantContactSettings merchantId="merchant-1" merchantLocationId="location-1" contacts={contacts} />);

  expect(screen.getByLabelText(/^Zalo link/)).toHaveAttribute("readonly");
  expect(screen.getByLabelText(/^Messenger link/)).toHaveAttribute("readonly");
  expect(screen.getByText("Ảnh QR liên hệ đã được lưu. Thay ảnh cần contact concurrency token từ backend.")).toBeInTheDocument();
  expect(screen.queryByLabelText(/^Chọn ảnh QR liên hệ/)).not.toBeInTheDocument();
});

it("creates only missing Zalo and Messenger channels as public backend contacts", async () => {
  render(<MerchantContactSettings merchantId="merchant-1" merchantLocationId="location-1" contacts={[]} />);

  fireEvent.change(screen.getByLabelText(/^Zalo link/), { target: { value: "https://zalo.me/new-shop" } });
  fireEvent.change(screen.getByLabelText(/^Messenger link/), { target: { value: "https://m.me/new-shop" } });
  fireEvent.click(screen.getByRole("button", { name: "Lưu liên hệ" }));

  await waitFor(() => expect(mocks.create).toHaveBeenCalledTimes(2));
  expect(mocks.create).toHaveBeenNthCalledWith(1, "merchant-1", {
    merchantLocationId: "location-1",
    contactType: "ZALO_LINK",
    value: "https://zalo.me/new-shop",
    publicContact: true,
  });
  expect(mocks.create).toHaveBeenNthCalledWith(2, "merchant-1", {
    merchantLocationId: "location-1",
    contactType: "MESSENGER_LINK",
    value: "https://m.me/new-shop",
    publicContact: true,
  });
  expect(mocks.refresh).toHaveBeenCalled();
});

it("uploads contact QR with the canonical MERCHANT_CONTACT_QR purpose", async () => {
  render(<MerchantContactSettings merchantId="merchant-1" merchantLocationId="location-1" contacts={[]} />);
  const file = new File(["qr"], "zalo-qr.png", { type: "image/png" });

  fireEvent.change(screen.getByLabelText(/^Chọn ảnh QR liên hệ/), { target: { files: [file] } });
  fireEvent.click(screen.getByRole("button", { name: "Lưu liên hệ" }));

  await waitFor(() => expect(mocks.upload).toHaveBeenCalledWith(file, "MERCHANT_CONTACT_QR"));
  expect(mocks.create).toHaveBeenCalledWith("merchant-1", {
    merchantLocationId: "location-1",
    contactType: "ZALO_QR",
    qrMediaId: "media-qr-1",
    publicContact: true,
  });
});
