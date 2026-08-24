import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { createDeliveryCoverage, listLocationAreas } = vi.hoisted(() => ({
  createDeliveryCoverage: vi.fn(),
  listLocationAreas: vi.fn(),
}));

vi.mock("@/features/shipping/shipping-api", () => ({
  createDeliveryCoverage,
  listLocationAreas,
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
    await waitFor(() => expect(screen.getByText("Khu A")).toBeInTheDocument());
    fireEvent.click(screen.getByLabelText("Khu vực được chọn"));
    fireEvent.click(screen.getByLabelText("Khu A"));
    expect(screen.getByRole("button", { name: "Lưu vùng phục vụ" })).toBeEnabled();
  });
});
