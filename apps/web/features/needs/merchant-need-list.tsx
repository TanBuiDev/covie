"use client";

import type { components } from "@covie/api-client";
import { useEffect, useState } from "react";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import { StatusChip } from "@/components/ui/status-chip";
import { countdownPresentation } from "@/lib/format/time";
import { discoverMerchantNeeds, type MerchantNeedMatch } from "./need-api";

type Location = components["schemas"]["MerchantLocationResponse"];

function distanceCopy(distanceMeters?: number) {
  if (distanceMeters == null) return undefined;
  if (distanceMeters < 1000) return `${Math.max(0, Math.round(distanceMeters))} m`;
  return `${(distanceMeters / 1000).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} km`;
}

export function MerchantNeedList({ merchantId, locations }: { merchantId: string; locations: Location[] }) {
  const firstLocationId = locations.find((location) => location.id)?.id ?? "";
  const [locationId, setLocationId] = useState(firstLocationId);
  const [items, setItems] = useState<MerchantNeedMatch[]>();
  const [nextCursor, setNextCursor] = useState<string>();
  const [error, setError] = useState<string>();
  const [reloadKey, setReloadKey] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);

  useEffect(() => {
    if (!locationId) {
      setItems([]);
      setNextCursor(undefined);
      return;
    }
    let active = true;
    setItems(undefined);
    setNextCursor(undefined);
    setError(undefined);
    void discoverMerchantNeeds(merchantId, locationId)
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
    if (!locationId || !nextCursor || loadingMore) return;
    setLoadingMore(true);
    setError(undefined);
    try {
      const page = await discoverMerchantNeeds(merchantId, locationId, nextCursor);
      setItems((current) => [...(current ?? []), ...(page.items ?? [])]);
      setNextCursor(page.nextCursor);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể tải thêm nhu cầu.");
    } finally {
      setLoadingMore(false);
    }
  }

  if (!locations.length) {
    return <EmptyState title="Chưa có điểm bán để phản hồi" description="Tạo điểm bán và cấu hình phạm vi trước khi xem nhu cầu phù hợp." />;
  }

  return (
    <section className="seller-stack" aria-label="Nhu cầu phù hợp">
      <label className="seller-need-location-filter">
        Điểm bán đang phản hồi
        <select required value={locationId} onChange={(event) => setLocationId(event.target.value)}>
          {locations.map((location) => location.id ? <option key={location.id} value={location.id}>{location.name ?? "Điểm bán"}</option> : null)}
        </select>
        <small>Chỉ dùng nội dung đang bán tại điểm bán này</small>
      </label>

      {error && !items ? (
        <ErrorState
          title="Không thể tải nhu cầu"
          description={error}
          action={<button type="button" onClick={() => setReloadKey((value) => value + 1)}>Thử lại</button>}
        />
      ) : null}
      {!error && !items ? <LoadingState label="Đang tìm nhu cầu phù hợp…" /> : null}
      {items?.length === 0 ? <EmptyState title="Chưa có nhu cầu phù hợp" description="Danh sách sẽ xuất hiện khi có nhu cầu phù hợp với phạm vi của điểm bán đang chọn." /> : null}

      {items?.length ? (
        <div className="seller-need-list">
          {items.map((match, index) => {
            const need = match.need;
            const key = need?.id ?? `${match.resolvedLocation?.id ?? "location"}-${index}`;
            const distance = distanceCopy(match.distanceMeters);
            const expiry = need?.expiresAt ? countdownPresentation(need.expiresAt) : undefined;
            return (
              <article className="seller-need-card" key={key}>
                <div className="seller-need-card__summary">
                  <strong>{need?.title ?? "Nhu cầu"}</strong>
                  <small>{[match.buyerArea?.label, distance ? `cách điểm bán ${distance}` : null].filter(Boolean).join(" · ") || "Khu vực theo dữ liệu hệ thống"}</small>
                </div>

                {expiry ? (
                  <div className={`seller-need-expiry${expiry.urgent ? " seller-need-expiry--urgent" : ""}`} role="status">
                    <span aria-hidden="true" />
                    <div>
                      <strong>{expiry.expired ? "Đã hết hạn" : expiry.urgent ? "Sắp hết hạn" : "Còn thời gian"}</strong>
                      <small>{expiry.label}</small>
                    </div>
                  </div>
                ) : null}

                <StatusChip
                  label={match.alreadyResponded ? "Đã phản hồi" : "Chưa phản hồi"}
                  tone={match.alreadyResponded ? "success" : "neutral"}
                />

                {!match.alreadyResponded ? (
                  <p className="seller-contract-note" role="status">
                    Hiện chưa thể chọn nội dung để phản hồi nhu cầu này. Hệ thống chưa cung cấp danh sách mục phù hợp cho điểm bán đã chọn.
                  </p>
                ) : null}
              </article>
            );
          })}
        </div>
      ) : null}

      {error && items ? <p role="alert">{error}</p> : null}
      {nextCursor ? <button className="seller-load-more" type="button" disabled={loadingMore} onClick={() => void loadMore()}>{loadingMore ? "Đang tải…" : "Tải thêm"}</button> : null}
    </section>
  );
}
