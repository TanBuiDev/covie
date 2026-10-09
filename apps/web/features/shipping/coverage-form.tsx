"use client";

import type { components } from "@covie/api-client";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { CovieApiError } from "@/lib/api/api-error";
import {
  createDeliveryCoverage,
  deleteDeliveryCoverage,
  listLocationAreas,
  updateDeliveryCoverage,
} from "@/features/shipping/shipping-api";

import {
  Compass,
  MapPin,
  Storefront,
  Info,
  Sparkle,
  FloppyDisk,
  CheckCircle,
} from "@phosphor-icons/react";

type Coverage = components["schemas"]["DeliveryCoverageResponse"];
type CoverageType = NonNullable<components["schemas"]["DeliveryCoverageUpsertRequest"]["coverageType"]>;
type LocationArea = components["schemas"]["LocationAreaResponse"];
type CoverageFacet = "RADIUS" | "AREAS";

const RADIUS_PRESETS = [500, 1000, 2000, 3000, 5000, 10000] as const;

function radiusLabel(value: number) {
  if (value < 1000) return `${value} m`;
  return `${value / 1000} km`;
}

function estimatedAreaSqKm(radiusMeters: number): string {
  if (!Number.isFinite(radiusMeters) || radiusMeters <= 0) return "0";
  const rKm = radiusMeters / 1000;
  return (Math.PI * rKm * rKm).toFixed(1);
}

function estimatedDeliveryTime(radiusMeters: number): string {
  if (!Number.isFinite(radiusMeters) || radiusMeters <= 0) return "Chưa xác định";
  if (radiusMeters <= 1000) return "5 - 15 phút";
  if (radiusMeters <= 3000) return "15 - 25 phút";
  if (radiusMeters <= 5000) return "25 - 35 phút";
  return "35 - 50 phút";
}

