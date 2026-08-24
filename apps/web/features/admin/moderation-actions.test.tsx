import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ModerationActions } from "./moderation-actions";
describe("ModerationActions",()=>{it("shows only pre-approval actions and never order controls",()=>{render(<ModerationActions caseId="case" caseType="PRE_APPROVAL" status="OPEN" onAction={vi.fn()}/>);expect(screen.getByText("approve")).toBeInTheDocument();expect(screen.getByText("reject")).toBeInTheDocument();expect(screen.queryByText("confirm")).not.toBeInTheDocument();expect(screen.queryByText("complete")).not.toBeInTheDocument();});});
