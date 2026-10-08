import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PasswordInput } from "../../components/ui/password-input";
import { PasswordStrengthMeter } from "../../features/auth/password-strength-meter";
import { LoginForm } from "../../features/auth/login-form";

describe("PasswordInput component", () => {
  it("toggles password visibility between password and text types", () => {
    render(<PasswordInput id="test-pwd" name="pwd" defaultValue="secret123" />);

    const input = screen.getByDisplayValue("secret123") as HTMLInputElement;
    expect(input.type).toBe("password");

    const toggleBtn = screen.getByRole("button", { name: "Hiện mật khẩu" });
    fireEvent.click(toggleBtn);

    expect(input.type).toBe("text");
    expect(screen.getByRole("button", { name: "Ẩn mật khẩu" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Ẩn mật khẩu" }));
    expect(input.type).toBe("password");
  });
});

describe("PasswordStrengthMeter component", () => {
  it("renders checklist items for empty password", () => {
    render(<PasswordStrengthMeter password="" />);
    expect(screen.getByText("Tối thiểu 10 ký tự")).toBeInTheDocument();
    expect(screen.getByText("Chữ hoa & chữ thường (a-z, A-Z)")).toBeInTheDocument();
  });

  it("evaluates strong password with all criteria satisfied", () => {
    render(<PasswordStrengthMeter password="CovieSuper@2026!" />);
    expect(screen.getByText("Rất an toàn")).toBeInTheDocument();
  });

  it("evaluates weak password when short and missing criteria", () => {
    render(<PasswordStrengthMeter password="abc" />);
    expect(screen.getByText("Chưa đủ điều kiện")).toBeInTheDocument();
  });
});

describe("LoginForm contextual banner & remember me", () => {
  it("displays contextual guidance banner when returnTo is seller", () => {
    render(<LoginForm returnTo="/seller/dashboard" />);
    expect(screen.getByRole("status")).toHaveTextContent(
      "Bạn đang truy cập Kênh Người Bán. Vui lòng đăng nhập để tiếp tục quản lý gian hàng.",
    );
  });

  it("renders remember me checkbox", () => {
    render(<LoginForm />);
    const checkbox = screen.getByLabelText("Ghi nhớ đăng nhập") as HTMLInputElement;
    expect(checkbox).toBeInTheDocument();
    expect(checkbox.checked).toBe(false);

    fireEvent.click(checkbox);
    expect(checkbox.checked).toBe(true);
  });
});
