import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { createDeliveryCoverage, listLocationAreas } = vi.hoisted(() => ({
  createDeliveryCoverage: vi.fn(),
  listLocationAreas: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: vi.fn() }),
}));

vi.mock("../../features/shipping/shipping-api", () => ({
  createDeliveryCoverage,
  deleteDeliveryCoverage: vi.fn(),
  listLocationAreas,
  updateDeliveryCoverage: vi.fn(),
}));

import { CoverageForm } from "@/features/shipping/coverage-form";

describe("delivery coverage configuration", () => {
  beforeEach(() => {
    createDeliveryCoverage.mockReset();
    listLocationAreas.mockReset();
    listLocationAreas.mockResolvedValue([{ id: "area-1", name: "Khu A", status: "ACTIVE" }]);
  });

  it("supports selected backend location areas without inventing a third coverage mode", async () => {
    render(<CoverageForm merchantId="merchant-1" locationId="location-1" />);

    fireEvent.change(screen.getByLabelText("Áp dụng cho"), { target: { value: "PRODUCT_DELIVERY" } });
    fireEvent.click(screen.getByRole("button", { name: "Khu vực" }));
    await waitFor(() => expect(screen.getByText("Khu A")).toBeInTheDocument());
    fireEvent.click(screen.getByLabelText("Khu A"));

    expect(screen.getByRole("button", { name: "Lưu khu vực" })).toBeEnabled();
    expect(screen.queryByRole("button", { name: /chế độ|kiểu phạm vi thứ ba/i })).not.toBeInTheDocument();
  });
});
