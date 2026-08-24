import { describe, expect, it, vi } from "vitest";
import { applyFieldErrors } from "@/lib/forms/apply-field-errors";

describe("applyFieldErrors", () => {
  it("maps stable backend fields to form fields", () => {
    const setError = vi.fn();
    const formErrors = applyFieldErrors({ title: ["REQUIRED"] }, { title: "title" }, setError);
    expect(setError).toHaveBeenCalledWith("title", { type: "server", message: "Trường này là bắt buộc." });
    expect(formErrors).toEqual([]);
  });

  it("returns unknown fields for a form-level error surface", () => {
    const setError = vi.fn();
    expect(applyFieldErrors({ serverOnly: ["INVALID"] }, { title: "title" }, setError)).toEqual(["serverOnly: INVALID"]);
    expect(setError).not.toHaveBeenCalled();
  });
});
