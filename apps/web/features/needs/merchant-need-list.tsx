"use client";

import type { components } from "@covie/api-client";
import { useEffect, useRef, useState } from "react";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import { StatusChip } from "@/components/ui/status-chip";
import { countdownPresentation } from "@/lib/format/time";
import {
  CheckCircle,
  Clock,
  HourglassHigh,
  Lightning,
  Package,
  Target,
  Wrench,
  X,
} from "@phosphor-icons/react";
import { discoverMerchantNeeds, getMerchantNeedResponse, listEligibleNeedItems, type MerchantNeedMatch, type NeedEligibleItemPage, type NeedResponse } from "./need-api";
import { MerchantNeedResponseForm } from "./merchant-need-response-form";

type Location = components["schemas"]["MerchantLocationResponse"];

function distanceCopy(distanceMeters?: number) {
  if (distanceMeters == null) return undefined;
  if (distanceMeters < 1000) return `${Math.max(0, Math.round(distanceMeters))} m`;
  return `${(distanceMeters / 1000).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} km`;
}

export function MerchantNeedList({ merchantId, locations }: { merchantId: string; locations: Location[] }) {
  const firstLocationId = locations.find((location) => location.id)?.id ?? "";
  const [locationId, setLocationId] = useState(firstLocationId);
  const [items, setItems] = useState<MerchantNeedMatch[] | undefined>(() => firstLocationId ? undefined : []);
  const [nextCursor, setNextCursor] = useState<string>();
  const [error, setError] = useState<string>();
  const [reloadKey, setReloadKey] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const [selectedNeedId, setSelectedNeedId] = useState<string>();
  const [selectionMode, setSelectionMode] = useState<"create" | "existing">();
  const [candidatePage, setCandidatePage] = useState<NeedEligibleItemPage>();
  const [candidateError, setCandidateError] = useState<string>();
  const [candidateReloadKey, setCandidateReloadKey] = useState(0);
  const [loadingMoreCandidates, setLoadingMoreCandidates] = useState(false);
  const [existingResponse, setExistingResponse] = useState<NeedResponse>();
  const [responseError, setResponseError] = useState<string>();
  const [responseReloadKey, setResponseReloadKey] = useState(0);
  const candidateContextRef = useRef(0);
  const candidateLoadMoreRef = useRef(false);
  const feedContextRef = useRef(0);

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

  useEffect(() => {
    if (!locationId || !selectedNeedId || selectionMode !== "create") return;
    let active = true;
    void listEligibleNeedItems(merchantId, selectedNeedId, locationId, undefined)
      .then((page) => {
        if (!active) return;
        setCandidatePage(page);
        setCandidateError(undefined);
      })
      .catch((cause) => {
        if (active) setCandidateError(cause instanceof Error ? cause.message : "Không thể tải nội dung phù hợp.");
      });
    return () => { active = false; };
  }, [merchantId, locationId, selectedNeedId, selectionMode, candidateReloadKey]);

  useEffect(() => {
    if (!locationId || !selectedNeedId || selectionMode !== "existing") return;
    let active = true;
    void getMerchantNeedResponse(merchantId, selectedNeedId, locationId)
      .then((response) => {
        if (!active) return;
        setExistingResponse(response);
        setResponseError(undefined);
      })
      .catch((cause) => {
        if (!active) return;
        setResponseError(cause instanceof Error ? cause.message : "Không thể tải phản hồi đã gửi.");
      });
    return () => { active = false; };
  }, [merchantId, locationId, selectedNeedId, selectionMode, responseReloadKey]);

  function clearCandidateSelection() {
    candidateContextRef.current += 1;
    candidateLoadMoreRef.current = false;
    setSelectedNeedId(undefined);
    setSelectionMode(undefined);
    setCandidatePage(undefined);
    setCandidateError(undefined);
    setLoadingMoreCandidates(false);
    setExistingResponse(undefined);
    setResponseError(undefined);
  }

  function selectNeed(needId: string) {
    candidateContextRef.current += 1;
    candidateLoadMoreRef.current = false;
    const closing = selectionMode === "create" && selectedNeedId === needId;
    setSelectedNeedId(closing ? undefined : needId);
    setSelectionMode(closing ? undefined : "create");
    setCandidatePage(undefined);
    setCandidateError(undefined);
    setLoadingMoreCandidates(false);
    setExistingResponse(undefined);
    setResponseError(undefined);
  }

  function selectExistingResponse(needId: string) {
    candidateContextRef.current += 1;
    const closing = selectionMode === "existing" && selectedNeedId === needId;
    setSelectedNeedId(closing ? undefined : needId);
    setSelectionMode(closing ? undefined : "existing");
    setCandidatePage(undefined);
    setCandidateError(undefined);
    setExistingResponse(undefined);
    setResponseError(undefined);
  }

  function selectLocation(nextLocationId: string) {
    feedContextRef.current += 1;
    clearCandidateSelection();
    setLocationId(nextLocationId);
    setItems(nextLocationId ? undefined : []);
    setNextCursor(undefined);
    setError(undefined);
    setLoadingMore(false);
  }

  function reload() {
    feedContextRef.current += 1;
    clearCandidateSelection();
    setItems(undefined);
    setNextCursor(undefined);
    setError(undefined);
    setReloadKey((value) => value + 1);
  }

  async function loadMore() {
    if (!locationId || !nextCursor || loadingMore) return;
    setLoadingMore(true);
    setError(undefined);
    const context = feedContextRef.current;
    try {
      const page = await discoverMerchantNeeds(merchantId, locationId, nextCursor);
      if (context !== feedContextRef.current) return;
      setItems((current) => [...(current ?? []), ...(page.items ?? [])]);
      setNextCursor(page.nextCursor);
    } catch (cause) {
      if (context !== feedContextRef.current) return;
      setError(cause instanceof Error ? cause.message : "Không thể tải thêm nhu cầu.");
    } finally {
      if (context === feedContextRef.current) setLoadingMore(false);
    }
  }

  function retryCandidates() {
    candidateContextRef.current += 1;
    candidateLoadMoreRef.current = false;
    setCandidatePage(undefined);
    setCandidateError(undefined);
    setLoadingMoreCandidates(false);
    setCandidateReloadKey((current) => current + 1);
  }

  async function loadMoreCandidates() {
    if (!selectedNeedId || !locationId || !candidatePage?.nextCursor || candidateLoadMoreRef.current) return;
    candidateLoadMoreRef.current = true;
    setLoadingMoreCandidates(true);
    setCandidateError(undefined);
    const context = candidateContextRef.current;
    try {
      const page = await listEligibleNeedItems(merchantId, selectedNeedId, locationId, candidatePage.nextCursor);
      if (context !== candidateContextRef.current) return;
      setCandidatePage((current) => {
        if (!current) return page;
        const existing = new Set((current.items ?? []).map((offer) => offer.locationVariantId));
        return { ...page, items: [...(current.items ?? []), ...(page.items ?? []).filter((offer) => !existing.has(offer.locationVariantId))] };
      });
    } catch (cause) {
      if (context === candidateContextRef.current) {
        setCandidateError(cause instanceof Error ? cause.message : "Không thể tải thêm nội dung phù hợp.");
      }
    } finally {
      candidateLoadMoreRef.current = false;
      if (context === candidateContextRef.current) setLoadingMoreCandidates(false);
    }
  }

  if (!locations.length) {
    return <EmptyState title="Chưa có điểm bán để phản hồi" description="Tạo điểm bán và cấu hình phạm vi trước khi xem nhu cầu phù hợp." />;
  }

  const selectedMatch = items?.find((match) => match.need?.id === selectedNeedId && !match.alreadyResponded
    && (!match.need?.expiresAt || !countdownPresentation(match.need.expiresAt).expired));
  const selectedExistingMatch = items?.find((match) => match.need?.id === selectedNeedId && match.alreadyResponded);

  const totalNeeds = items?.length ?? 0;
  const urgentNeeds = items?.filter((m) => {
    if (!m.need?.expiresAt || m.alreadyResponded) return false;
    const exp = countdownPresentation(m.need.expiresAt);
    return !exp.expired && exp.urgent;
  }).length ?? 0;
  const respondedNeeds = items?.filter((m) => m.alreadyResponded).length ?? 0;

  return (
    <section className="seller-stack" aria-label="Nhu cầu phù hợp">
      <label className="seller-need-location-filter">
        Điểm bán đang phản hồi
        <select aria-label="Điểm bán đang phản hồi" required value={locationId} onChange={(event) => selectLocation(event.target.value)}>
          {locations.map((location) => location.id ? <option key={location.id} value={location.id}>{location.name ?? "Điểm bán"}</option> : null)}
        </select>
        <small>Chỉ dùng nội dung đang bán tại điểm bán này</small>
      </label>

      {items && items.length > 0 ? (
        <div className="seller-need-metrics-bar" role="region" aria-label="Tổng quan nhu cầu">
          <div className="seller-need-metric-card">
            <div className="seller-need-metric-card__icon" style={{ background: "rgba(4, 120, 87, 0.08)", color: "#047857" }}>
              <Target size={20} weight="duotone" />
            </div>
            <div className="seller-need-metric-card__info">
              <span className="seller-need-metric-card__value">{totalNeeds}</span>
              <span className="seller-need-metric-card__label">Nhu cầu quanh điểm bán</span>
            </div>
          </div>
          <div className="seller-need-metric-card">
            <div className="seller-need-metric-card__icon" style={{ background: "rgba(234, 88, 12, 0.1)", color: "#ea580c" }}>
              <Lightning size={20} weight="duotone" />
            </div>
            <div className="seller-need-metric-card__info">
              <span className="seller-need-metric-card__value">{urgentNeeds}</span>
              <span className="seller-need-metric-card__label">Khẩn cấp (sắp hết hạn)</span>
            </div>
          </div>
          <div className="seller-need-metric-card">
            <div className="seller-need-metric-card__icon" style={{ background: "rgba(37, 99, 235, 0.08)", color: "#2563eb" }}>
              <CheckCircle size={20} weight="duotone" />
            </div>
            <div className="seller-need-metric-card__info">
              <span className="seller-need-metric-card__value">{respondedNeeds}</span>
              <span className="seller-need-metric-card__label">Đã gửi phản hồi</span>
            </div>
          </div>
        </div>
      ) : null}

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
        <div className={`seller-need-workspace${selectionMode === "existing" ? " seller-need-workspace--existing" : ""}`}>
        <div className="seller-need-list">
          {items.map((match, index) => {
            const need = match.need;
            const key = need?.id ?? `${match.resolvedLocation?.id ?? "location"}-${index}`;
            const distance = distanceCopy(match.distanceMeters);
            const expiry = need?.expiresAt ? countdownPresentation(need.expiresAt) : undefined;
            return (
              <article className="seller-need-card" key={key}>
                <div className="seller-need-card__summary">
                  <div className="seller-need-card__type-badge" style={{ background: need?.type === "SERVICE" ? "rgba(4, 120, 87, 0.08)" : "rgba(71, 85, 105, 0.08)", color: need?.type === "SERVICE" ? "#047857" : "#475569" }}>
                    {need?.type === "SERVICE" ? <Wrench size={15} weight="duotone" /> : <Package size={15} weight="duotone" />}
                    <span>{need?.type === "SERVICE" ? "Dịch vụ" : "Sản phẩm"}</span>
                  </div>
                  <strong>{need?.title ?? "Nhu cầu"}</strong>
                  <small>{[match.buyerArea?.label, distance ? `cách điểm bán ${distance}` : null].filter(Boolean).join(" · ") || "Khu vực theo dữ liệu hệ thống"}</small>
                </div>

                {expiry ? (
                  <div className={`seller-need-expiry${expiry.urgent ? " seller-need-expiry--urgent" : ""}`} role="status">
                    <span aria-hidden="true">
                      {expiry.urgent ? <Clock size={16} weight="bold" /> : <HourglassHigh size={16} weight="bold" />}
                    </span>
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

                {!match.alreadyResponded && expiry?.expired ? (
                  <p className="seller-contract-note" role="status">
                    Thời gian hiển thị đã hết. Hãy tải lại danh sách để kiểm tra trạng thái nhu cầu từ hệ thống.
                    <button type="button" onClick={reload}>Tải lại nhu cầu</button>
                  </p>
                ) : null}
                {!match.alreadyResponded && !expiry?.expired && need?.id ? (
                  <button className="seller-order-open" type="button" aria-expanded={selectedNeedId === need.id}
                    aria-controls="seller-need-candidates" onClick={() => selectNeed(need.id!)}>
                    {selectedNeedId === need.id ? "Đóng phản hồi" : "Phản hồi"}
                    <span className="sr-only"> {need.id}</span>
                  </button>
                ) : null}
                {match.alreadyResponded && need?.id ? (
                  <button className="seller-order-open" type="button"
                    aria-expanded={selectionMode === "existing" && selectedNeedId === need.id}
                    aria-controls="seller-need-existing-response"
                    onClick={() => selectExistingResponse(need.id!)}>
                    {selectionMode === "existing" && selectedNeedId === need.id ? "Đóng phản hồi" : "Xem phản hồi"}
                    <span className="sr-only"> {need.id}</span>
                  </button>
                ) : null}
              </article>
            );
          })}
        </div>
        {selectedMatch && selectedNeedId ? (
          <section className="seller-need-candidates" id="seller-need-candidates" aria-label="Chọn nội dung phản hồi" aria-busy={!candidatePage && !candidateError}>
            <div className="seller-need-candidates__summary">
              <h2>{selectedMatch.need?.title ?? "Nhu cầu"}</h2>
              <p>{[selectedMatch.buyerArea?.label, distanceCopy(selectedMatch.distanceMeters)
                ? `cách điểm bán ${distanceCopy(selectedMatch.distanceMeters)}` : null].filter(Boolean).join(" · ")}</p>
              <small>Khu vực được chia sẻ ở mức cần thiết để đánh giá khả năng phục vụ.</small>
            </div>
            {!candidatePage && !candidateError ? <LoadingState label="Đang tải nội dung phù hợp…" /> : null}
            {candidateError && !candidatePage ? (
              <ErrorState title="Không thể tải nội dung phù hợp" description={candidateError}
                action={<button type="button" onClick={retryCandidates}>Thử tải lại nội dung</button>} />
            ) : null}
            {candidatePage?.items?.length === 0 ? (
              <EmptyState title="Chưa có nội dung phù hợp để chọn."
                description="Kiểm tra nội dung đang bán tại điểm bán này hoặc tải tiếp nếu còn kết quả." />
            ) : null}
            {candidatePage?.items?.length && candidatePage.maxResponseItems != null ? (
              <MerchantNeedResponseForm key={`${merchantId}:${selectedNeedId}:${locationId}`}
                needId={selectedNeedId} merchantLocationId={locationId}
                maxItems={candidatePage.maxResponseItems} offers={candidatePage.items}
                onCreated={reload} />
            ) : null}
            {candidatePage?.items?.length && candidatePage.maxResponseItems == null ? (
              <p role="alert">Không thể xác định giới hạn phản hồi. Hãy tải lại nội dung.</p>
            ) : null}
            {candidateError && candidatePage ? (
              <p role="alert">{candidateError} <button type="button" onClick={() => void loadMoreCandidates()}>Thử tải tiếp</button></p>
            ) : null}
            {candidatePage?.hasMore && candidatePage.nextCursor ? (
              <button className="seller-load-more" type="button" disabled={loadingMoreCandidates}
                onClick={() => void loadMoreCandidates()}>
                {loadingMoreCandidates ? "Đang tải nội dung…" : "Tải thêm nội dung"}
              </button>
            ) : null}
          </section>
        ) : null}
        {selectedExistingMatch && selectedNeedId ? (
          <section className="seller-need-existing" id="seller-need-existing-response"
            aria-label="Phản hồi đã gửi" aria-busy={!existingResponse && !responseError}>
            <div className="seller-need-existing__heading">
              <div>
                <p className="eyebrow">PHẢN HỒI ĐÃ GỬI</p>
                <h2>{selectedExistingMatch.need?.title ?? "Phản hồi đã gửi"}</h2>
              </div>
              <button type="button" onClick={() => selectExistingResponse(selectedNeedId)}>
                <X size={15} weight="bold" style={{ display: "inline-block", verticalAlign: "-2px", marginRight: 4 }} />
                Đóng
              </button>
            </div>
            {!existingResponse && !responseError ? <LoadingState label="Đang tải phản hồi đã gửi…" /> : null}
            {responseError && !existingResponse ? (
              <ErrorState title="Không thể tải phản hồi đã gửi" description={responseError}
                action={<button type="button" onClick={() => {
                  setResponseError(undefined);
                  setResponseReloadKey((current) => current + 1);
                }}>Thử lại</button>} />
            ) : null}
            {existingResponse ? (
              <div className="seller-need-existing__content">
                <div className="seller-need-existing__status" role="status">
                  <span aria-hidden="true" style={{ color: "var(--seller-brand, #047857)", display: "inline-flex" }}>
                    <CheckCircle size={22} weight="fill" />
                  </span>
                  <div>
                    <strong>{existingResponse.status === "ACTIVE" ? "Phản hồi đang hoạt động" : "Phản hồi đã rút"}</strong>
                    <p>{existingResponse.items.length} nội dung đang được gửi cho yêu cầu này.</p>
                  </div>
                </div>
                <div className="seller-need-existing__items" aria-label="Nội dung đã gửi">
                  {existingResponse.items.map((item) => (
                    <article className="seller-need-existing__item" key={item.id}>
                      <div className="seller-need-existing__thumb" aria-hidden="true">
                        {selectedExistingMatch.need?.type === "SERVICE" ? (
                          <Wrench size={18} weight="duotone" color="#047857" />
                        ) : (
                          <Package size={18} weight="duotone" color="#475569" />
                        )}
                      </div>
                      <div>
                        <strong>{item.catalogItemName || "Nội dung catalog"}</strong>
                        <small>{selectedExistingMatch.need?.type === "SERVICE" ? "Dịch vụ" : "Sản phẩm"} · {existingResponse.merchantLocationName || "Điểm bán"}</small>
                      </div>
                      <strong>{item.priceAtResponse.amount.toLocaleString("vi-VN")} đ</strong>
                      <span className="seller-need-existing__sent">
                        <CheckCircle size={14} weight="fill" style={{ marginRight: 4, display: "inline-block", verticalAlign: "-2px" }} />
                        Đã gửi
                      </span>
                    </article>
                  ))}
                </div>
                {existingResponse.note ? (
                  <div className="seller-need-existing__note">
                    <strong>Ghi chú</strong>
                    <p>{existingResponse.note}</p>
                  </div>
                ) : null}
              </div>
            ) : null}
          </section>
        ) : null}
        </div>
      ) : null}

      {error && items ? <p role="alert">{error}</p> : null}
      {nextCursor ? <button className="seller-load-more" type="button" disabled={loadingMore} onClick={() => void loadMore()}>{loadingMore ? "Đang tải…" : "Tải thêm"}</button> : null}
    </section>
  );
}
