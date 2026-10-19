import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { CovieApiError } from "@/lib/api/api-error";
import { SellerBarcodeLookup, type BarcodeReferenceData } from "./seller-barcode-lookup";

const api = vi.hoisted(() => ({
  lookupBarcode: vi.fn<(gtin: string) => Promise<BarcodeReferenceData>>(),
}));

vi.mock("./catalog-api", () => ({
  lookupBarcode: api.lookupBarcode,
}));

const mockCompleteProduct: BarcodeReferenceData = {
  gtin: "8934673501354",
  gtin14: "08934673501354",
  productName: "Sữa tươi tiệt trùng Vinamilk ít đường 220ml",
  brand: "Vinamilk",
  quantity: "220ml",
  categoryLabel: "Sữa tươi tiệt trùng",
  marketCountry: "VN",
  marketSignal: "OFF_MARKET_TAG",
  source: "OPEN_FOOD_FACTS",
  sourceRecordUrl: "https://world.openfoodfacts.org/product/8934673501354",
  licenseId: "ODbL-1.0",
  qualityStatus: "COMMUNITY_UNVERIFIED",
  needsReview: true,
  attributionNotice: "Dữ liệu tham khảo từ Open Food Facts (ODbL 1.0).",
};

beforeEach(() => {
  api.lookupBarcode.mockReset();
});

afterEach(() => {
  cleanup();
});

