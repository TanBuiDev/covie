import Link from "next/link";
import type { components } from "@covie/api-client";
import { statusCopy } from "@/lib/format/status-copy";
import { sellerBasePath } from "./seller-context";

type MerchantLocation = components["schemas"]["MerchantLocationResponse"];

export function LocationList({ merchantId, locations }: { merchantId: string; locations: MerchantLocation[] }) {
  const base = sellerBasePath(merchantId);
  if (!locations.length) return <p>Chưa có điểm bán nào.</p>;

  return (
    <ul className="management-list">
      {locations.map((location) => location.id ? (
        <li key={location.id}>
          <div>
            <strong>{location.name ?? "Điểm bán"}</strong>
            {location.addressText ? <small>{location.addressText}</small> : null}
            <span>{statusCopy(location.status)}</span>
          </div>
          <Link href={`${base}/locations/${location.id}`}>Cấu hình</Link>
        </li>
      ) : null)}
    </ul>
  );
}
