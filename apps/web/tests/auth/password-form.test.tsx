import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { PasswordForm } from "../../features/auth/password-form";
import { CovieApiError } from "../../lib/api/api-error";

it("renders Google reset validation errors from the newPassword field", async () => {
  const submitPassword = vi.fn().mockRejectedValue(new CovieApiError(
    422,
    "VALIDATION_FAILED",
    "backend detail",
    undefined,
    { newPassword: ["Mật khẩu phải có ít nhất 10 ký tự."] },
  ));
  render(<PasswordForm fieldName="newPassword" submitPassword={submitPassword} />);

  fireEvent.change(screen.getByLabelText("Mật khẩu mới"), { target: { value: "short" } });
  fireEvent.click(screen.getByRole("button", { name: "Lưu mật khẩu" }));

  await waitFor(() => expect(screen.getByText("Mật khẩu phải có ít nhất 10 ký tự.")).toBeInTheDocument());
});

it("shows mismatch indicator and prevents submission when confirm password differs", async () => {
  const submitPassword = vi.fn().mockResolvedValue(undefined);
  render(<PasswordForm fieldName="newPassword" submitPassword={submitPassword} />);

  fireEvent.change(screen.getByLabelText("Mật khẩu mới"), { target: { value: "SecurePass123!" } });
  fireEvent.change(screen.getByLabelText("Xác nhận mật khẩu mới"), { target: { value: "DifferentPass123!" } });

  expect(screen.getByText("Mật khẩu xác nhận chưa khớp")).toBeInTheDocument();

  fireEvent.click(screen.getByRole("button", { name: "Lưu mật khẩu" }));
  expect(screen.getByRole("alert")).toHaveTextContent("Mật khẩu xác nhận không khớp.");
  expect(submitPassword).not.toHaveBeenCalled();
});

it("shows match indicator and submits successfully when confirm password matches", async () => {
  const submitPassword = vi.fn().mockResolvedValue(undefined);
  const onComplete = vi.fn();
  render(<PasswordForm fieldName="newPassword" submitPassword={submitPassword} onComplete={onComplete} />);

  fireEvent.change(screen.getByLabelText("Mật khẩu mới"), { target: { value: "SecurePass123!" } });
  fireEvent.change(screen.getByLabelText("Xác nhận mật khẩu mới"), { target: { value: "SecurePass123!" } });

  expect(screen.getByText("Mật khẩu xác nhận khớp")).toBeInTheDocument();

  fireEvent.click(screen.getByRole("button", { name: "Lưu mật khẩu" }));
  await waitFor(() => expect(submitPassword).toHaveBeenCalledWith("SecurePass123!"));
  expect(onComplete).toHaveBeenCalledWith("/");
});

it("never hides validation errors for an unexpected backend field", async () => {
  const submitPassword = vi.fn().mockRejectedValue(new CovieApiError(422, "VALIDATION_FAILED", "Invalid", undefined, { other: ["Không thể chấp nhận mật khẩu này."] }));
  render(<PasswordForm submitPassword={submitPassword} />);
  fireEvent.change(screen.getByLabelText("Mật khẩu mới"), { target: { value: "SecurePass123!" } });
  fireEvent.click(screen.getByRole("button", { name: "Lưu mật khẩu" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Không thể chấp nhận mật khẩu này.");
});

it("explains that Google verification must be restarted when the recovery ticket is unavailable", async () => {
  const submitPassword = vi.fn().mockRejectedValue(new CovieApiError(409, "PASSWORD_RECOVERY_NOT_AVAILABLE", "Unavailable"));
  render(<PasswordForm submitPassword={submitPassword} />);
  fireEvent.change(screen.getByLabelText("Mật khẩu mới"), { target: { value: "SecurePass123!" } });
  fireEvent.click(screen.getByRole("button", { name: "Lưu mật khẩu" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Hãy xác thực Google lại");
});
