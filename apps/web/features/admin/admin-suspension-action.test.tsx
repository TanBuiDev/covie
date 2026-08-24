import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { AdminSuspensionAction } from "./admin-suspension-action";
describe("AdminSuspensionAction",()=>{it("requires a reason to suspend and permits restore without one",()=>{const submit=vi.fn().mockResolvedValue(undefined);const {rerender}=render(<AdminSuspensionAction status="ACTIVE" onSubmit={submit}/>);expect(screen.getByText("Suspend")).toBeDisabled();fireEvent.change(screen.getByLabelText("Administrative reason"),{target:{value:"policy"}});expect(screen.getByText("Suspend")).toBeEnabled();rerender(<AdminSuspensionAction status="SUSPENDED" onSubmit={submit}/>);expect(screen.getByText("Restore")).toBeEnabled();});});
