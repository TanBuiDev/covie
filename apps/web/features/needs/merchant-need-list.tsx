"use client";

import { useCallback, useEffect, useState } from "react";
import { discoverMerchantNeeds, type MerchantNeedMatch } from "./need-api";
import { MerchantNeedResponseForm } from "./merchant-need-response-form";

export function MerchantNeedList({ merchantId }: { merchantId: string }) {
  const [locationId, setLocationId] = useState("");
  const [items, setItems] = useState<MerchantNeedMatch[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const refresh = useCallback(() => setRefreshKey((value) => value + 1), []);

  useEffect(() => {
    let active = true;
    discoverMerchantNeeds(merchantId, locationId || undefined)
      .then((page) => active && setItems(page.items))
      .catch((cause) => active && setError(cause instanceof Error ? cause.message : "Không thể tải nhu cầu phù hợp."));
    return () => { active = false; };
  }, [merchantId, locationId, refreshKey]);

  return (
    <section>
      <label>
        Lọc theo merchant location ID
        <input value={locationId} onChange={(event) => setLocationId(event.target.value)} placeholder="Để trống để xem các location được phép" />
      </label>
      {error && <p role="alert">{error}</p>}
      <div style={{ display: "grid", gap: 16, marginTop: 16 }}>
        {items.map((match) => (
          <article key={`${match.need.id}:${match.resolvedLocation.id}`} style={{ border: "1px solid #ddd", padding: 16, borderRadius: 8 }}>
            <h2>{match.need.title}</h2>
            <p>{match.need.type} · {match.distanceMeters.toLocaleString("vi-VN")} m · location: {match.resolvedLocation.name}</p>
            {match.need.description && <p>{match.need.description}</p>}
            <small>Hết hạn: {new Date(match.need.expiresAt).toLocaleString("vi-VN")}</small>
            {match.alreadyResponded ? (
              <p>Location này đã có phản hồi đang hoạt động.</p>
            ) : (
              <MerchantNeedResponseForm
                needId={match.need.id}
                defaultLocationId={match.resolvedLocation.id}
                maxItems={match.responseConstraints.maxItems}
                onCreated={refresh}
              />
            )}
          </article>
        ))}
        {!items.length && !error && <p>Chưa có nhu cầu phù hợp.</p>}
      </div>
    </section>
  );
}
