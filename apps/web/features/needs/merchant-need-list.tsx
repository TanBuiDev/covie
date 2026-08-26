"use client";

import type { components } from "@covie/api-client";
import { useEffect, useState } from "react";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import { formatDateTime } from "@/lib/format/time";
import { discoverMerchantNeeds, type MerchantNeedMatch } from "./need-api";

type Location = components["schemas"]["MerchantLocationResponse"];

export function MerchantNeedList({ merchantId, locations }: { merchantId: string; locations: Location[] }) {
  const [locationId, setLocationId] = useState("");
  const [items, setItems] = useState<MerchantNeedMatch[]>();
  const [nextCursor, setNextCursor] = useState<string>();
  const [error, setError] = useState<string>();
  const [reloadKey, setReloadKey] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);

  useEffect(() => {
    let active = true;
    setItems(undefined);
    setNextCursor(undefined);
    setError(undefined);
    void discoverMerchantNeeds(merchantId, locationId || undefined)
      .then((page) => {
        if (!active) return;
        setItems(page.items ?? []);
        setNextCursor(page.nextCursor);
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : "Không thể tải nhu cầu phù hợp.");
      });
    return () => {
      active = false;
    };
  }, [merchantId, locationId, reloadKey]);

  async function loadMore() {
    if (!nextCursor || loadingMore) return;
    setLoadingMore(true);
    setError(undefined);
    try {
      const page = await discoverMerchantNeeds(merchantId, locationId || undefined, nextCursor);
      setItems((current) => [...(current ?? []), ...(page.items ?? [])]);
      setNextCursor(page.nextCursor);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể tải thêm nhu cầu.");
    } finally {
      setLoadingMore(false);
    }
  }

  return (
    <section className="seller-stack" aria-label="Nhu cầu phù hợp">
      {locations.length ? (
        <label className="seller-need-location-filter">
          Điểm bán
          <select value={locationId} onChange={(event) => setLocationId(event.target.value)}>
            <option value="">Tất cả điểm bán được backend cho phép</option>
            {locations.map((location) => location.id ? <option key={location.id} value={location.id}>{location.name ?? "Điểm bán"}</option> : null)}
          </select>
        </label>
      ) : null}

      {error && !items ? (
        <ErrorState
          title="Không thể tải nhu cầu"
          description={error}
          action={<button type="button" onClick={() => setReloadKey((value) => value + 1)}>Thử lại</button>}
        />
      ) : null}
      {!error && !items ? <LoadingState label="Đang tìm nhu cầu phù hợp…" /> : null}
      {items?.length === 0 ? <EmptyState title="Chưa có nhu cầu phù hợp" description="Danh sách này do backend match theo phạm vi location của Seller." /> : null}

      {items?.length ? (
        <div className="seller-need-list">
          {items.map((match, index) => {
            const need = match.need;
            const resolved = match.resolvedLocation;
            const key = need?.id ?? `${resolved?.id ?? "location"}-${index}`;
            return (
              <article className="seller-need-card" key={key}>
                <header>
                  <div>
                    <small>{need?.type === "SERVICE" ? "Dịch vụ" : "Sản phẩm"}</small>
                    <h2>{need?.title ?? "Nhu cầu"}</h2>
                  </div>
                  {match.alreadyResponded ? <span className="status-chip">Đã phản hồi</span> : null}
                </header>
                {need?.description ? <p>{need.description}</p> : null}
                <dl className="seller-order-facts">
                  {match.buyerArea?.label ? <div><dt>Khu vực người mua</dt><dd>{match.buyerArea.label}</dd></div> : null}
                  {match.distanceMeters != null ? <div><dt>Khoảng cách</dt><dd>{(match.distanceMeters / 1000).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} km</dd></div> : null}
                  {resolved?.name ? <div><dt>Điểm bán được match</dt><dd>{resolved.name}</dd></div> : null}
                  {match.responseConstraints?.maxItems != null ? <div><dt>Tối đa offer</dt><dd>{match.responseConstraints.maxItems}</dd></div> : null}
                </dl>
                {need?.expiresAt ? <small>Hết hạn: {formatDateTime(need.expiresAt)}</small> : null}
                {!match.alreadyResponded ? (
                  <p className="seller-muted" role="status">
                    Chưa mount thao tác phản hồi: API hiện chưa trả danh sách LocationVariant/offer đủ điều kiện cho Need này. CoVie không cho nhập UUID hoặc giá thủ công.
                  </p>
                ) : null}
              </article>
            );
          })}
        </div>
      ) : null}

      {error && items ? <p role="alert">{error}</p> : null}
      {nextCursor ? <button type="button" disabled={loadingMore} onClick={() => void loadMore()}>{loadingMore ? "Đang tải…" : "Tải thêm nhu cầu"}</button> : null}
    </section>
  );
}
