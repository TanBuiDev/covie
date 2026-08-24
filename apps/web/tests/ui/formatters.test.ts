import { describe, expect, it } from "vitest";
import { formatMoney, negotiatedShippingCopy } from "@/lib/format/money";
import { countdownPresentation } from "@/lib/format/time";
import { contentTypeCopy, statusCopy } from "@/lib/format/status-copy";

describe("presentation formatters", () => {
  it("formats money and negotiated shipping without inventing a zero fee", () => {
    expect(formatMoney({ amount: 125000, currency: "VND" })).toContain("125");
    expect(negotiatedShippingCopy()).toBe("Thỏa thuận với người bán");
  });

  it("uses safe fallback copy for future enum values", () => {
    expect(statusCopy("SOME_FUTURE_STATE")).toBe("Trạng thái mới");
    expect(contentTypeCopy("SOME_FUTURE_TYPE")).toBe("Nội dung");
  });

  it("treats a reached deadline as presentation-only refetch state", () => {
    expect(countdownPresentation("2026-08-24T10:00:00Z", Date.parse("2026-08-24T10:00:01Z"))).toEqual({
      expired: true,
      urgent: true,
      label: "Đã tới hạn, đang cập nhật...",
    });
  });
});
