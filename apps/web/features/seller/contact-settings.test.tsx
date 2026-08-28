import type { components } from "@covie/api-client";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { MerchantContactSettings } from "./contact-settings";

type MerchantContact = components["schemas"]["MerchantContactResponse"];

const mocks = vi.hoisted(() => ({
  refresh: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
  remove: vi.fn(),
  upload: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: mocks.refresh }),
}));

vi.mock("./contact-api", () => ({
  createMerchantContact: mocks.create,
  updateMerchantContact: mocks.update,
  deleteMerchantContact: mocks.remove,
}));

vi.mock("@/features/media/direct-image-upload", () => ({
  uploadImage: mocks.upload,
}));

beforeEach(() => {
  mocks.refresh.mockReset();
  mocks.create.mockReset().mockResolvedValue({});
  mocks.update.mockReset().mockResolvedValue({});
  mocks.remove.mockReset().mockResolvedValue(undefined);
  mocks.upload.mockReset().mockResolvedValue({ mediaId: "media-qr-1" });
});

it("renders existing versioned contact channels as editable", () => {
  const contacts: MerchantContact[] = [
    { id: "zalo-1", merchantLocationId: "location-1", contactType: "ZALO_LINK", value: "https://zalo.me/shop", publicContact: true, version: 2 },
    { id: "messenger-1", merchantLocationId: "location-1", contactType: "MESSENGER_LINK", value: "https://m.me/shop", publicContact: true, version: 3 },
    { id: "qr-1", merchantLocationId: "location-1", contactType: "ZALO_QR", qrMediaId: "media-existing", publicContact: true, version: 1 },
  ];

  render(<MerchantContactSettings merchantId="merchant-1" merchantLocationId="location-1" contacts={contacts} />);

  expect(screen.getByLabelText(/^Zalo link/)).not.toHaveAttribute("readonly");
  expect(screen.getByLabelText(/^Messenger link/)).not.toHaveAttribute("readonly");
  expect(screen.getByText("Ảnh QR liên hệ đã được lưu. Chọn ảnh mới để thay thế.")).toBeInTheDocument();
  expect(screen.getByLabelText(/^Thay ảnh QR liên hệ/)).toBeInTheDocument();
});

it("updates an existing contact with its exact version", async () => {
  const contacts: MerchantContact[] = [{
    id: "zalo-1", merchantLocationId: "location-1", contactType: "ZALO_LINK",
    value: "https://zalo.me/shop", publicContact: true, version: 7,
  }];
  render(<MerchantContactSettings merchantId="merchant-1" merchantLocationId="location-1" contacts={contacts} />);

  fireEvent.change(screen.getByLabelText(/^Zalo link/), { target: { value: "https://zalo.me/new-shop" } });
  fireEvent.click(screen.getByRole("button", { name: "Lưu liên hệ" }));

  await waitFor(() => expect(mocks.update).toHaveBeenCalledWith("merchant-1", "zalo-1", 7, {
    merchantLocationId: "location-1",
    contactType: "ZALO_LINK",
    value: "https://zalo.me/new-shop",
    publicContact: true,
  }));
  expect(mocks.create).not.toHaveBeenCalled();
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
