import { render, screen } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { GoogleAuthProvider } from "@/features/auth/google-auth-provider";
vi.mock("@/features/auth/google-auth-status",()=>({getGoogleAuthStatus:async()=>true}));
import OnboardingPasswordPage from "../../app/(auth)/onboarding/password/page";
import ResetPasswordPage from "../../app/(auth)/reset-password/page";

afterEach(() => document.body.replaceChildren());

it("renders the onboarding password flow", () => {
  render(<OnboardingPasswordPage />);

  expect(screen.getByRole("heading", { name: "Đặt mật khẩu" })).toBeInTheDocument();
  expect(screen.getByLabelText("Mật khẩu mới")).toBeInTheDocument();
});

it("renders the reset password flow with a configured recovery restart link", async () => {
  render(<GoogleAuthProvider><ResetPasswordPage /></GoogleAuthProvider>);

  expect(screen.getByRole("heading", { name: "Đặt lại mật khẩu" })).toBeInTheDocument();
  expect(await screen.findByRole("link", { name: "Tiếp tục với Google" })).toHaveAttribute(
    "href",
    "/api/v1/auth/google/start?intent=PASSWORD_RESET",
  );
});
