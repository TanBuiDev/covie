import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SettingsEditor } from "./settings-editor";

describe("SettingsEditor", () => {
  it("renders an allowlisted setting definition and its default", () => {
    render(<SettingsEditor definitions={[{ key: "moderation.mode", valueType: "ENUM", defaultValue: "AUTO_PUBLISH", allowedScopes: ["GLOBAL"] }]} />);
    expect(screen.getByText("moderation.mode")).toBeInTheDocument();
    expect(screen.getByText("AUTO_PUBLISH")).toBeInTheDocument();
  });
});