export function CoverageForm({
  merchantId,
  locationId,
  coverage,
  onCancelEdit,
  locationName,
}: {
  merchantId: string;
  locationId: string;
  coverage?: Coverage;
  onCancelEdit?: () => void;
  locationName?: string;
}) {
  const router = useRouter();
  const editing = Boolean(coverage?.id);
  const [coverageType, setCoverageType] = useState<CoverageType | "">(coverage?.coverageType ?? "");
  const [facet, setFacet] = useState<CoverageFacet>(coverage?.areaEnabled && !coverage.radiusEnabled ? "AREAS" : "RADIUS");
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

  const radius = Number(radiusMeters);
  const radiusPreset = Number.isSafeInteger(radius) && RADIUS_PRESETS.some((preset) => preset === radius)
    ? String(radius)
    : "CUSTOM";
  const selectedAreaNames = useMemo(() => selectedAreaIds
    .map((id) => areas.find((area) => area.id === id)?.name)
    .filter((name): name is string => Boolean(name)), [areas, selectedAreaIds]);

  function selectFacet(next: CoverageFacet) {
    setFacet(next);
    if (editing) {
      if (next === "RADIUS") setRadiusEnabled(true);
      else setAreaEnabled(true);
      return;
    }
    setRadiusEnabled(next === "RADIUS");
    setAreaEnabled(next === "AREAS");
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    setMessage(undefined);

    if (!coverageType) {
      setMessage("Chọn mục đích phạm vi trong phần Thông tin bổ sung trước khi lưu.");
      return;
    }
    if (!radiusEnabled && !areaEnabled) {
      setMessage("Phạm vi cần ít nhất một bán kính hoặc khu vực.");
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
      status: coverage?.status ?? "ACTIVE",
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
        setMessage("Phạm vi đã được thay đổi ở nơi khác. Tải dữ liệu mới rồi thử lại.");
      } else if (error instanceof CovieApiError && error.code === "DELIVERY_COVERAGE_DUPLICATE_ACTIVE") {
        setMessage("Điểm bán đã có một phạm vi đang hoạt động cho mục đích này. Hãy tạm ngưng phạm vi cũ trước.");
      } else {
        setMessage(error instanceof Error && error.message === "RESOURCE_VERSION_REQUIRED"
          ? "Thông tin phạm vi chưa đủ để chỉnh sửa an toàn. Hãy tải lại dữ liệu trước khi sửa."
          : "Không thể lưu phạm vi phục vụ. Hãy kiểm tra dữ liệu và thử lại.");
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
        setMessage("Phạm vi đã thay đổi. Tải dữ liệu mới trước khi tạm ngưng.");
      } else {
        setMessage("Không thể tạm ngưng phạm vi. Vui lòng thử lại.");
      }
      setConfirmDeactivate(false);
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="seller-coverage-studio-layout">
      <div className="seller-coverage-studio-main">
        <form className="seller-config-form seller-coverage-form" onSubmit={submit} aria-busy={pending}>
          <div className="seller-section__heading seller-desktop-only">
            <h2>{editing ? "Sửa phạm vi phục vụ" : "Thêm phạm vi phục vụ"}</h2>
            {editing && onCancelEdit ? (
              <button type="button" className="seller-button seller-button--secondary" disabled={pending} onClick={onCancelEdit}>
                Đóng chỉnh sửa
              </button>
            ) : null}
          </div>

          <div className="seller-segmented seller-segmented--2" role="group" aria-label="Kiểu phạm vi đang chỉnh">
            <button
              type="button"
              className={facet === "RADIUS" ? "is-active" : ""}
              aria-pressed={facet === "RADIUS"}
              disabled={pending}
              onClick={() => selectFacet("RADIUS")}
            >
              Bán kính
            </button>
            <button
              type="button"
              className={facet === "AREAS" ? "is-active" : ""}
              aria-pressed={facet === "AREAS"}
              disabled={pending}
              onClick={() => selectFacet("AREAS")}
            >
              Khu vực
            </button>
          </div>

          {facet === "RADIUS" ? (
            <div className="seller-coverage-radius-section">
              <label>
                Bán kính
                <select
                  value={radiusPreset}
                  disabled={pending}
                  onChange={(event) => {
                    if (event.target.value !== "CUSTOM") setRadiusMeters(event.target.value);
                    else if (radiusPreset !== "CUSTOM") setRadiusMeters("");
                    setRadiusEnabled(true);
                  }}
                >
                  {RADIUS_PRESETS.map((preset) => <option value={preset} key={preset}>{radiusLabel(preset)}</option>)}
                  <option value="CUSTOM">Tùy chỉnh</option>
                </select>
                <small className="field-hint">500 m · 1 · 2 · 3 · 5 · 10 km hoặc tùy chỉnh</small>
              </label>

              {/* Quick Preset Buttons */}
              <div className="seller-radius-preset-pills" aria-hidden="true">
                {RADIUS_PRESETS.map((preset) => (
                  <button
                    key={preset}
                    type="button"
                    tabIndex={-1}
                    className={`seller-preset-pill ${Number(radiusMeters) === preset ? "is-selected" : ""}`}
                    disabled={pending}
                    onClick={() => {
                      setRadiusMeters(String(preset));
                      setRadiusEnabled(true);
                    }}
                  >
                    {radiusLabel(preset)}
                  </button>
                ))}
              </div>
            </div>
          ) : null}

          {facet === "RADIUS" && radiusPreset === "CUSTOM" ? (
            <label>
              Bán kính tùy chỉnh (mét)
              <input
                type="number"
                inputMode="numeric"
                min="1"
                step="1"
                value={radiusMeters}
                disabled={pending}
                onChange={(event) => setRadiusMeters(event.target.value)}
                required
              />
            </label>
          ) : null}

          {facet === "AREAS" ? (
            <section className="seller-coverage-areas" aria-labelledby="coverage-area-heading">
              <div className="seller-coverage-areas__head">
                <strong id="coverage-area-heading">Chọn khu vực phục vụ</strong>
                <span className="seller-badge seller-badge--neutral">
                  Đã chọn {selectedAreaIds.length} khu vực
                </span>
              </div>
              {areasLoading ? <p className="seller-muted">Đang tải khu vực…</p> : null}
              {!areasLoading && areas.length ? (
                <div className="seller-coverage-area-list">
                  {areas.map((area) => area.id ? (
                    <label key={area.id} className="seller-coverage-area-item">
                      <input
                        type="checkbox"
                        checked={selectedAreaIds.includes(area.id)}
                        disabled={pending}
                        onChange={(event) => {
                          setAreaEnabled(true);
                          setSelectedAreaIds((current) => event.target.checked
                            ? [...new Set([...current, area.id!])]
                            : current.filter((id) => id !== area.id));
                        }}
                      />
                      <span>{area.name ?? "Khu vực"}</span>
                    </label>
                  ) : null)}
                </div>
              ) : null}
              {!areasLoading && !areas.length ? <p className="seller-muted">Chưa có khu vực khả dụng.</p> : null}
              <p className="seller-muted">Bạn có thể giữ thêm bán kính nếu cấu hình hiện tại đang áp dụng cả hai phạm vi.</p>
            </section>
          ) : null}

          {(radiusEnabled || areaEnabled) ? (
            <section className="seller-info-card seller-coverage-current" aria-label="Phạm vi đang áp dụng">
              <strong>Phạm vi đang áp dụng</strong>
              {radiusEnabled && Number.isSafeInteger(radius) && radius > 0 ? <p>• Bán kính {radiusLabel(radius)}</p> : null}
              {areaEnabled ? <p>• Khu vực: {selectedAreaNames.length ? selectedAreaNames.join(", ") : `${selectedAreaIds.length} khu vực đã chọn`}</p> : null}
              {radiusEnabled && areaEnabled ? <small>Phạm vi này đang áp dụng cả bán kính và khu vực.</small> : null}
            </section>
          ) : null}

          <details className="seller-technical-fields" open={!editing && !coverageType}>
            <summary>Thông tin bổ sung</summary>
            <label>
              Áp dụng cho
              <select disabled={pending || editing} value={coverageType} onChange={(event) => setCoverageType(event.target.value as CoverageType)} required>
                <option value="">Chọn mục đích phạm vi</option>
                <option value="PRODUCT_DELIVERY">Giao sản phẩm</option>
                <option value="SERVICE_AREA">Khu vực dịch vụ</option>
              </select>
            </label>
            {editing ? <p className="field-hint">Mục đích áp dụng không thể đổi khi sửa. Tạo cấu hình mới nếu cần dùng mục đích khác.</p> : null}
            {editing ? <p className="field-hint">Trạng thái hiện tại: {coverage?.status ?? "không xác định"}.</p> : null}
            {editing && coverage?.status !== "INACTIVE" ? (
              <button type="button" className="button-danger" disabled={pending} onClick={() => setConfirmDeactivate(true)}>
                Tạm ngưng phạm vi
              </button>
            ) : null}
          </details>

          {message ? <p role="alert">{message}</p> : null}
          <button
            className="seller-button seller-button--primary seller-coverage-save"
            type="submit"
            disabled={pending || !coverageType || (facet === "AREAS" && selectedAreaIds.length === 0)}
          >
            {pending ? (
              "Đang lưu…"
            ) : facet === "AREAS" ? (
              <>
                <FloppyDisk size={16} weight="bold" aria-hidden="true" />
                <span>Lưu khu vực</span>
              </>
            ) : (
              <>
                <FloppyDisk size={16} weight="bold" aria-hidden="true" />
                <span>Lưu phạm vi</span>
              </>
            )}
          </button>

          <ConfirmDialog
            open={confirmDeactivate}
            title="Tạm ngưng phạm vi phục vụ?"
            description="Phạm vi sẽ được chuyển sang trạng thái tạm ngưng; dữ liệu cấu hình vẫn được giữ lại."
            confirmLabel={pending ? "Đang xử lý…" : "Tạm ngưng"}
            dangerous
            confirmDisabled={pending}
            cancelDisabled={pending}
            onCancel={() => { if (!pending) setConfirmDeactivate(false); }}
            onConfirm={() => { void deactivate(); }}
          />
        </form>
      </div>

      {/* ASIDE: LIVE COVERAGE RADAR & STAT CARD */}
      <aside className="seller-coverage-studio-aside">
        <div className="seller-live-preview-box">
          <div className="seller-live-preview-box__title">
            <Sparkle size={16} weight="fill" className="seller-brand-accent" aria-hidden="true" />
            <span>Trực quan độ phủ chi nhánh</span>
          </div>

          <div className="seller-coverage-radar-card">
            <div className="seller-radar-container" aria-hidden="true">
              <div className="seller-radar-grid">
                <div className="seller-radar-ring seller-radar-ring--1" />
                <div className="seller-radar-ring seller-radar-ring--2" />
                <div className="seller-radar-ring seller-radar-ring--3" />
                <div className="seller-radar-pulse" />
                <div className="seller-radar-center">
                  <Storefront size={20} weight="fill" />
                </div>
              </div>
              <div className="seller-radar-distance-badge">
                {facet === "RADIUS" ? radiusLabel(radius || 3000) : `${selectedAreaIds.length} khu vực`}
              </div>
            </div>

            <div className="seller-radar-stats">
              <div className="seller-radar-stat-item">
                <span className="seller-radar-stat-label">Diện tích phục vụ</span>
                <strong className="seller-radar-stat-value">
                  {facet === "RADIUS" ? `~${estimatedAreaSqKm(radius || 3000)} km²` : `${selectedAreaIds.length} khu vực`}
                </strong>
              </div>
              <div className="seller-radar-stat-item">
                <span className="seller-radar-stat-label">Thời gian giao dự kiến</span>
                <strong className="seller-radar-stat-value">
                  {estimatedDeliveryTime(radius || 3000)}
                </strong>
              </div>
              <div className="seller-radar-stat-item">
                <span className="seller-radar-stat-label">Mục đích</span>
                <strong className="seller-radar-stat-value">
                  {coverageType === "PRODUCT_DELIVERY"
                    ? "Giao sản phẩm"
                    : coverageType === "SERVICE_AREA"
                    ? "Khu vực dịch vụ"
                    : "Chưa chọn"}
                </strong>
              </div>
            </div>
          </div>

          <div className="seller-create-tips-card">
            <h4>
              <Info size={16} weight="bold" aria-hidden="true" />
              <span>Gợi ý cự ly logistics</span>
            </h4>
            <ul className="seller-tips-list">
              <li><strong>Đồ ăn, thức uống (F&B):</strong> Nên chọn 1 - 3 km để thức ăn luôn nóng và shipper giao nhanh.</li>
              <li><strong>Bách hóa, đồ tươi sống:</strong> Chọn 3 - 5 km để cân bằng giữa lượng khách và chi phí giao.</li>
              <li><strong>Hàng thời trang, đồ khô:</strong> Có thể mở rộng lên 5 - 10 km để bao phủ toàn thành phố.</li>
            </ul>
          </div>
        </div>
      </aside>
    </div>
  );
}
