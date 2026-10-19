"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Camera, UploadSimple, WarningCircle, X } from "@phosphor-icons/react";

export interface BarcodeCameraModalProps {
  onScan: (gtin: string) => void;
  onClose: () => void;
  onError?: (errorMessage: string) => void;
}

export function isBarcodeCameraSupported(): boolean {
  if (typeof window === "undefined") return false;
  return Boolean(
    typeof navigator !== "undefined" &&
    navigator.mediaDevices &&
    typeof navigator.mediaDevices.getUserMedia === "function",
  );
}

const UNSUPPORTED_MESSAGE =
  "Thiết bị hoặc trình duyệt này không hỗ trợ truy cập camera. Bạn vẫn có thể nhập mã vạch thủ công.";

export function BarcodeCameraModal({
  onScan,
  onClose,
  onError,
}: BarcodeCameraModalProps) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const zxingControlsRef = useRef<{ stop: () => void } | null>(null);
  const scannedRef = useRef(false);

  const onScanRef = useRef(onScan);
  const onCloseRef = useRef(onClose);
  const onErrorRef = useRef(onError);

  useEffect(() => {
    onScanRef.current = onScan;
    onCloseRef.current = onClose;
    onErrorRef.current = onError;
  });

  const supported = isBarcodeCameraSupported();
  const [loading, setLoading] = useState(supported);
  const [uploadLoading, setUploadLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(
    supported ? null : UNSUPPORTED_MESSAGE,
  );

  const stopMediaStream = useCallback(() => {
    if (zxingControlsRef.current) {
      try {
        zxingControlsRef.current.stop();
      } catch {
        // ignore
      }
      zxingControlsRef.current = null;
    }
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => {
        try {
          track.stop();
        } catch {
          // ignore error on stop
        }
      });
      streamRef.current = null;
    }
  }, []);

  const handleClose = useCallback(() => {
    stopMediaStream();
    onCloseRef.current();
  }, [stopMediaStream]);

  async function handleFileUpload(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) return;
    event.target.value = "";
    setUploadLoading(true);

    try {
      const objectUrl = URL.createObjectURL(file);
      let detected: string | null = null;

      if (typeof window !== "undefined" && "BarcodeDetector" in window) {
        const BarcodeDetectorClass = (window as unknown as {
          BarcodeDetector: new (opts?: { formats: string[] }) => {
            detect: (source: ImageBitmapSource) => Promise<Array<{ rawValue: string }>>;
          };
        }).BarcodeDetector;
        const detector = new BarcodeDetectorClass({
          formats: ["ean_13", "ean_8", "upc_a", "upc_e"],
        });
        const img = document.createElement("img");
        img.src = objectUrl;
        await img.decode?.().catch(() => {});
        const results = await detector.detect(img);
        detected = results?.[0]?.rawValue?.trim() ?? null;
      }

      if (!detected) {
        const { BrowserMultiFormatReader, BarcodeFormat } = await import("@zxing/browser");
        const hints = new Map();
        hints.set(2, [
          BarcodeFormat.EAN_13,
          BarcodeFormat.EAN_8,
          BarcodeFormat.UPC_A,
          BarcodeFormat.UPC_E,
          BarcodeFormat.CODE_128,
          BarcodeFormat.ITF,
        ]);
        hints.set(3, true); // TRY_HARDER
        hints.set(11, true); // ALSO_INVERTED
        const reader = new BrowserMultiFormatReader(hints);
        const result = await reader.decodeFromImageUrl(objectUrl);
        detected = (result.getText?.() || "").trim();
      }

      URL.revokeObjectURL(objectUrl);

      if (detected) {
        scannedRef.current = true;
        stopMediaStream();
        onScanRef.current(detected);
      } else {
        setErrorMessage("Không nhận diện được mã vạch trong ảnh này. Vui lòng chọn ảnh chụp rõ nét hơn.");
      }
    } catch {
      setErrorMessage("Không nhận diện được mã vạch trong ảnh này. Vui lòng chọn ảnh chụp rõ nét hơn.");
    } finally {
      setUploadLoading(false);
    }
  }

  useEffect(() => {
    scannedRef.current = false;

    if (!isBarcodeCameraSupported()) {
      onErrorRef.current?.(UNSUPPORTED_MESSAGE);
      return;
    }

    let isMounted = true;

    async function initCamera() {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: {
            facingMode: { ideal: "environment" },
            width: { ideal: 1920 },
            height: { ideal: 1080 },
          },
        });

        if (!isMounted) {
          stream.getTracks().forEach((t) => t.stop());
          return;
        }

        streamRef.current = stream;
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          await videoRef.current.play?.().catch(() => {});
        }

        if (isMounted) {
          setLoading(false);
        }

        // Engine 1: Native BarcodeDetector (Android, macOS Safari/Chrome, ChromeOS)
        if (typeof window !== "undefined" && "BarcodeDetector" in window) {
          const BarcodeDetectorClass = (window as unknown as {
            BarcodeDetector: new (opts?: { formats: string[] }) => {
              detect: (source: ImageBitmapSource) => Promise<Array<{ rawValue: string }>>;
            };
          }).BarcodeDetector;

          const detector = new BarcodeDetectorClass({
            formats: ["ean_13", "ean_8", "upc_a", "upc_e"],
          });

          // Scan interval: poll detector every 150ms
          intervalRef.current = setInterval(async () => {
            if (!videoRef.current || scannedRef.current || videoRef.current.readyState < 2) {
              return;
            }

            try {
              const results = await detector.detect(videoRef.current);
              const firstResult = results?.[0];
              if (firstResult && !scannedRef.current) {
                const detected = firstResult.rawValue?.trim();
                if (detected) {
                  scannedRef.current = true;
                  stopMediaStream();
                  onScanRef.current(detected);
                }
              }
            } catch {
              // Frame detection error, ignore and continue next tick
            }
          }, 150);
        } else {
          // Engine 2: Pure JS fallback via @zxing/browser (Windows laptop, Firefox, etc.)
          try {
            const { BrowserMultiFormatReader, BarcodeFormat } = await import("@zxing/browser");
            if (!isMounted) return;

            const hints = new Map();
            // 2 corresponds to POSSIBLE_FORMATS in ZXing DecodeHintType
            hints.set(2, [
              BarcodeFormat.EAN_13,
              BarcodeFormat.EAN_8,
              BarcodeFormat.UPC_A,
              BarcodeFormat.UPC_E,
              BarcodeFormat.CODE_128,
              BarcodeFormat.ITF,
            ]);
            // 3 corresponds to TRY_HARDER (thử nhiều góc quét, đa dòng và binarization để nhận diện khi hơi mờ/nghiêng)
            hints.set(3, true);
            // 11 corresponds to ALSO_INVERTED (hỗ trợ nền tương phản đảo ngược)
            hints.set(11, true);

            const reader = new BrowserMultiFormatReader(hints, { delayBetweenScanAttempts: 100 });
            if (videoRef.current && isMounted) {
              const controls = await reader.decodeFromVideoElement(
                videoRef.current,
                (result) => {
                  if (result && !scannedRef.current) {
                    const detected = (result.getText?.() || "").trim();
                    if (detected) {
                      scannedRef.current = true;
                      controls.stop();
                      stopMediaStream();
                      onScanRef.current(detected);
                    }
                  }
                },
              );
              zxingControlsRef.current = controls;
            }
          } catch (fallbackErr) {
            console.warn("Fallback barcode scanner failed to initialize:", fallbackErr);
          }
        }
      } catch (err) {
        if (!isMounted) return;
        setLoading(false);

        let friendlyError = "Không thể truy cập camera. Bạn vẫn có thể nhập mã vạch thủ công.";
        if (err instanceof DOMException) {
          if (err.name === "NotAllowedError" || err.name === "PermissionDeniedError") {
            friendlyError = "Không có quyền truy cập camera. Vui lòng cấp quyền trong cài đặt trình duyệt hoặc nhập mã vạch thủ công.";
          } else if (err.name === "NotFoundError" || err.name === "DevicesNotFoundError") {
            friendlyError = "Không tìm thấy thiết bị camera trên máy này. Bạn có thể nhập mã vạch thủ công.";
          }
        }
        setErrorMessage(friendlyError);
        onErrorRef.current?.(friendlyError);
      }
    }

    void initCamera();

    function onKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") {
        handleClose();
      }
    }

    window.addEventListener("keydown", onKeyDown);

    return () => {
      isMounted = false;
      window.removeEventListener("keydown", onKeyDown);
      stopMediaStream();
    };
  }, [handleClose, stopMediaStream]);

  return (
    <div
      className="dialog-backdrop"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) handleClose();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Quét mã vạch bằng camera"
        className="seller-camera-modal"
      >
        <header className="seller-camera-modal__header">
          <div className="seller-camera-modal__title">
            <Camera size={20} weight="bold" color="#047857" aria-hidden="true" />
            <span>Quét mã vạch sản phẩm</span>
          </div>
          <button
            type="button"
            className="seller-camera-modal__close-btn"
            aria-label="Đóng máy quét"
            onClick={handleClose}
          >
            <X size={18} weight="bold" aria-hidden="true" />
          </button>
        </header>

        <div className="seller-camera-modal__body">
          {errorMessage ? (
            <div className="seller-camera-modal__error-box">
              <div className="warning-banner" role="alert" style={{ margin: "0 0 12px" }}>
                <WarningCircle size={18} weight="fill" aria-hidden="true" />
                <span>{errorMessage}</span>
              </div>
              <p className="seller-muted" style={{ fontSize: "13px" }}>
                Bạn vẫn có thể tiếp tục bằng cách nhập mã vạch bằng tay trong ô tra cứu hoặc tải ảnh chụp mã vạch bên dưới.
              </p>
            </div>
          ) : (
            <>
              <video
                ref={videoRef}
                playsInline
                muted
                autoPlay
                className="seller-camera-modal__video"
              />
              <div className="seller-camera-modal__overlay" aria-hidden="true">
                <div className="seller-camera-modal__viewfinder">
                  <div className="seller-camera-modal__reticle" />
                </div>
                <p className="seller-camera-modal__hint">
                  {loading ? "Đang khởi động camera…" : "Căn chỉnh mã vạch vào giữa khung quét"}
                </p>
                <p className="seller-camera-modal__tips">
                  Mẹo webcam: Giữ mã vạch cách camera 20–30 cm (không để quá sát) để lấy nét tốt nhất
                </p>
              </div>
            </>
          )}
        </div>

        <footer className="seller-camera-modal__footer">
          <label className="seller-button seller-button--secondary" style={{ cursor: "pointer" }}>
            <UploadSimple size={16} weight="bold" aria-hidden="true" />
            <span>{uploadLoading ? "Đang xử lý ảnh…" : "Tải ảnh mã vạch"}</span>
            <input
              type="file"
              accept="image/*"
              aria-label="Tải ảnh chụp mã vạch"
              style={{ display: "none" }}
              disabled={uploadLoading}
              onChange={handleFileUpload}
            />
          </label>
          <button
            type="button"
            className="seller-button seller-button--secondary"
            onClick={handleClose}
          >
            <span>{errorMessage ? "Đóng" : "Hủy quét"}</span>
          </button>
        </footer>
      </div>
    </div>
  );
}
