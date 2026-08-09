import { render, screen } from "@testing-library/react";
import { afterEach, expect, it } from "vitest";
import OnboardingPasswordPage from "../../app/(auth)/onboarding/password/page";
import ResetPasswordPage from "../../app/(auth)/reset-password/page";

afterEach(() => document.body.replaceChildren());

it("renders the onboarding password flow", () => {
  render(<OnboardingPasswordPage />);

  expect(screen.getByRole("heading", { name: "Đặt mật khẩu" })).toBeInTheDocument();
  expect(screen.getByLabelText("Mật khẩu mới")).toBeInTheDocument();
});

it("renders the reset password flow with a recovery restart link", () => {
  render(<ResetPasswordPage />);

  expect(screen.getByRole("heading", { name: "Đặt lại mật khẩu" })).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Tiếp tục với Google" })).toHaveAttribute(
    "href",
    "/api/v1/auth/google/start?intent=PASSWORD_RESET",
  );
});
