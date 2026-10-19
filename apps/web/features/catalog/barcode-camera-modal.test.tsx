import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Mock } from "vitest";
import { BarcodeCameraModal } from "./barcode-camera-modal";

class MockTrack {
  kind = "video";
  stop = vi.fn();
}

class MockMediaStream {
  tracks: MockTrack[];
  constructor() {
    this.tracks = [new MockTrack()];
  }
  getTracks() {
    return this.tracks;
  }
}

describe("BarcodeCameraModal Unit Tests", () => {
  let mockStream: MockMediaStream;
  let mockDetect: Mock<(source: unknown) => Promise<{ rawValue: string; format?: string }[]>>;

  beforeEach(() => {
    mockStream = new MockMediaStream();
    mockDetect = vi.fn().mockResolvedValue([]);

    // Mock mediaDevices.getUserMedia
    Object.defineProperty(navigator, "mediaDevices", {
      value: {
        getUserMedia: vi.fn().mockResolvedValue(mockStream),
      },
      writable: true,
      configurable: true,
    });

    // Mock BarcodeDetector
    class MockBarcodeDetector {
      formats: string[];
      constructor(opts?: { formats: string[] }) {
        this.formats = opts?.formats ?? [];
      }
      detect(source: unknown) {
        return mockDetect(source);
      }
    }

    Object.defineProperty(window, "BarcodeDetector", {
      value: MockBarcodeDetector,
      writable: true,
      configurable: true,
    });

    window.HTMLMediaElement.prototype.play = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(HTMLMediaElement.prototype, "readyState", {
      get: () => 4,
      configurable: true,
    });
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("1. Khởi động camera thành công và gán stream cho thẻ video", async () => {
    render(<BarcodeCameraModal onScan={vi.fn()} onClose={vi.fn()} />);

    await waitFor(() => {
      expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalledWith({
        video: {
          facingMode: { ideal: "environment" },
          width: { ideal: 1920 },
          height: { ideal: 1080 },
        },
      });
    });

    expect(screen.getByText("Căn chỉnh mã vạch vào giữa khung quét")).toBeInTheDocument();
  });

  it("2. Xử lý khi bị từ chối quyền camera (NotAllowedError)", async () => {
    const error = new DOMException("Permission denied", "NotAllowedError");
    (navigator.mediaDevices.getUserMedia as ReturnType<typeof vi.fn>).mockRejectedValue(error);
    const onError = vi.fn();

    render(<BarcodeCameraModal onScan={vi.fn()} onClose={vi.fn()} onError={onError} />);

    await waitFor(() => {
      expect(screen.getByText("Không có quyền truy cập camera. Vui lòng cấp quyền trong cài đặt trình duyệt hoặc nhập mã vạch thủ công.")).toBeInTheDocument();
    });
    expect(onError).toHaveBeenCalledWith(
      "Không có quyền truy cập camera. Vui lòng cấp quyền trong cài đặt trình duyệt hoặc nhập mã vạch thủ công.",
    );
  });

  it("3. Xử lý thiết bị không có mediaDevices (fallback)", () => {
    Object.defineProperty(navigator, "mediaDevices", {
      value: undefined,
      writable: true,
      configurable: true,
    });
    const onError = vi.fn();

    render(<BarcodeCameraModal onScan={vi.fn()} onClose={vi.fn()} onError={onError} />);

    expect(screen.getByText("Thiết bị hoặc trình duyệt này không hỗ trợ truy cập camera. Bạn vẫn có thể nhập mã vạch thủ công.")).toBeInTheDocument();
    expect(onError).toHaveBeenCalled();
  });

  it("4. Xử lý trình duyệt không có BarcodeDetector: tự động kích hoạt fallback camera cho laptop", async () => {
    // Xóa native BarcodeDetector
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    delete (window as any).BarcodeDetector;

    render(<BarcodeCameraModal onScan={vi.fn()} onClose={vi.fn()} />);

    await waitFor(() => {
      expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalled();
    });
    expect(screen.getByText("Căn chỉnh mã vạch vào giữa khung quét")).toBeInTheDocument();
  });

  it("5. Quét thành công: gọi onScan với mã hợp lệ và dừng media track ngay lập tức", async () => {
    const onScan = vi.fn();
    const onClose = vi.fn();
    mockDetect.mockResolvedValueOnce([{ rawValue: "8934673501354", format: "ean_13" }]);

    render(<BarcodeCameraModal onScan={onScan} onClose={onClose} />);

    // Mock HTMLMediaElement.prototype.readyState
    Object.defineProperty(HTMLMediaElement.prototype, "readyState", {
      get: () => 4, // HAVE_ENOUGH_DATA
      configurable: true,
    });

    await waitFor(() => {
      expect(onScan).toHaveBeenCalledWith("8934673501354");
    });

    // Media track must be stopped
    expect(mockStream.tracks[0]?.stop).toHaveBeenCalled();
  });

  it("6. Chống quét lặp (duplicate scan prevention)", async () => {
    const onScan = vi.fn();
    mockDetect.mockResolvedValue([{ rawValue: "8934673501354", format: "ean_13" }]);

    Object.defineProperty(HTMLMediaElement.prototype, "readyState", {
      get: () => 4,
      configurable: true,
    });

    render(<BarcodeCameraModal onScan={onScan} onClose={vi.fn()} />);

    await waitFor(() => {
      expect(onScan).toHaveBeenCalledTimes(1);
    });

    // Advance timers if any; onScan must not be called again
    await new Promise((r) => setTimeout(r, 450));
    expect(onScan).toHaveBeenCalledTimes(1);
  });

  it("7. Cleanup khi component unmount: giải phóng camera stream", async () => {
    const { unmount } = render(<BarcodeCameraModal onScan={vi.fn()} onClose={vi.fn()} />);

    await waitFor(() => {
      expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalled();
    });

    unmount();
    expect(mockStream.tracks[0]?.stop).toHaveBeenCalled();
  });

  it("8. Bấm nút Hủy quét giải phóng stream và gọi onClose", async () => {
    const onClose = vi.fn();
    render(<BarcodeCameraModal onScan={vi.fn()} onClose={onClose} />);

    await waitFor(() => {
      expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalled();
    });

    fireEvent.click(screen.getByRole("button", { name: "Hủy quét" }));
    expect(mockStream.tracks[0]?.stop).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it("9. Nhấn phím Escape đóng scanner và giải phóng camera", async () => {
    const onClose = vi.fn();
    render(<BarcodeCameraModal onScan={vi.fn()} onClose={onClose} />);

    await waitFor(() => {
      expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalled();
    });

    fireEvent.keyDown(window, { key: "Escape" });
    expect(mockStream.tracks[0]?.stop).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it("10. Bấm nút X ở header đóng scanner và dừng stream", async () => {
    const onClose = vi.fn();
    render(<BarcodeCameraModal onScan={vi.fn()} onClose={onClose} />);

    await waitFor(() => {
      expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalled();
    });

    fireEvent.click(screen.getByRole("button", { name: "Đóng máy quét" }));
    expect(mockStream.tracks[0]?.stop).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });
});
