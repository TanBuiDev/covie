"use client";

import type { components } from "@covie/api-client";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { CovieApiError } from "@/lib/api/api-error";
import {
  createDeliveryCoverage,
  deleteDeliveryCoverage,
  listLocationAreas,
  updateDeliveryCoverage,
} from "@/features/shipping/shipping-api";

type Coverage = components["schemas"]["DeliveryCoverageResponse"];
type CoverageType = NonNullable<components["schemas"]["DeliveryCoverageUpsertRequest"]["coverageType"]>;
type CoverageStatus = NonNullable<components["schemas"]["DeliveryCoverageUpsertRequest"]["status"]>;
type LocationArea = components["schemas"]["LocationAreaResponse"];

export function CoverageForm({
  merchantId,
  locationId,
  coverage,
  onCancelEdit,
}: {
  merchantId: string;
  locationId: string;
  coverage?: Coverage;
  onCancelEdit?: () => void;
}) {
  const router = useRouter();
  const editing = Boolean(coverage?.id);
  const [coverageType, setCoverageType] = useState<CoverageType>(coverage?.coverageType ?? "PRODUCT_DELIVERY");
  const [status, setStatus] = useState<CoverageStatus>(coverage?.status ?? "ACTIVE");
  const [radiusEnabled, setRadiusEnabled] = useState(coverage?.radiusEnabled ?? true);
  const [areaEnabled, setAreaEnabled] = useState(coverage?.areaEnabled ?? false);
  const [radiusMeters, setRadiusMeters] = useState(String(coverage?.radiusMeters ?? 3000));
  const [areas, setAreas] = useState<LocationArea[]>([]);
  const [selectedAreaIds, setSelectedAreaIds] = useState<string[]>(coverage?.locationAreaIds ?? []);
  const [message, setMessage] = useState<string>();
  const [pending, setPending] = useState(false);
  const [areasLoading, setAreasLoading] = useState(true);
  const [confirmDeactivate, setConfirmDeactivate] = useState(false);

  useEffect(() => {
    let active = true;
    void listLocationAreas()
      .then((items) => {
        if (active) setAreas(items);
      })
      .catch(() => {
        if (active) setMessage("Không thể tải danh sách khu vực. Bạn vẫn có thể cấu hình bằng bán kính.");
      })
      .finally(() => {
        if (active) setAreasLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    setMessage(undefined);

    const radius = Number(radiusMeters);
    if (!radiusEnabled && !areaEnabled) {
      setMessage("Bật ít nhất một cách xác định vùng phục vụ.");
      return;
    }
    if (radiusEnabled && (!Number.isSafeInteger(radius) || radius < 1)) {
      setMessage("Bán kính phải là số nguyên dương theo mét.");
      return;
    }
    if (areaEnabled && selectedAreaIds.length === 0) {
      setMessage("Chọn ít nhất một khu vực trước khi lưu.");
      return;
    }

    const input: components["schemas"]["DeliveryCoverageUpsertRequest"] = {
      coverageType,
      radiusEnabled,
      radiusMeters: radiusEnabled ? radius : undefined,
      areaEnabled,
      locationAreaIds: areaEnabled ? selectedAreaIds : [],
      status,
    };

    setPending(true);
    try {
      if (coverage?.id) {
        if (coverage.version == null) throw new Error("RESOURCE_VERSION_REQUIRED");
        await updateDeliveryCoverage(merchantId, locationId, coverage.id, coverage.version, input);
      } else {
        await createDeliveryCoverage(merchantId, locationId, input);
      }
      router.refresh();
      onCancelEdit?.();
    } catch (error) {
      if (error instanceof CovieApiError && error.code === "RESOURCE_VERSION_CONFLICT") {
        setMessage("Vùng phục vụ đã được thay đổi ở nơi khác. Tải dữ liệu mới rồi thử lại.");
      } else if (error instanceof CovieApiError && error.code === "DELIVERY_COVERAGE_DUPLICATE_ACTIVE") {
        setMessage("Điểm bán đã có vùng đang hoạt động cho loại này. Hãy tạm ngưng vùng cũ trước.");
      } else {
        setMessage(error instanceof Error && error.message === "RESOURCE_VERSION_REQUIRED"
          ? "Thiếu version của vùng phục vụ. Hãy tải lại dữ liệu trước khi sửa."
          : "Không thể lưu vùng phục vụ. Hãy kiểm tra dữ liệu và thử lại.");
      }
    } finally {
      setPending(false);
    }
  }

  async function deactivate() {
    if (!coverage?.id || coverage.version == null || pending) return;
    setPending(true);
    setMessage(undefined);
    try {
      await deleteDeliveryCoverage(merchantId, locationId, coverage.id, coverage.version);
      setConfirmDeactivate(false);
      onCancelEdit?.();
      router.refresh();
    } catch (error) {
      if (error instanceof CovieApiError && error.code === "RESOURCE_VERSION_CONFLICT") {
        setMessage("Vùng phục vụ đã thay đổi. Tải dữ liệu mới trước khi tạm ngưng.");
      } else {
        setMessage("Không thể tạm ngưng vùng phục vụ. Vui lòng thử lại.");
      }
      setConfirmDeactivate(false);
    } finally {
      setPending(false);
    }
  }

  return (
    <form className="seller-config-form" onSubmit={submit} aria-busy={pending}>
      <div className="seller-section__heading">
        <h2>{editing ? "Sửa vùng phục vụ" : "Thêm vùng phục vụ"}</h2>
        {editing && onCancelEdit ? <button type="button" disabled={pending} onClick={onCancelEdit}>Đóng chỉnh sửa</button> : null}
      </div>

      <label>
        Áp dụng
        <select disabled={pending || editing} value={coverageType} onChange={(event) => setCoverageType(event.target.value as CoverageType)}>
          <option value="PRODUCT_DELIVERY">Giao sản phẩm</option>
          <option value="SERVICE_AREA">Khu vực dịch vụ</option>
        </select>
      </label>
      {editing ? <p className="field-hint">Loại coverage là immutable theo backend; tạo vùng mới nếu cần đổi loại.</p> : null}

      <fieldset disabled={pending}>
        <legend>Cách xác định vùng</legend>
        <label>
          <input type="checkbox" checked={radiusEnabled} onChange={(event) => setRadiusEnabled(event.target.checked)} />
          Dùng bán kính
        </label>
        {radiusEnabled ? (
          <label>
            Bán kính (mét)
            <input
              type="number"
              inputMode="numeric"
              min="1"
              step="1"
              value={radiusMeters}
              onChange={(event) => setRadiusMeters(event.target.value)}
              required
            />
          </label>
        ) : null}

        <label>
          <input type="checkbox" checked={areaEnabled} onChange={(event) => setAreaEnabled(event.target.checked)} />
          Dùng khu vực được chọn
        </label>
        {areaEnabled ? (
          <div className="seller-area-options" aria-label="Khu vực phục vụ">
            {areasLoading ? <p className="seller-muted">Đang tải khu vực…</p> : null}
            {!areasLoading && areas.length ? areas.map((area) => area.id ? (
              <label key={area.id}>
                <input
                  type="checkbox"
                  checked={selectedAreaIds.includes(area.id)}
                  onChange={(event) => setSelectedAreaIds((current) => event.target.checked
                    ? [...new Set([...current, area.id!])]
                    : current.filter((id) => id !== area.id))}
                />
                {area.name ?? "Khu vực"}
              </label>
            ) : null) : null}
            {!areasLoading && !areas.length ? <p className="seller-muted">Chưa có khu vực khả dụng.</p> : null}
          </div>
        ) : null}
      </fieldset>

      <label>
        Trạng thái
        <select disabled={pending} value={status} onChange={(event) => setStatus(event.target.value as CoverageStatus)}>
          <option value="ACTIVE">Đang hoạt động</option>
          <option value="INACTIVE">Tạm ngưng</option>
        </select>
      </label>

      {message ? <p role="alert">{message}</p> : null}
      <div className="seller-form-actions">
        <button type="submit" disabled={pending || (!radiusEnabled && !areaEnabled)}>
          {pending ? "Đang lưu…" : editing ? "Lưu vùng phục vụ" : "Thêm vùng phục vụ"}
        </button>
        {editing && coverage?.status !== "INACTIVE" ? (
          <button type="button" className="button-danger" disabled={pending} onClick={() => setConfirmDeactivate(true)}>
            Tạm ngưng vùng
          </button>
        ) : null}
      </div>

      <ConfirmDialog
        open={confirmDeactivate}
        title="Tạm ngưng vùng phục vụ?"
        description="Backend sẽ chuyển vùng này sang INACTIVE; dữ liệu cấu hình vẫn được giữ lại."
        confirmLabel={pending ? "Đang xử lý…" : "Tạm ngưng"}
        dangerous
        confirmDisabled={pending}
        cancelDisabled={pending}
        onCancel={() => { if (!pending) setConfirmDeactivate(false); }}
        onConfirm={() => { void deactivate(); }}
      />
    </form>
  );
}
