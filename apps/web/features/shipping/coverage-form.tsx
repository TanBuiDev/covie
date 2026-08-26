"use client";

import type { components } from "@covie/api-client";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import { createDeliveryCoverage, listLocationAreas } from "@/features/shipping/shipping-api";

type CoverageMode = "RADIUS" | "SELECTED_AREAS";
type LocationArea = components["schemas"]["LocationAreaResponse"];

export function CoverageForm({ merchantId, locationId }: { merchantId: string; locationId: string }) {
  const router = useRouter();
  const [mode, setMode] = useState<CoverageMode>("RADIUS");
  const [areas, setAreas] = useState<LocationArea[]>([]);
  const [selectedAreaIds, setSelectedAreaIds] = useState<string[]>([]);
  const [message, setMessage] = useState<string>();
  const [pending, setPending] = useState(false);
  const [areasLoading, setAreasLoading] = useState(true);

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
    const form = new FormData(event.currentTarget);
    const radiusMeters = Math.max(1, Math.trunc(Number(form.get("radiusMeters"))));
    setPending(true);
    setMessage(undefined);
    try {
      await createDeliveryCoverage(merchantId, locationId, {
        coverageType: String(form.get("coverageType")) as "PRODUCT_DELIVERY" | "SERVICE_AREA",
        radiusEnabled: mode === "RADIUS",
        radiusMeters: mode === "RADIUS" ? radiusMeters : undefined,
        areaEnabled: mode === "SELECTED_AREAS",
        locationAreaIds: mode === "SELECTED_AREAS" ? selectedAreaIds : undefined,
        status: "ACTIVE",
      });
      router.refresh();
    } catch {
      setMessage("Không thể lưu vùng phục vụ. Hãy kiểm tra dữ liệu và thử lại.");
    } finally {
      setPending(false);
    }
  }

  return (
    <form onSubmit={submit} aria-busy={pending}>
      <h2>Thêm vùng phục vụ</h2>
      <label>
        Áp dụng
        <select name="coverageType" disabled={pending}>
          <option value="PRODUCT_DELIVERY">Giao sản phẩm</option>
          <option value="SERVICE_AREA">Khu vực dịch vụ</option>
        </select>
      </label>
      <fieldset disabled={pending}>
        <legend>Cách xác định vùng</legend>
        <label><input type="radio" name="coverageMode" checked={mode === "RADIUS"} onChange={() => setMode("RADIUS")} /> Bán kính</label>
        <label><input type="radio" name="coverageMode" checked={mode === "SELECTED_AREAS"} onChange={() => setMode("SELECTED_AREAS")} /> Khu vực được chọn</label>
      </fieldset>

      {mode === "RADIUS" ? (
        <label>
          Bán kính (mét)
          <input name="radiusMeters" type="number" inputMode="numeric" min="1" step="1" required disabled={pending} defaultValue="3000" />
        </label>
      ) : (
        <fieldset disabled={pending || areasLoading}>
          <legend>Chọn khu vực</legend>
          {areasLoading ? <p className="seller-muted">Đang tải khu vực…</p> : null}
          {!areasLoading && areas.length ? areas.map((area) => area.id ? (
            <label key={area.id}>
              <input
                type="checkbox"
                checked={selectedAreaIds.includes(area.id)}
                onChange={(event) => setSelectedAreaIds((current) => event.target.checked ? [...current, area.id!] : current.filter((id) => id !== area.id))}
              />
              {area.name ?? "Khu vực"}
            </label>
          ) : null) : null}
          {!areasLoading && !areas.length ? <p className="seller-muted">Chưa có khu vực khả dụng.</p> : null}
          {!selectedAreaIds.length ? <p className="field-hint">Chọn ít nhất một khu vực trước khi lưu.</p> : null}
        </fieldset>
      )}

      {message ? <p role="status">{message}</p> : null}
      <button type="submit" disabled={pending || (mode === "SELECTED_AREAS" && selectedAreaIds.length === 0)}>
        {pending ? "Đang lưu…" : "Lưu vùng phục vụ"}
      </button>
    </form>
  );
}
