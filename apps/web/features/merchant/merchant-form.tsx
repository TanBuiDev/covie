"use client";

import type { components } from "@covie/api-client";
import Link from "next/link";
import { FormEvent, useState } from "react";
import { CovieApiError } from "@/lib/api/api-error";
import type { Etagged } from "./merchant-api";
import { createMerchant, updateMerchant } from "./merchant-api";

export function MerchantList({ merchants }: { merchants: components["schemas"]["MerchantSummaryResponse"][] }) {
  return <ul>{merchants.map((merchant) => <li key={merchant.id}>
    <Link href={`/seller/${merchant.id}`}>{merchant.name}</Link> <span>{merchant.status}</span>
  </li>)}</ul>;
}

export function MerchantForm({ merchant, onCreated, onReload }: {
  merchant?: Etagged<components["schemas"]["MerchantManagementResponse"]>;
  onCreated?: () => void;
  onReload?: () => void;
}) {
  const [message, setMessage] = useState<string>();
  const editing = Boolean(merchant?.data.id);
  const reload = onReload ?? (() => window.location.reload());

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const input = { name: String(form.get("name")).trim(), description: String(form.get("description") ?? "").trim() || undefined };
    try {
      if (editing && merchant?.data.id) await updateMerchant(merchant.data.id, merchant.etag, input);
      else await createMerchant(input);
      (onCreated ?? (() => window.location.reload()))();
    } catch (error) {
      if (error instanceof CovieApiError && error.code === "RESOURCE_VERSION_CONFLICT") {
        setMessage("Dữ liệu đã được thay đổi ở nơi khác. Hãy tải lại trước khi tiếp tục.");
        return;
      }
      setMessage("Không thể lưu cửa hàng. Vui lòng thử lại.");
    }
  }

  return <form onSubmit={submit}>
    <h2>{editing ? "Chỉnh sửa cửa hàng" : "Tạo cửa hàng"}</h2>
    <label>Tên cửa hàng <input name="name" required defaultValue={merchant?.data.name ?? ""} /></label>
    <label>Mô tả <textarea name="description" defaultValue={merchant?.data.description ?? ""} /></label>
    {message && <p role="alert">{message}</p>}
    {message?.startsWith("Dữ liệu") && <button type="button" onClick={reload}>Tải lại dữ liệu</button>}
    <button type="submit">{editing ? "Lưu cửa hàng" : "Tạo cửa hàng"}</button>
  </form>;
}
