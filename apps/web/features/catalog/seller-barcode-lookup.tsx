"use client";

import type { components } from "@covie/api-client";
import { useEffect, useRef, useState, type KeyboardEvent } from "react";
import {
  ArrowSquareOut,
  Barcode,
  Camera,
  CheckCircle,
  MagnifyingGlass,
  WarningCircle,
} from "@phosphor-icons/react";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { CovieApiError } from "@/lib/api/api-error";
import { lookupBarcode } from "./catalog-api";
import { BarcodeCameraModal, isBarcodeCameraSupported } from "./barcode-camera-modal";

export type BarcodeReferenceData = components["schemas"]["BarcodeReferenceResponse"];

export type BarcodeLookupStatus = "idle" | "loading" | "success" | "invalid" | "not_found" | "error";

export interface SellerBarcodeLookupProps {
  mode?: "create" | "edit";
  canApply?: boolean;
  disabled?: boolean;
  currentProductName?: string;
  onApplyProductName?: (name: string) => void;
  className?: string;
}

function isSafeUrl(url?: string): boolean {
  if (!url) return false;
  try {
    const parsed = new URL(url);
    return parsed.protocol === "https:" || parsed.protocol === "http:";
  } catch {
    return false;
  }
}

export function SellerBarcodeLookup({
  mode = "create",
  canApply = true,
  disabled = false,
  currentProductName = "",
  onApplyProductName,
  className = "",
}: SellerBarcodeLookupProps) {
  const [gtin, setGtin] = useState("");
  const [status, setStatus] = useState<BarcodeLookupStatus>("idle");
  const [data, setData] = useState<BarcodeReferenceData | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Suggested name handling (with <= 220 chars enforcement)
  const [nameDraft, setNameDraft] = useState("");
  const [appliedSuccess, setAppliedSuccess] = useState(false);

  // Confirmation dialog for name replacement
  const [showConfirm, setShowConfirm] = useState(false);
  const [pendingName, setPendingName] = useState<string | null>(null);

  // Camera scanner modal
  const [scannerOpen, setScannerOpen] = useState(false);

  const requestIdRef = useRef(0);
  const isMountedRef = useRef(true);

  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  function handleInputChange(value: string) {
    setGtin(value);
    setAppliedSuccess(false);
    // When input changes, clear previous result and error to never display stale GTIN info
    if (status !== "idle") {
      setStatus("idle");
      setData(null);
      setErrorMessage(null);
      setNameDraft("");
    }
  }

  async function executeLookup(codeToLookup: string) {
    if (disabled || status === "loading") return;

    const trimmed = codeToLookup.trim();
    if (!trimmed) {
      setStatus("invalid");
      setErrorMessage("Vui lòng nhập mã vạch GTIN/EAN.");
      setData(null);
      return;
    }

    // Basic format check on frontend (GTIN-8, GTIN-12, GTIN-13, GTIN-14)
    const isDigitsOnly = /^\d+$/.test(trimmed);
    const isValidLength = [8, 12, 13, 14].includes(trimmed.length);
    if (!isDigitsOnly || !isValidLength) {
      setStatus("invalid");
      setErrorMessage("Mã vạch phải gồm 8, 12, 13 hoặc 14 chữ số hợp lệ.");
      setData(null);
      return;
    }

    const currentReqId = ++requestIdRef.current;
    setStatus("loading");
    setErrorMessage(null);
    setData(null);
    setAppliedSuccess(false);

    try {
      const responseData = await lookupBarcode(trimmed);
      if (!isMountedRef.current || requestIdRef.current !== currentReqId) return;

      setStatus("success");
      setData(responseData);
      setNameDraft(responseData.productName ?? "");
    } catch (error) {
      if (!isMountedRef.current || requestIdRef.current !== currentReqId) return;

      if (error instanceof CovieApiError) {
        if (error.status === 422 || error.code === "VALIDATION_FAILED") {
          setStatus("invalid");
          setErrorMessage(error.message || "Mã GTIN/Barcode không hợp lệ hoặc sai số kiểm tra.");
        } else if (error.status === 404 || error.code === "RESOURCE_NOT_FOUND") {
          setStatus("not_found");
          setErrorMessage("Chưa có thông tin tham chiếu cho mã vạch này trong hệ thống.");
        } else if (error.status === 403) {
          setStatus("error");
          setErrorMessage("Bạn không có quyền tra cứu mã vạch này.");
        } else if (error.status === 429) {
          setStatus("error");
          setErrorMessage("Quá nhiều yêu cầu. Vui lòng thử lại sau giây lát.");
        } else if (error.status === 500 || error.status === 503) {
          setStatus("error");
          setErrorMessage("Hệ thống tra cứu tạm thời gián đoạn. Vui lòng thử lại sau.");
        } else {
          setStatus("error");
          setErrorMessage(error.message || "Không thể tra cứu thông tin mã vạch.");
        }
      } else {
        setStatus("error");
        setErrorMessage("Lỗi kết nối mạng. Vui lòng kiểm tra kết nối và thử lại.");
      }
    }
  }

  function handleLookup() {
    void executeLookup(gtin);
  }

  function handleOpenScanner() {
    if (disabled || status === "loading") return;
    if (!isBarcodeCameraSupported()) {
      setStatus("invalid");
      setErrorMessage("Thiết bị hoặc trình duyệt này không hỗ trợ truy cập camera. Bạn vẫn có thể nhập mã vạch thủ công.");
      setData(null);
      return;
    }
    setScannerOpen(true);
  }

  function handleCameraScanned(scannedCode: string) {
    setScannerOpen(false);
    const cleaned = scannedCode.trim();
    setGtin(cleaned);
    void executeLookup(cleaned);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "Enter") {
      // CRITICAL: Prevent submitting the parent catalog form!
      event.preventDefault();
      void handleLookup();
    }
  }

  function handleApplyClick() {
    if (!canApply || !data) return;
    const targetName = nameDraft.trim();
    if (!targetName) return;

    if (targetName.length > 220) {
      // Contract limit: maximum 220 characters
      return;
    }

    const existingName = currentProductName.trim();
    if (existingName && existingName !== targetName) {
      // Existing name is different: prompt for replacement confirmation
      setPendingName(targetName);
      setShowConfirm(true);
    } else {
      executeApply(targetName);
    }
  }

  function executeApply(nameToApply: string) {
    onApplyProductName?.(nameToApply);
    setAppliedSuccess(true);
    setShowConfirm(false);
    setPendingName(null);
  }

  function handleCancelConfirm() {
    setShowConfirm(false);
    setPendingName(null);
  }

  const suggestedNameTooLong = nameDraft.trim().length > 220;

  return (
    <div className={`seller-barcode-lookup ${className}`} data-mode={mode} aria-label="Tra cứu mã vạch sản phẩm">
      <div className="seller-barcode-lookup__header">
        <div className="seller-barcode-lookup__title">
          <Barcode size={20} weight="bold" color="#047857" aria-hidden="true" />
          <span>Tra cứu mã vạch GTIN / EAN</span>
        </div>
        <span className="seller-badge-subtle">Tham khảo</span>
      </div>

      <p className="seller-barcode-lookup__desc">
        Nhập mã vạch sản phẩm (GTIN-8, GTIN-12, GTIN-13 hoặc GTIN-14) để tìm thông tin tham khảo từ Open Food Facts.
      </p>

      <div className="seller-barcode-lookup__row">
        <div className="seller-barcode-lookup__input-wrap">
          <input
            type="text"
            className="seller-barcode-lookup__input"
            aria-label="Mã vạch GTIN/EAN"
            placeholder="Ví dụ: 8934673501354"
            autoComplete="off"
            value={gtin}
            disabled={disabled || status === "loading"}
            onChange={(e) => handleInputChange(e.target.value)}
            onKeyDown={handleKeyDown}
          />
        </div>

        <button
          type="button"
          className="seller-button seller-button--secondary seller-barcode-lookup__action-btn"
          aria-label="Quét mã bằng camera"
          disabled={disabled || status === "loading"}
          onClick={handleOpenScanner}
        >
          <Camera size={16} weight="bold" aria-hidden="true" />
          <span>Quét mã</span>
        </button>

        <button
          type="button"
          className="seller-button seller-button--secondary seller-barcode-lookup__action-btn"
          disabled={disabled || status === "loading" || !gtin.trim()}
          onClick={handleLookup}
        >
          <MagnifyingGlass size={16} weight="bold" aria-hidden="true" />
          <span>{status === "loading" ? "Đang tra cứu…" : "Tra cứu"}</span>
        </button>
      </div>

      {status === "invalid" && errorMessage ? (
        <div className="warning-banner" role="alert" style={{ margin: "4px 0 0" }}>
          <WarningCircle size={16} weight="fill" aria-hidden="true" />
          <span>{errorMessage}</span>
        </div>
      ) : null}

      {status === "not_found" && errorMessage ? (
        <div className="seller-contract-note" role="status" style={{ margin: "4px 0 0" }}>
          <span>{errorMessage}</span>
        </div>
      ) : null}

      {status === "error" && errorMessage ? (
        <div className="warning-banner" role="alert" style={{ margin: "4px 0 0" }}>
          <WarningCircle size={16} weight="fill" aria-hidden="true" />
          <span>{errorMessage}</span>
        </div>
      ) : null}

      {status === "success" && data ? (
        <div className="seller-barcode-lookup__result" aria-live="polite">
          <div className="seller-barcode-lookup__grid">
            <div>
              <span className="seller-barcode-lookup__field-label">Mã tra cứu:</span>
              <span className="seller-barcode-lookup__field-val">{data.gtin || gtin}</span>
            </div>
            {data.brand ? (
              <div>
                <span className="seller-barcode-lookup__field-label">Thương hiệu tham khảo:</span>
                <span className="seller-barcode-lookup__field-val">{data.brand}</span>
              </div>
            ) : null}
            {data.quantity ? (
              <div>
                <span className="seller-barcode-lookup__field-label">Quy cách tham khảo:</span>
                <span className="seller-barcode-lookup__field-val">{data.quantity}</span>
              </div>
            ) : null}
            {data.categoryLabel ? (
              <div>
                <span className="seller-barcode-lookup__field-label">Nhãn ngành hàng:</span>
                <span className="seller-barcode-lookup__field-val">{data.categoryLabel}</span>
              </div>
            ) : null}
            {data.qualityStatus ? (
              <div>
                <span className="seller-barcode-lookup__field-label">Trạng thái dữ liệu:</span>
                <span className="seller-barcode-lookup__field-val">{data.qualityStatus}</span>
              </div>
            ) : null}
            {data.marketSignal ? (
              <div>
                <span className="seller-barcode-lookup__field-label">Tín hiệu phân loại:</span>
                <span className="seller-barcode-lookup__field-val">{data.marketSignal}</span>
              </div>
            ) : null}
          </div>

          {data.productName ? (
            <div style={{ marginTop: "4px" }}>
              <label>
                <span className="seller-barcode-lookup__field-label">
                  Tên sản phẩm gợi ý (tối đa 220 ký tự):
                </span>
                <input
                  type="text"
                  className="seller-barcode-lookup__input"
                  aria-label="Tên sản phẩm gợi ý"
                  value={nameDraft}
                  maxLength={240}
                  disabled={!canApply || disabled}
                  onChange={(e) => {
                    setNameDraft(e.target.value);
                    setAppliedSuccess(false);
                  }}
                />
              </label>
              {suggestedNameTooLong ? (
                <p className="warning-banner" role="alert" style={{ marginTop: "6px", fontSize: "12px" }}>
                  Tên gợi ý dài {nameDraft.trim().length} ký tự (vượt quá giới hạn 220 ký tự). Vui lòng rút gọn trước khi áp dụng.
                </p>
              ) : null}
            </div>
          ) : null}

          {data.needsReview ? (
            <div className="seller-barcode-lookup__warning" role="alert">
              <WarningCircle size={16} weight="fill" aria-hidden="true" />
              <span>
                Dữ liệu do cộng đồng đóng góp. Người bán cần đối chiếu kỹ với thông tin trên bao bì thực tế trước khi sử dụng.
              </span>
            </div>
          ) : null}

          <p className="seller-barcode-lookup__attribution">
            {data.attributionNotice || "Dữ liệu tham khảo từ Open Food Facts (ODbL 1.0). Thông tin do cộng đồng đóng góp, người bán cần xác nhận lại."}
          </p>

          {isSafeUrl(data.sourceRecordUrl) ? (
            <div>
              <a
                href={data.sourceRecordUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="seller-barcode-lookup__link"
              >
                <span>Xem bản ghi nguồn Open Food Facts</span>
                <ArrowSquareOut size={13} weight="bold" style={{ marginLeft: "4px", verticalAlign: "middle" }} aria-hidden="true" />
              </a>
            </div>
          ) : null}

          {canApply && data.productName ? (
            <div className="seller-barcode-lookup__apply-box">
              {appliedSuccess ? (
                <span style={{ fontSize: "13px", color: "#047857", fontWeight: 600, display: "inline-flex", alignItems: "center", gap: "4px" }}>
                  <CheckCircle size={16} weight="fill" aria-hidden="true" />
                  <span>Đã áp dụng tên vào form</span>
                </span>
              ) : null}

              <button
                type="button"
                className="seller-button seller-button--primary"
                disabled={disabled || suggestedNameTooLong || !nameDraft.trim()}
                onClick={handleApplyClick}
              >
                <span>Áp dụng tên gợi ý</span>
              </button>
            </div>
          ) : !canApply ? (
            <p className="seller-contract-note" style={{ margin: "4px 0 0" }}>
              Chế độ chỉ xem thông tin tham khảo.
            </p>
          ) : null}
        </div>
      ) : null}

      {scannerOpen ? (
        <BarcodeCameraModal
          onScan={handleCameraScanned}
          onClose={() => setScannerOpen(false)}
          onError={(msg) => {
            setScannerOpen(false);
            setStatus("invalid");
            setErrorMessage(msg);
          }}
        />
      ) : null}

      <ConfirmDialog
        open={showConfirm}
        title="Xác nhận thay thế tên sản phẩm"
        description={`Tên hiện tại ("${currentProductName}") sẽ được thay thế bằng "${pendingName}". Bạn có chắc chắn muốn áp dụng tên mới?`}
        confirmLabel="Thay thế"
        cancelLabel="Giữ tên hiện tại"
        presentation="modal"
        onConfirm={() => {
          if (pendingName) executeApply(pendingName);
        }}
        onCancel={handleCancelConfirm}
      />
    </div>
  );
}
