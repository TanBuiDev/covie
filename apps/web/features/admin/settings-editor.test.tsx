import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SettingsEditor } from "./settings-editor";

describe("SettingsEditor", () => {
  it("renders an allowlisted setting definition and its default", () => {
    render(<SettingsEditor definitions={[{ key: "moderation.mode", valueType: "ENUM", defaultValue: "AUTO_PUBLISH", allowedScopes: ["GLOBAL"] }]} />);
    expect(screen.getByText("moderation.mode")).toBeInTheDocument();
    expect(screen.getByDisplayValue("AUTO_PUBLISH")).toBeInTheDocument();
    expect(screen.getByText(/Mặc định: AUTO_PUBLISH/)).toBeInTheDocument();
  });

  it("remounts the draft owner when the effective version changes", () => {
    const definition = { key: "moderation.mode", valueType: "ENUM", defaultValue: "AUTO_PUBLISH", allowedScopes: ["GLOBAL"], effectiveValue: "AUTO_PUBLISH" };
    const { rerender } = render(<SettingsEditor definitions={[definition]} onSave={async () => undefined} />);
    fireEvent.change(screen.getByDisplayValue("AUTO_PUBLISH"), { target: { value: "MANUAL_REVIEW" } });
    expect(screen.getByDisplayValue("MANUAL_REVIEW")).toBeInTheDocument();

    rerender(<SettingsEditor definitions={[{ ...definition, effectiveValue: "HYBRID" }]} onSave={async () => undefined} />);
    expect(screen.getByDisplayValue("HYBRID")).toBeInTheDocument();
    expect(screen.queryByDisplayValue("MANUAL_REVIEW")).not.toBeInTheDocument();
  });
});
