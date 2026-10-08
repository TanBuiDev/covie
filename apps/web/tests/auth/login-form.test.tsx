import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { renderToString } from "react-dom/server";
import { GoogleLoginButton } from "../../features/auth/google-login-button";
import { LoginForm } from "../../features/auth/login-form";
import { GoogleAuthProvider } from "@/features/auth/google-auth-provider";
vi.mock("@/features/auth/google-auth-status",()=>({getGoogleAuthStatus:async()=>true}));

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

it.each(["/me", "//evil.test"])("uses a safe destination after successful email login: %s", async (returnTo) => {
  vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    return url.includes("csrf")
      ? new Response(JSON.stringify({ headerName: "X-CSRF-Token", token: "token" }))
      : new Response(null, { status: 204 });
  }));
  const navigate = vi.fn();
  render(<LoginForm returnTo={returnTo} onNavigate={navigate} />);
  fireEvent.change(screen.getByLabelText("Email"), { target: { value: "person@example.com" } });
  fireEvent.change(screen.getByLabelText("Mật khẩu"), { target: { value: "password" } });
  fireEvent.click(screen.getByRole("button", { name: "Đăng nhập" }));
  await waitFor(() => expect(navigate).toHaveBeenCalledWith(returnTo === "/me" ? "/me" : "/"));
});

it("uses PASSWORD_RESET intent for the configured Google recovery link", async () => {
  render(<GoogleAuthProvider><GoogleLoginButton intent="PASSWORD_RESET" /></GoogleAuthProvider>);

  expect(await screen.findByRole("link", { name: "Tiếp tục với Google" })).toHaveAttribute(
    "href",
    "/api/v1/auth/google/start?intent=PASSWORD_RESET",
  );
});

it("uses a POST fallback so pre-hydration submit never puts credentials in the URL", () => {
  render(<LoginForm />);

  expect(screen.getByRole("button", { name: "Đăng nhập" }).closest("form")).toHaveAttribute("method", "post");
});

it("keeps submit disabled in server HTML until the login handler is hydrated", () => {
  expect(renderToString(<LoginForm />)).toContain("disabled");
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
