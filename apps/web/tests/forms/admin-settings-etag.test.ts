import { describe, expect, it } from "vitest";
import { formatSettingEtag } from "../../features/admin/admin-settings-screen";

describe("admin setting ETags", () => {
  it("uses the API version precondition format", () => {
    expect(formatSettingEtag(0)).toBe('"v0"');
    expect(formatSettingEtag(17)).toBe('"v17"');
  });
});
