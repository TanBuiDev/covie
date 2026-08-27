import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ModerationActions } from "./moderation-actions";

describe("ModerationActions", () => {
  it("renders only the server-provided moderation actions and no order controls", () => {
    render(
      <ModerationActions
        caseId="case-1"
        actions={["approve", "reject"]}
        onComplete={vi.fn()}
        onConflict={vi.fn()}
      />,
    );

    expect(screen.getByRole("button", { name: "Phê duyệt" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Từ chối" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /xác nhận đơn/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /hoàn tất đơn/i })).not.toBeInTheDocument();
  });
});
