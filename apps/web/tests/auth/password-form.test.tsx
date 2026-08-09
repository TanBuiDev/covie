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
