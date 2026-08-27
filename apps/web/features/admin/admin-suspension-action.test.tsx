import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { AdminSuspensionAction } from "./admin-suspension-action";

describe("AdminSuspensionAction", () => {
  it("requires a reason to suspend and permits restore without one", () => {
    const submit = vi.fn().mockResolvedValue(undefined);
    const { rerender } = render(<AdminSuspensionAction status="ACTIVE" onSubmit={submit} />);

    fireEvent.click(screen.getByRole("button", { name: "Tạm ngưng" }));
    expect(screen.getByRole("button", { name: "Xác nhận" })).toBeDisabled();
    fireEvent.change(screen.getByLabelText("Lý do"), { target: { value: "policy" } });
    expect(screen.getByRole("button", { name: "Xác nhận" })).toBeEnabled();

    fireEvent.click(screen.getByRole("button", { name: "Huỷ" }));
    rerender(<AdminSuspensionAction status="SUSPENDED" onSubmit={submit} />);
    fireEvent.click(screen.getByRole("button", { name: "Khôi phục" }));
    expect(screen.getByRole("button", { name: "Xác nhận" })).toBeEnabled();
  });
});
