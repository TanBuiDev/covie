import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { CovieApiError } from "@/lib/api/api-error";

const { createSavedAddress, updateSavedAddress } = vi.hoisted(() => ({
  createSavedAddress: vi.fn(),
  updateSavedAddress: vi.fn(),
}));

vi.mock("@/features/profile/address-api", () => ({
  createSavedAddress,
  updateSavedAddress,
}));

import { AddressForm } from "@/features/profile/address-form";

describe("AddressForm", () => {
  beforeEach(() => {
    createSavedAddress.mockReset();
    updateSavedAddress.mockReset();
  });

  it("shows backend field errors on the matching address field", async () => {
    createSavedAddress.mockRejectedValueOnce(new CovieApiError(422, "VALIDATION_FAILED", "invalid", undefined, { label: ["REQUIRED"] }));
    render(<AddressForm />);

    fireEvent.change(screen.getByLabelText("Tên gợi nhớ"), { target: { value: "Nhà" } });
    fireEvent.change(screen.getByLabelText("Địa chỉ"), { target: { value: "TP.HCM" } });
    fireEvent.change(screen.getByLabelText("Vĩ độ"), { target: { value: "10.77" } });
    fireEvent.change(screen.getByLabelText("Kinh độ"), { target: { value: "106.69" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu địa chỉ" }));

    await waitFor(() => expect(screen.getByText("Trường này là bắt buộc.")).toBeInTheDocument());
  });

  it("marks an address as default when selected", async () => {
    createSavedAddress.mockResolvedValueOnce({ id: "address-1" });
    render(<AddressForm />);
    fireEvent.change(screen.getByLabelText("Tên gợi nhớ"), { target: { value: "Nhà" } });
    fireEvent.change(screen.getByLabelText("Địa chỉ"), { target: { value: "TP.HCM" } });
    fireEvent.change(screen.getByLabelText("Vĩ độ"), { target: { value: "10.77" } });
    fireEvent.change(screen.getByLabelText("Kinh độ"), { target: { value: "106.69" } });
    fireEvent.click(screen.getByLabelText(/Đặt làm địa chỉ mặc định/));
    fireEvent.click(screen.getByRole("button", { name: "Lưu địa chỉ" }));
    await waitFor(() => expect(createSavedAddress).toHaveBeenCalledWith(expect.objectContaining({ defaultAddress: true })));
  });
});
