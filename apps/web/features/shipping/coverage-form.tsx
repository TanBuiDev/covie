"use client";

import { FormEvent, useState } from "react";
import { createDeliveryCoverage } from "./shipping-api";

export function CoverageForm({ merchantId, locationId }: { merchantId: string; locationId: string }) {
  const [radiusEnabled, setRadiusEnabled] = useState(true); const [message, setMessage] = useState<string>();
  async function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); const form = new FormData(event.currentTarget); try { await createDeliveryCoverage(merchantId, locationId, { coverageType: String(form.get("coverageType")) as "PRODUCT_DELIVERY" | "SERVICE_AREA", radiusEnabled, radiusMeters: radiusEnabled ? Number(form.get("radiusMeters")) : undefined, areaEnabled: false, status: "ACTIVE" }); window.location.reload(); } catch { setMessage("Không thể lưu vùng phục vụ."); } }
  return <form onSubmit={submit}><h2>Vùng phục vụ</h2><label>Áp dụng <select name="coverageType"><option value="PRODUCT_DELIVERY">Giao sản phẩm</option><option value="SERVICE_AREA">Khu vực dịch vụ</option></select></label><label><input type="checkbox" checked={radiusEnabled} onChange={(event) => setRadiusEnabled(event.target.checked)} /> Bán kính</label>{radiusEnabled && <label>Mét <input name="radiusMeters" type="number" min="1" required /></label>}{message && <p role="alert">{message}</p>}<button type="submit">Lưu vùng phục vụ</button></form>;
}
