"use client";

import type { components } from "@covie/api-client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { CovieApiError } from "@/lib/api/api-error";
import type { Etagged } from "./merchant-api";
import { createMerchant, updateMerchant } from "./merchant-api";

export function MerchantList({ merchants }: { merchants: components["schemas"]["MerchantSummaryResponse"][] }) {
  return (
    <div className="seller-row-list">
      {merchants.map((merchant) => merchant.id ? (
        <Link className="seller-row" key={merchant.id} href={`/seller/${encodeURIComponent(merchant.id)}`}>
          <div>
            <strong>{merchant.name ?? "Cửa hàng"}</strong>
            <small>{merchant.currentUserRole ?? "Thành viên"}</small>
          </div>
          <span className="status-chip">{merchant.status ?? "UNKNOWN"}</span>
        </Link>
      ) : null)}
    </div>
  );
}

export function MerchantForm({ merchant, onCreated, onReload }: {
  merchant?: Etagged<components["schemas"]["MerchantManagementResponse"]>;
  onCreated?: () => void;
  onReload?: () => void;
}) {
  const router = useRouter();
  const [message, setMessage] = useState<string>();
  const [pending, setPending] = useState(false);
  const editing = Boolean(merchant?.data.id);
  const reload = onReload ?? (() => router.refresh());
  const saved = onCreated ?? (() => router.refresh());

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    const form = new FormData(event.currentTarget);
    const input = {
      name: String(form.get("name") ?? "").trim(),
      description: String(form.get("description") ?? "").trim() || undefined,
    };

    setPending(true);
    setMessage(undefined);
    try {
      if (editing && merchant?.data.id) await updateMerchant(merchant.data.id, merchant.etag, input);
      else await createMerchant(input);
      saved();
    } catch (error) {
      if (error instanceof CovieApiError && error.code === "RESOURCE_VERSION_CONFLICT") {
        setMessage("Dữ liệu đã được thay đổi ở nơi khác. Hãy tải dữ liệu mới trước khi lưu lại.");
      } else {
        setMessage("Không thể lưu cửa hàng. Vui lòng thử lại.");
      }
    } finally {
      setPending(false);
    }
  }

  return (
    <form onSubmit={submit} aria-busy={pending}>
      <h2>{editing ? "Thông tin cửa hàng" : "Tạo cửa hàng"}</h2>
      <label>
        Tên cửa hàng
        <input name="name" required disabled={pending} defaultValue={merchant?.data.name ?? ""} autoComplete="organization" />
      </label>
      <label>
        Mô tả
        <textarea name="description" disabled={pending} defaultValue={merchant?.data.description ?? ""} />
      </label>
      {message ? <p role="alert">{message}</p> : null}
      {message?.startsWith("Dữ liệu") ? (
        <button type="button" disabled={pending} onClick={reload}>Tải dữ liệu mới</button>
      ) : null}
      <button type="submit" disabled={pending}>{pending ? "Đang lưu…" : editing ? "Lưu cửa hàng" : "Tạo cửa hàng"}</button>
    </form>
  );
}
