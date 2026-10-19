"use client";

import { useRef, useState } from "react";
import { createApiClient } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";

export function AdminBillingInvoiceExport() {
  const inFlight = useRef(false);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState("");

  async function exportInvoices() {
    if (inFlight.current) return;
    inFlight.current = true;
    setExporting(true);
    setError("");
    try {
      let failureResponse: Response | undefined;
      const client = createApiClient("", response => {
        if (!response.ok) failureResponse = response.clone();
      });
      const { data, response } = await client.GET("/api/v1/admin/billing/invoices/export", {
        params: { query: { limit: 20, offset: 0 } }, parseAs: "blob",
      });
      if (!response.ok) throw await readApiError(failureResponse ?? response);
      if (!data) throw new Error("Không nhận được tệp hóa đơn.");
      const objectUrl = URL.createObjectURL(data);
      const anchor = document.createElement("a");
      try {
        anchor.href = objectUrl;
        anchor.download = "seller-billing-invoices.csv";
        anchor.hidden = true;
        document.body.appendChild(anchor);
        anchor.click();
      } finally {
        anchor.remove();
        URL.revokeObjectURL(objectUrl);
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể xuất hóa đơn.");
    } finally {
      inFlight.current = false;
      setExporting(false);
    }
  }

  return <div>
    <button type="button" className="admin-button admin-button--secondary" disabled={exporting}
      aria-busy={exporting} onClick={() => void exportInvoices()}>
      {exporting ? "Đang xuất CSV…" : "Xuất trang CSV"}
    </button>
    {error ? <p role="alert" className="admin-error">{error}</p> : null}
  </div>;
}
