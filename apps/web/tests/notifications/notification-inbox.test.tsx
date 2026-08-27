import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { NotificationInbox } from "@/features/notifications/notification-inbox";

const mocks = vi.hoisted(() => ({
  inbox: vi.fn(),
  markRead: vi.fn(),
  markAllRead: vi.fn(),
  push: vi.fn(),
}));

vi.mock("@/features/notifications/notification-api", () => ({
  inbox: mocks.inbox,
  markRead: mocks.markRead,
  markAllRead: mocks.markAllRead,
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: mocks.push }) }));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

it("marks a notification read without inventing a fallback destination", async () => {
  mocks.inbox.mockResolvedValue({ items: [{ id: "n1", type: "ORDER_CONFIRMED", read: false, createdAt: "2026-01-01T00:00:00Z", data: {} }] });
  mocks.markRead.mockResolvedValue(undefined);
  render(<NotificationInbox />);

  const notification = await screen.findByRole("button", { name: /Đơn hàng đã được xác nhận/ });
  fireEvent.click(notification);

  await waitFor(() => expect(mocks.markRead).toHaveBeenCalledWith("n1"));
  expect(mocks.push).not.toHaveBeenCalled();
});

it("navigates only to the safe path supplied by the backend payload", async () => {
  mocks.inbox.mockResolvedValue({ items: [{ id: "n2", type: "ORDER_CONFIRMED", read: false, createdAt: "2026-01-01T00:00:00Z", data: { deepLink: "/orders/o-2" } }] });
  mocks.markRead.mockResolvedValue(undefined);
  render(<NotificationInbox />);

  fireEvent.click(await screen.findByRole("button", { name: /Đơn hàng đã được xác nhận.*Mở chi tiết/ }));

  await waitFor(() => expect(mocks.push).toHaveBeenCalledWith("/orders/o-2"));
  expect(mocks.markRead).toHaveBeenCalledWith("n2");
});
