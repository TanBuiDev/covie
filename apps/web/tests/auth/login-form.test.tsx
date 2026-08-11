import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { GoogleLoginButton } from "../../features/auth/google-login-button";
import { LoginForm } from "../../features/auth/login-form";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

it("uses PASSWORD_RESET intent for the Google recovery link", () => {
  render(<GoogleLoginButton intent="PASSWORD_RESET" />);

  expect(screen.getByRole("link", { name: "Tiếp tục với Google" })).toHaveAttribute(
    "href",
    "/api/v1/auth/google/start?intent=PASSWORD_RESET",
  );
});

it("maps invalid credentials by error code without showing the backend message", async () => {
  vi.stubGlobal("fetch", vi.fn()
    .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: "X-CSRF-Token", token: "token" })))
    .mockResolvedValueOnce(new Response(
      JSON.stringify({ error: { code: "INVALID_CREDENTIALS", message: "leaked backend detail" } }),
      { status: 401 },
    )));
  const navigate = vi.fn();
  render(<LoginForm onNavigate={navigate} />);

  fireEvent.change(screen.getByLabelText("Email"), { target: { value: "person@example.com" } });
  fireEvent.change(screen.getByLabelText("Mật khẩu"), { target: { value: "password" } });
  fireEvent.click(screen.getByRole("button", { name: "Đăng nhập" }));

  await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Email hoặc mật khẩu không đúng."));
  expect(screen.queryByText("leaked backend detail")).not.toBeInTheDocument();
  expect(navigate).not.toHaveBeenCalled();
});
