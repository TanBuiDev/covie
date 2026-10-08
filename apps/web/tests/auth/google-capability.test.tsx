import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { GoogleAuthLink, GoogleLoginButton } from "@/features/auth/google-login-button";
import { GoogleAuthProvider } from "@/features/auth/google-auth-provider";

const mocks = vi.hoisted(()=>({status:vi.fn()}));
vi.mock("@/features/auth/google-auth-status",()=>({getGoogleAuthStatus:mocks.status}));

afterEach(()=>{cleanup();mocks.status.mockReset();});

it("does not expose an OAuth start link before configuration readiness is known", () => {
  render(<GoogleLoginButton/>);
  expect(screen.queryByRole("link",{name:"Tiếp tục với Google"})).not.toBeInTheDocument();
  expect(screen.getByRole("button",{name:"Tiếp tục với Google"})).toBeDisabled();
});

it("disables Google login, registration and recovery when backend is unconfigured", async () => {
  mocks.status.mockResolvedValue(false);
  render(<GoogleAuthProvider><GoogleLoginButton/><GoogleAuthLink intent="PASSWORD_RESET">Quên mật khẩu?</GoogleAuthLink><GoogleAuthLink>Tạo tài khoản</GoogleAuthLink></GoogleAuthProvider>);
  await screen.findByText(/Google hiện chưa được cấu hình/);
  expect(screen.getByRole("button",{name:"Tiếp tục với Google"})).toBeDisabled();
  expect(screen.queryAllByRole("link")).toHaveLength(0);
  expect(mocks.status).toHaveBeenCalledTimes(1);
});

it("enables the intended Google links only after a configured response", async () => {
  mocks.status.mockResolvedValue(true);
  render(<GoogleAuthProvider><GoogleLoginButton/><GoogleAuthLink intent="PASSWORD_RESET">Quên mật khẩu?</GoogleAuthLink><GoogleAuthLink>Tạo tài khoản</GoogleAuthLink></GoogleAuthProvider>);
  expect(await screen.findByRole("link",{name:"Tiếp tục với Google"})).toHaveAttribute("href","/api/v1/auth/google/start?intent=LOGIN");
  expect(screen.getByRole("link",{name:"Quên mật khẩu?"})).toHaveAttribute("href","/api/v1/auth/google/start?intent=PASSWORD_RESET");
  expect(screen.getByRole("link",{name:"Tạo tài khoản"})).toHaveAttribute("href","/api/v1/auth/google/start?intent=LOGIN");
});

it("keeps OAuth disabled on capability errors and can retry without showing backend details", async () => {
  mocks.status.mockRejectedValueOnce(new Error("private backend detail")).mockResolvedValueOnce(true);
  render(<GoogleAuthProvider><GoogleLoginButton/></GoogleAuthProvider>);
  await screen.findByText("Chưa kiểm tra được đăng nhập Google. Vui lòng thử lại.");
  expect(screen.queryByText("private backend detail")).not.toBeInTheDocument();
  expect(screen.getByRole("button",{name:"Tiếp tục với Google"})).toBeDisabled();
  fireEvent.click(screen.getByRole("button",{name:"Thử lại Google"}));
  await screen.findByRole("link",{name:"Tiếp tục với Google"});
  await waitFor(()=>expect(mocks.status).toHaveBeenCalledTimes(2));
});
