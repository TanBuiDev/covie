import type { components } from "@covie/api-client";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { CoverageForm } from "./coverage-form";

type Coverage = components["schemas"]["DeliveryCoverageResponse"];

const mocks = vi.hoisted(() => ({
  refresh: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
  remove: vi.fn(),
  listAreas: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: mocks.refresh }),
}));

vi.mock("@/features/shipping/shipping-api", () => ({
  createDeliveryCoverage: mocks.create,
  updateDeliveryCoverage: mocks.update,
  deleteDeliveryCoverage: mocks.remove,
  listLocationAreas: mocks.listAreas,
}));

beforeEach(() => {
  mocks.refresh.mockReset();
  mocks.create.mockReset().mockResolvedValue({});
  mocks.update.mockReset().mockResolvedValue({});
  mocks.remove.mockReset().mockResolvedValue(undefined);
  mocks.listAreas.mockReset().mockResolvedValue([
    { id: "area-1", name: "The Origami" },
    { id: "area-2", name: "The Rainbow" },
  ]);
});

it("uses the frozen area facet while requiring an explicit backend coverage type", async () => {
  render(<CoverageForm merchantId="merchant-1" locationId="location-1" />);

  fireEvent.change(screen.getByLabelText(/^Loại coverage/), { target: { value: "PRODUCT_DELIVERY" } });
  fireEvent.click(screen.getByRole("button", { name: "Khu vực" }));
  fireEvent.click(await screen.findByLabelText("The Origami"));
  fireEvent.click(screen.getByRole("button", { name: "Lưu khu vực" }));

  await waitFor(() => expect(mocks.create).toHaveBeenCalledTimes(1));
  expect(mocks.create).toHaveBeenCalledWith("merchant-1", "location-1", {
    coverageType: "PRODUCT_DELIVERY",
    radiusEnabled: false,
    radiusMeters: undefined,
    areaEnabled: true,
    locationAreaIds: ["area-1"],
    status: "ACTIVE",
  });
});

it("preserves the other backend facet when editing a combined coverage record", async () => {
  const coverage: Coverage = {
    id: "coverage-1",
    coverageType: "PRODUCT_DELIVERY",
    radiusEnabled: true,
    radiusMeters: 3000,
    areaEnabled: true,
    locationAreaIds: ["area-1"],
    status: "ACTIVE",
    version: 4,
  };

  render(<CoverageForm merchantId="merchant-1" locationId="location-1" coverage={coverage} />);
  fireEvent.change(screen.getByLabelText(/^Bán kính$/), { target: { value: "5000" } });
  fireEvent.click(screen.getByRole("button", { name: "Lưu phạm vi" }));

  await waitFor(() => expect(mocks.update).toHaveBeenCalledTimes(1));
  expect(mocks.update).toHaveBeenCalledWith("merchant-1", "location-1", "coverage-1", 4, {
    coverageType: "PRODUCT_DELIVERY",
    radiusEnabled: true,
    radiusMeters: 5000,
    areaEnabled: true,
    locationAreaIds: ["area-1"],
    status: "ACTIVE",
  });
});
