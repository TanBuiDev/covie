import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SettingsEditor } from "./settings-editor";

describe("SettingsEditor", () => {
  it("renders an allowlisted setting definition and its default", () => {
    render(<SettingsEditor definitions={[{ key: "moderation.mode", valueType: "ENUM", defaultValue: "AUTO_PUBLISH", allowedScopes: ["GLOBAL"] }]} />);
    expect(screen.getByText("moderation.mode")).toBeInTheDocument();
    expect(screen.getByDisplayValue("AUTO_PUBLISH")).toBeInTheDocument();
    expect(screen.getByText(/Mặc định: AUTO_PUBLISH/)).toBeInTheDocument();
  });
});
