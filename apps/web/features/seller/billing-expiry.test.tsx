import { act, cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { BillingExpiry, BillingRemainingDays } from "./billing-expiry";

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime("2030-01-01T00:00:00Z");
});
afterEach(() => { cleanup(); vi.useRealTimers(); });

it("shows advisory remaining24h periods without inventing validity at or after expiry", () => {
  const { rerender } = render(<BillingRemainingDays expiresAt="2030-01-02T00:00:01Z" />);
  expect(screen.getByText(/Còn khoảng 2 ngày/)).toBeInTheDocument();
  rerender(<BillingRemainingDays expiresAt="2030-01-01T00:00:00Z" />);
  act(() => { vi.runOnlyPendingTimers(); });
  expect(screen.getByText(/Còn khoảng 0 ngày/)).toBeInTheDocument();
  rerender(<BillingRemainingDays expiresAt="2029-12-31T00:00:00Z" />);
  expect(screen.getByText(/Còn khoảng 0 ngày/)).toBeInTheDocument();
  rerender(<BillingRemainingDays expiresAt="invalid" />);
  expect(screen.queryByText(/Còn khoảng/)).not.toBeInTheDocument();
});

it("keeps ticks non-live inside the actual quote status ancestor and announces expiry once", () => {
  render(<p role="status"><BillingExpiry expiresAt="2030-01-01T00:01:00Z" label="Báo giá còn hiệu lực" /></p>);
  const timer = screen.getByRole("timer", { name: "Báo giá còn hiệu lực" });
  expect(timer).toHaveAttribute("aria-live", "off");
  expect(timer.closest("[role='status']")).toBeInTheDocument();
  expect(timer).toHaveTextContent("01:00");
  act(() => { vi.advanceTimersByTime(2000); });
  expect(timer).toHaveTextContent("00:58");
  act(() => { vi.advanceTimersByTime(58000); });
  expect(timer).toHaveTextContent("00:00");
  expect(screen.getAllByText(/Thời hạn hiển thị đã hết/)).toHaveLength(1);
  expect(vi.getTimerCount()).toBe(0);
});

it("does not invent a valid deadline and releases the timer when unmounted", () => {
  const { unmount, rerender } = render(<BillingExpiry expiresAt="invalid" label="Link thanh toán còn hiệu lực" />);
  expect(screen.queryByRole("timer")).not.toBeInTheDocument();
  expect(screen.getByText(/Chưa xác định được thời hạn/)).toBeInTheDocument();
  expect(vi.getTimerCount()).toBe(0);
  rerender(<BillingExpiry expiresAt="2030-01-01T00:01:00Z" label="Link thanh toán còn hiệu lực" />);
  expect(vi.getTimerCount()).toBe(1);
  unmount();
  expect(vi.getTimerCount()).toBe(0);
});

it("resolves an already-expired replacement deadline without retaining a stale positive countdown", () => {
  const { rerender } = render(<BillingExpiry expiresAt={undefined} label="Link thanh toán còn hiệu lực" />);
  act(() => { vi.advanceTimersByTime(120000); });
  rerender(<BillingExpiry expiresAt="2030-01-01T00:00:30Z" label="Link thanh toán còn hiệu lực" />);
  act(() => { vi.runOnlyPendingTimers(); });
  expect(screen.getByRole("timer")).toHaveTextContent("00:00");
  expect(screen.getByText(/Thời hạn hiển thị đã hết/)).toBeInTheDocument();
  expect(vi.getTimerCount()).toBe(0);
});
