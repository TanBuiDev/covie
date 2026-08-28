"use client";

import type { components } from "@covie/api-client";
import { useEffect, useState } from "react";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import { StatusChip } from "@/components/ui/status-chip";
import { countdownPresentation } from "@/lib/format/time";
import { MerchantNeedResponseForm, type NeedOfferOption } from "./merchant-need-response-form";
import {
  discoverMerchantNeeds,
  listNeedResponseCandidates,
  type MerchantNeedMatch,
  type NeedResponseCandidatePage,
} from "./need-api";

type Location = components["schemas"]["MerchantLocationResponse"];

function distanceCopy(distanceMeters?: number) {
  if (distanceMeters == null) return undefined;
  if (distanceMeters < 1000) return `${Math.max(0, Math.round(distanceMeters))} m`;
  return `${(distanceMeters / 1000).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} km`;
}

function candidateLabel(candidate: NonNullable<NeedResponseCandidatePage["items"]>[number]) {
  const parts = [candidate.itemName, candidate.variantName];
  const amount = candidate.currentPrice?.amount;
  if (amount != null) {
    parts.push(new Intl.NumberFormat("vi-VN", {
      style: "currency",
      currency: candidate.currentPrice?.currency ?? "VND",
      maximumFractionDigits: 0,
    }).format(amount));
  }
  const availability = candidate.availability as { availableQuantity?: unknown } | undefined;
  const quantity = typeof availability?.availableQuantity === "number"
    ? availability.availableQuantity : undefined;
  if (quantity != null) parts.push(`còn ${quantity}`);
  return parts.filter(Boolean).join(" · ");
}

function MerchantNeedCandidateForm({
  needId,
  merchantId,
  merchantLocationId,
  onCreated,
}: {
  needId: string;
  merchantId: string;
  merchantLocationId: string;
  onCreated: () => void;
}) {
  const [requestState, setRequestState] = useState<{
    key: string;
    page?: NeedResponseCandidatePage;
    error?: string;
  }>();
  const [reloadKey, setReloadKey] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const resourceKey = `${needId}:${merchantId}:${merchantLocationId}`;

  useEffect(() => {
    let active = true;
    void listNeedResponseCandidates(needId, merchantId, merchantLocationId)
      .then((result) => active && setRequestState({ key: resourceKey, page: result }))
      .catch((cause) => active && setRequestState({
        key: resourceKey,
        error: cause instanceof Error ? cause.message : "Không thể tải nội dung phù hợp.",
      }));
    return () => { active = false; };
  }, [needId, merchantId, merchantLocationId, reloadKey, resourceKey]);

  const currentState = requestState?.key === resourceKey ? requestState : undefined;
  const page = currentState?.page;
  const error = currentState?.error;

  async function loadMoreCandidates() {
    if (!page?.nextCursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const next = await listNeedResponseCandidates(needId, merchantId, merchantLocationId, page.nextCursor);
      setRequestState({ key: resourceKey, page: {
          ...next,
          items: [...(page.items ?? []), ...(next.items ?? [])],
        } });
    } catch (cause) {
      setRequestState({
        key: resourceKey,
        page,
        error: cause instanceof Error ? cause.message : "Không thể tải thêm nội dung phù hợp.",
      });
    } finally {
      setLoadingMore(false);
    }
  }

  if (!page && !error) return <LoadingState label="Đang tải nội dung phù hợp…" />;
  if (!page && error) {
    return <ErrorState title="Không thể tải nội dung phù hợp" description={error}
      action={<button type="button" onClick={() => setReloadKey((value) => value + 1)}>Thử lại</button>} />;
  }

  const offers: NeedOfferOption[] = (page?.items ?? []).flatMap((candidate) => candidate.locationVariantId ? [{
    locationVariantId: candidate.locationVariantId,
    label: candidateLabel(candidate),
    available: candidate.available,
    actionable: true,
  }] : []);

  return (
    <div className="seller-stack">
      <MerchantNeedResponseForm
        needId={needId}
        merchantLocationId={merchantLocationId}
        maxItems={page?.maxItems ?? 1}
        offers={offers}
        onCreated={onCreated}
        onSubmitError={() => setReloadKey((value) => value + 1)}
      />
      {error ? <p role="alert">{error}</p> : null}
      {page?.nextCursor ? (
        <button type="button" disabled={loadingMore} onClick={() => void loadMoreCandidates()}>
          {loadingMore ? "Đang tải…" : "Tải thêm nội dung"}
        </button>
      ) : null}
    </div>
  );
}

export function MerchantNeedList({ merchantId, locations }: { merchantId: string; locations: Location[] }) {
  const firstLocationId = locations.find((location) => location.id)?.id ?? "";
  const [locationId, setLocationId] = useState(firstLocationId);
  const [items, setItems] = useState<MerchantNeedMatch[] | undefined>(() => firstLocationId ? undefined : []);
  const [nextCursor, setNextCursor] = useState<string>();
  const [error, setError] = useState<string>();
  const [reloadKey, setReloadKey] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);

  useEffect(() => {
    if (!locationId) return;
    let active = true;
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

  function selectLocation(nextLocationId: string) {
    setLocationId(nextLocationId);
    setItems(nextLocationId ? undefined : []);
    setNextCursor(undefined);
    setError(undefined);
  }

  function reload() {
    setItems(undefined);
    setNextCursor(undefined);
    setError(undefined);
    setReloadKey((value) => value + 1);
  }

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
        <select aria-label="Điểm bán đang phản hồi" required value={locationId} onChange={(event) => selectLocation(event.target.value)}>
          {locations.map((location) => location.id ? <option key={location.id} value={location.id}>{location.name ?? "Điểm bán"}</option> : null)}
        </select>
        <small>Chỉ dùng nội dung đang bán tại điểm bán này</small>
      </label>

      {error && !items ? (
        <ErrorState
          title="Không thể tải nhu cầu"
          description={error}
          action={<button type="button" onClick={reload}>Thử lại</button>}
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
                  need?.id && locationId ? (
                    <MerchantNeedCandidateForm
                      needId={need.id}
                      merchantId={merchantId}
                      merchantLocationId={locationId}
                      onCreated={reload}
                    />
                  ) : null
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
