import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { AdminOperationsCollection } from "./admin-operations-collection";

const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/api-client", () => ({ requestBrowserApi: request }));

afterEach(() => {
  cleanup();
  request.mockReset();
});

function response(payload: unknown) {
  return Promise.resolve(new Response(JSON.stringify(payload), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  }));
}

it("renders listing state and only links an authoritative moderation case", async () => {
  request.mockReturnValueOnce(response({
    items: [{
      id: "item-1", name: "Máy lọc", type: "PRODUCT", listingKind: "STANDARD", merchantName: "Air Shop",
      listingStatus: "ACTIVE", moderationStatus: "PENDING", moderationCaseId: "case-1",
      updatedAt: "2026-08-29T00:00:00Z",
    }],
    total: 1,
  }));

  render(<AdminOperationsCollection resource="listings" />);

  expect(await screen.findByText("Máy lọc")).toBeInTheDocument();
  expect(screen.getByText("Chờ kiểm duyệt")).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Mở hồ sơ" })).toHaveAttribute("href", "/admin/moderation/case-1");
  expect(screen.queryByRole("button", { name: /duyệt|ẩn|xóa/i })).not.toBeInTheDocument();
});

it("renders need operations without buyer identity", async () => {
  request.mockReturnValueOnce(response({
    items: [{
      id: "need-1", title: "Cần máy lọc", type: "PRODUCT", categoryName: "Gia dụng",
      status: "ACTIVE", responseCount: 3, searchRadiusMeters: 5000,
      expiresAt: "2026-09-01T00:00:00Z", updatedAt: "2026-08-29T00:00:00Z",
    }],
    total: 1,
  }));

  render(<AdminOperationsCollection resource="needs" />);

  expect(await screen.findByText("Cần máy lọc")).toBeInTheDocument();
  expect(screen.getByText("5.000 m")).toBeInTheDocument();
  expect(screen.queryByText(/buyer/i)).not.toBeInTheDocument();
});
