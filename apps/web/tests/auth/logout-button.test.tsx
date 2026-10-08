import { act, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { LogoutButton } from "@/features/profile/logout-button";

const mocks = vi.hoisted(() => ({ logout: vi.fn(), replace: vi.fn() }));
vi.mock("@/features/auth/auth-api", () => ({ logout: mocks.logout }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: mocks.replace }) }));
beforeEach(() => vi.clearAllMocks());

it("sends only one logout for clicks in the same batch", () => {
  mocks.logout.mockReturnValue(new Promise(() => {}));
  render(<LogoutButton />);
  const button = screen.getByRole("button", { name: "Đăng xuất" });
  act(() => { button.click(); button.click(); });
  expect(mocks.logout).toHaveBeenCalledTimes(1);
  expect(screen.getByRole("button", { name: "Đang đăng xuất…" })).toBeDisabled();
});

it("allows retry after a failed logout without exposing diagnostics", async () => {
  mocks.logout.mockRejectedValueOnce(new Error("private diagnostic"));
  mocks.logout.mockImplementationOnce(async (navigate: (path: string) => void) => navigate("/login"));
  render(<LogoutButton />);
  fireEvent.click(screen.getByRole("button", { name: "Đăng xuất" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Không thể đăng xuất. Vui lòng thử lại.");
  expect(screen.queryByText("private diagnostic")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Đăng xuất" }));
  expect(mocks.logout).toHaveBeenCalledTimes(2);
  expect(mocks.replace).toHaveBeenCalledWith("/login");
});