describe("SellerBarcodeLookup Unit Tests", () => {
  it("1. Tra cứu GTIN hợp lệ và giữ nguyên số 0 ở đầu", async () => {
    api.lookupBarcode.mockResolvedValue(mockCompleteProduct);

    render(<SellerBarcodeLookup />);
    const input = screen.getByLabelText("Mã vạch GTIN/EAN");
    fireEvent.change(input, { target: { value: "  08934673501354  " } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu" }));

    await waitFor(() => {
      expect(api.lookupBarcode).toHaveBeenCalledWith("08934673501354");
    });
    expect(screen.getByDisplayValue("Sữa tươi tiệt trùng Vinamilk ít đường 220ml")).toBeInTheDocument();
  });

  it("2. Báo lỗi khi mã GTIN sai định dạng hoặc độ dài", async () => {
    render(<SellerBarcodeLookup />);
    const input = screen.getByLabelText("Mã vạch GTIN/EAN");

    // Quá ngắn
    fireEvent.change(input, { target: { value: "12345" } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu" }));
    expect(screen.getByText("Mã vạch phải gồm 8, 12, 13 hoặc 14 chữ số hợp lệ.")).toBeInTheDocument();
    expect(api.lookupBarcode).not.toHaveBeenCalled();

    // Chứa chữ cái
    fireEvent.change(input, { target: { value: "893467350135A" } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu" }));
    expect(screen.getByText("Mã vạch phải gồm 8, 12, 13 hoặc 14 chữ số hợp lệ.")).toBeInTheDocument();
    expect(api.lookupBarcode).not.toHaveBeenCalled();
  });

  it("3. Báo lỗi khi backend trả 422 VALIDATION_FAILED (sai check digit)", async () => {
    api.lookupBarcode.mockRejectedValue(
      new CovieApiError(422, "VALIDATION_FAILED", "Mã GTIN/Barcode không hợp lệ hoặc sai số kiểm tra."),
    );

    render(<SellerBarcodeLookup />);
    fireEvent.change(screen.getByLabelText("Mã vạch GTIN/EAN"), { target: { value: "8935217400394" } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu" }));

    await waitFor(() => {
      expect(screen.getByText("Mã GTIN/Barcode không hợp lệ hoặc sai số kiểm tra.")).toBeInTheDocument();
    });
  });

  it("4. Báo trạng thái phù hợp khi backend trả 404 RESOURCE_NOT_FOUND", async () => {
    api.lookupBarcode.mockRejectedValue(
      new CovieApiError(404, "RESOURCE_NOT_FOUND", "Không tìm thấy dữ liệu tham chiếu cho mã vạch này."),
    );

    render(<SellerBarcodeLookup />);
    fireEvent.change(screen.getByLabelText("Mã vạch GTIN/EAN"), { target: { value: "4006381333931" } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu" }));

    await waitFor(() => {
      expect(screen.getByText("Chưa có thông tin tham chiếu cho mã vạch này trong hệ thống.")).toBeInTheDocument();
    });
  });

  it("5. Hiển thị đầy đủ thông tin tham khảo khi API 200", async () => {
    api.lookupBarcode.mockResolvedValue(mockCompleteProduct);

    render(<SellerBarcodeLookup />);
    fireEvent.change(screen.getByLabelText("Mã vạch GTIN/EAN"), { target: { value: "8934673501354" } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu" }));

    await waitFor(() => {
      expect(screen.getByText("Vinamilk")).toBeInTheDocument();
      expect(screen.getByText("220ml")).toBeInTheDocument();
      expect(screen.getByText("Sữa tươi tiệt trùng")).toBeInTheDocument();
      expect(screen.getByText("COMMUNITY_UNVERIFIED")).toBeInTheDocument();
      expect(screen.getByText("OFF_MARKET_TAG")).toBeInTheDocument();
      expect(screen.getByText("Dữ liệu tham khảo từ Open Food Facts (ODbL 1.0).")).toBeInTheDocument();
    });
  });

  it("6. Xử lý an toàn khi API trả thiếu các trường tuỳ chọn", async () => {
    const minimalData: BarcodeReferenceData = {
      gtin: "8934673501354",
      gtin14: "08934673501354",
      productName: "Sản phẩm tối giản",
      needsReview: false,
    };
    api.lookupBarcode.mockResolvedValue(minimalData);

    render(<SellerBarcodeLookup />);
    fireEvent.change(screen.getByLabelText("Mã vạch GTIN/EAN"), { target: { value: "8934673501354" } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu" }));

    await waitFor(() => {
      expect(screen.getByDisplayValue("Sản phẩm tối giản")).toBeInTheDocument();
    });
    expect(screen.queryByText("Thương hiệu tham khảo:")).not.toBeInTheDocument();
    expect(screen.queryByText("Quy cách tham khảo:")).not.toBeInTheDocument();
  });

  it("7. Báo lỗi gián đoạn khi API trả lỗi 500", async () => {
    api.lookupBarcode.mockRejectedValue(new CovieApiError(500, "INTERNAL_ERROR", "Internal Server Error"));

    render(<SellerBarcodeLookup />);
    fireEvent.change(screen.getByLabelText("Mã vạch GTIN/EAN"), { target: { value: "8934673501354" } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu" }));

    await waitFor(() => {
      expect(screen.getByText("Hệ thống tra cứu tạm thời gián đoạn. Vui lòng thử lại sau.")).toBeInTheDocument();
    });
  });

  it("8. Báo lỗi kết nối mạng khi network error/timeout", async () => {
    api.lookupBarcode.mockRejectedValue(new TypeError("Failed to fetch"));

    render(<SellerBarcodeLookup />);
    fireEvent.change(screen.getByLabelText("Mã vạch GTIN/EAN"), { target: { value: "8934673501354" } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu" }));

    await waitFor(() => {
      expect(screen.getByText("Lỗi kết nối mạng. Vui lòng kiểm tra kết nối và thử lại.")).toBeInTheDocument();
    });
  });

  it("9. Chống race condition khi request cũ hoàn thành sau request mới", async () => {
    let resolveFirst!: (val: BarcodeReferenceData) => void;
    let resolveSecond!: (val: BarcodeReferenceData) => void;

    api.lookupBarcode
      .mockImplementationOnce(() => new Promise((res) => { resolveFirst = res; }))
      .mockImplementationOnce(() => new Promise((res) => { resolveSecond = res; }));

    render(<SellerBarcodeLookup />);
    const input = screen.getByLabelText("Mã vạch GTIN/EAN");

    // Request 1
    fireEvent.change(input, { target: { value: "8934673501354" } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu" }));

    // Request 2
    fireEvent.change(input, { target: { value: "8935217400393" } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu" }));

    // Request 2 completes first
    resolveSecond({ ...mockCompleteProduct, productName: "Sản phẩm mới (Request 2)" });
    await waitFor(() => {
      expect(screen.getByDisplayValue("Sản phẩm mới (Request 2)")).toBeInTheDocument();
    });

    // Request 1 completes later -> must NOT overwrite result of request 2
    resolveFirst({ ...mockCompleteProduct, productName: "Sản phẩm cũ (Request 1)" });
    expect(screen.queryByDisplayValue("Sản phẩm cũ (Request 1)")).not.toBeInTheDocument();
    expect(screen.getByDisplayValue("Sản phẩm mới (Request 2)")).toBeInTheDocument();
  });

  it("10. Cleanup khi component unmount trong khi request đang chạy", async () => {
    let resolvePending!: (val: BarcodeReferenceData) => void;
    api.lookupBarcode.mockImplementation(() => new Promise((res) => { resolvePending = res; }));

    const { unmount } = render(<SellerBarcodeLookup />);
    fireEvent.change(screen.getByLabelText("Mã vạch GTIN/EAN"), { target: { value: "8934673501354" } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu" }));

    unmount();
    // Resolving after unmount must not throw or cause state update error
    resolvePending(mockCompleteProduct);
  });

  it("11. Không tự lookup khi người dùng gõ từng ký tự", () => {
    render(<SellerBarcodeLookup />);
    const input = screen.getByLabelText("Mã vạch GTIN/EAN");
    fireEvent.change(input, { target: { value: "8" } });
    fireEvent.change(input, { target: { value: "89" } });
    fireEvent.change(input, { target: { value: "893" } });
    expect(api.lookupBarcode).not.toHaveBeenCalled();
  });

  it("12. Nhấn Enter trong ô GTIN kích hoạt lookup nhưng không submit form Catalog cha", async () => {
    api.lookupBarcode.mockResolvedValue(mockCompleteProduct);
    const formSubmit = vi.fn((e) => e.preventDefault());

    render(
      <form onSubmit={formSubmit}>
        <SellerBarcodeLookup />
      </form>,
    );

    const input = screen.getByLabelText("Mã vạch GTIN/EAN");
    fireEvent.change(input, { target: { value: "8934673501354" } });
    fireEvent.keyDown(input, { key: "Enter" });

    await waitFor(() => {
      expect(api.lookupBarcode).toHaveBeenCalledWith("8934673501354");
    });
    // Parent form submit must NOT have fired!
    expect(formSubmit).not.toHaveBeenCalled();
  });

  it("13. Yêu cầu xác nhận ghi đè khi trường tên hiện tại đã có dữ liệu khác", async () => {
    api.lookupBarcode.mockResolvedValue(mockCompleteProduct);
    const onApply = vi.fn();

    render(<SellerBarcodeLookup currentProductName="Sữa TH ít đường" onApplyProductName={onApply} />);
    fireEvent.change(screen.getByLabelText("Mã vạch GTIN/EAN"), { target: { value: "8934673501354" } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu" }));

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Áp dụng tên gợi ý" })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "Áp dụng tên gợi ý" }));

    // Confirm dialog must appear
    expect(screen.getByText("Xác nhận thay thế tên sản phẩm")).toBeInTheDocument();
    expect(screen.getByText(/Tên hiện tại \("Sữa TH ít đường"\) sẽ được thay thế/)).toBeInTheDocument();
    // onApply is NOT called yet
    expect(onApply).not.toHaveBeenCalled();
  });

  it("14. Áp dụng tên mới khi người dùng xác nhận thay thế", async () => {
    api.lookupBarcode.mockResolvedValue(mockCompleteProduct);
    const onApply = vi.fn();

    render(<SellerBarcodeLookup currentProductName="Sữa TH ít đường" onApplyProductName={onApply} />);
    fireEvent.change(screen.getByLabelText("Mã vạch GTIN/EAN"), { target: { value: "8934673501354" } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu" }));

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Áp dụng tên gợi ý" })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "Áp dụng tên gợi ý" }));
    fireEvent.click(screen.getByRole("button", { name: "Thay thế" }));

    expect(onApply).toHaveBeenCalledWith("Sữa tươi tiệt trùng Vinamilk ít đường 220ml");
    expect(screen.getByText("Đã áp dụng tên vào form")).toBeInTheDocument();
  });

  it("15. Giữ nguyên tên hiện tại khi người dùng huỷ xác nhận", async () => {
    api.lookupBarcode.mockResolvedValue(mockCompleteProduct);
    const onApply = vi.fn();

    render(<SellerBarcodeLookup currentProductName="Sữa TH ít đường" onApplyProductName={onApply} />);
    fireEvent.change(screen.getByLabelText("Mã vạch GTIN/EAN"), { target: { value: "8934673501354" } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu" }));

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Áp dụng tên gợi ý" })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "Áp dụng tên gợi ý" }));
    fireEvent.click(screen.getByRole("button", { name: "Giữ tên hiện tại" }));

    expect(onApply).not.toHaveBeenCalled();
    expect(screen.queryByText("Xác nhận thay thế tên sản phẩm")).not.toBeInTheDocument();
  });

  it("16. Áp dụng trực tiếp khi tên hiện tại đang rỗng mà không cần confirm", async () => {
    api.lookupBarcode.mockResolvedValue(mockCompleteProduct);
    const onApply = vi.fn();

    render(<SellerBarcodeLookup currentProductName="" onApplyProductName={onApply} />);
    fireEvent.change(screen.getByLabelText("Mã vạch GTIN/EAN"), { target: { value: "8934673501354" } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu" }));

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Áp dụng tên gợi ý" })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "Áp dụng tên gợi ý" }));
    expect(onApply).toHaveBeenCalledWith("Sữa tươi tiệt trùng Vinamilk ít đường 220ml");
  });

  it("17. Hiển thị attribution và cảnh báo needsReview đúng", async () => {
    api.lookupBarcode.mockResolvedValue(mockCompleteProduct);

    render(<SellerBarcodeLookup />);
    fireEvent.change(screen.getByLabelText("Mã vạch GTIN/EAN"), { target: { value: "8934673501354" } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu" }));

    await waitFor(() => {
      expect(screen.getByText(/Dữ liệu do cộng đồng đóng góp. Người bán cần đối chiếu kỹ/)).toBeInTheDocument();
      expect(screen.getByText("Dữ liệu tham khảo từ Open Food Facts (ODbL 1.0).")).toBeInTheDocument();
    });
  });

  it("18. Không render URL không an toàn (chỉ https/http được render thẻ a)", async () => {
    const maliciousData: BarcodeReferenceData = {
      ...mockCompleteProduct,
      sourceRecordUrl: "javascript:alert('xss')",
    };
    api.lookupBarcode.mockResolvedValue(maliciousData);

    render(<SellerBarcodeLookup />);
    fireEvent.change(screen.getByLabelText("Mã vạch GTIN/EAN"), { target: { value: "8934673501354" } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu" }));

    await waitFor(() => {
      expect(screen.getByDisplayValue("Sữa tươi tiệt trùng Vinamilk ít đường 220ml")).toBeInTheDocument();
    });
    // javascript: link must NOT be rendered as a link
    expect(screen.queryByText("Xem bản ghi nguồn Open Food Facts")).not.toBeInTheDocument();
  });

  it("19. Khi thay đổi ô nhập, xoá dữ liệu GTIN cũ để không gây hiểu nhầm", async () => {
    api.lookupBarcode.mockResolvedValue(mockCompleteProduct);

    render(<SellerBarcodeLookup />);
    const input = screen.getByLabelText("Mã vạch GTIN/EAN");
    fireEvent.change(input, { target: { value: "8934673501354" } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu" }));

    await waitFor(() => {
      expect(screen.getByText("Vinamilk")).toBeInTheDocument();
    });

    // Change input
    fireEvent.change(input, { target: { value: "8934673501355" } });
    // Old data must disappear
    expect(screen.queryByText("Vinamilk")).not.toBeInTheDocument();
  });

  it("20. Cảnh báo và không cho phép áp dụng âm thầm khi tên gợi ý vượt quá 220 ký tự", async () => {
    const longName = "A".repeat(230);
    const longProduct: BarcodeReferenceData = {
      ...mockCompleteProduct,
      productName: longName,
    };
    api.lookupBarcode.mockResolvedValue(longProduct);
    const onApply = vi.fn();

    render(<SellerBarcodeLookup onApplyProductName={onApply} />);
    fireEvent.change(screen.getByLabelText("Mã vạch GTIN/EAN"), { target: { value: "8934673501354" } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu" }));

    await waitFor(() => {
      expect(screen.getByText(/Tên gợi ý dài 230 ký tự \(vượt quá giới hạn 220 ký tự\)/)).toBeInTheDocument();
    });

    const applyButton = screen.getByRole("button", { name: "Áp dụng tên gợi ý" });
    expect(applyButton).toBeDisabled();

    // Rút gọn tên về <= 220 ký tự
    const nameInput = screen.getByLabelText("Tên sản phẩm gợi ý");
    fireEvent.change(nameInput, { target: { value: "A".repeat(200) } });

    expect(screen.queryByText(/vượt quá giới hạn 220 ký tự/)).not.toBeInTheDocument();
    expect(applyButton).toBeEnabled();

    fireEvent.click(applyButton);
    expect(onApply).toHaveBeenCalledWith("A".repeat(200));
  });

  it("21. Bấm nút Quét mã khi thiết bị không hỗ trợ camera hiển thị cảnh báo fallback", () => {
    // navigator.mediaDevices is undefined in default jsdom
    render(<SellerBarcodeLookup />);
    const scanButton = screen.getByRole("button", { name: /Quét mã/i });
    fireEvent.click(scanButton);

    expect(
      screen.getByText("Thiết bị hoặc trình duyệt này không hỗ trợ truy cập camera. Bạn vẫn có thể nhập mã vạch thủ công."),
    ).toBeInTheDocument();
  });

  it("22. Quét mã từ camera tự động đưa GTIN vào ô nhập và thực hiện tra cứu", async () => {
    api.lookupBarcode.mockResolvedValue(mockCompleteProduct);

    // Mock camera and BarcodeDetector
    const mockTrack = { stop: vi.fn() };
    Object.defineProperty(navigator, "mediaDevices", {
      value: {
        getUserMedia: vi.fn().mockResolvedValue({
          getTracks: () => [mockTrack],
        }),
      },
      writable: true,
      configurable: true,
    });

    class MockDetector {
      detect() {
        return Promise.resolve([{ rawValue: "8934673501354", format: "ean_13" }]);
      }
    }
    Object.defineProperty(window, "BarcodeDetector", {
      value: MockDetector,
      writable: true,
      configurable: true,
    });

    window.HTMLMediaElement.prototype.play = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(HTMLMediaElement.prototype, "readyState", {
      get: () => 4,
      configurable: true,
    });

    render(<SellerBarcodeLookup />);
    fireEvent.click(screen.getByRole("button", { name: /Quét mã/i }));

    // Scanner modal opens
    expect(screen.getByText("Quét mã vạch sản phẩm")).toBeInTheDocument();

    // Auto-detects and triggers lookup
    await waitFor(() => {
      expect(api.lookupBarcode).toHaveBeenCalledWith("8934673501354");
    });

    // Scanner modal is closed automatically
    expect(screen.queryByText("Quét mã vạch sản phẩm")).not.toBeInTheDocument();

    // Result is displayed in the lookup UI
    expect(screen.getByDisplayValue("Sữa tươi tiệt trùng Vinamilk ít đường 220ml")).toBeInTheDocument();
  });
});
