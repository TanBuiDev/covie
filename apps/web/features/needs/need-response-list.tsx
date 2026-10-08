"use client";

import Link from "next/link";
import { CovieApiError } from "@/lib/api/api-error";
import { ErrorState } from "@/components/ui/error-state";
import { useEffect, useRef, useState } from "react";
import {
  listNeedAddresses,
  listNeedResponses,
  type NeedAddress,
  type NeedResponse,
} from "./need-api";
import styles from "./buyer-needs-mobile.module.css";

function money(amount?: number, currency?: string): string {
  if (amount == null || !currency) return "Liên hệ giá";
  try {
    return new Intl.NumberFormat("vi-VN", { style: "currency", currency, maximumFractionDigits: 0 }).format(amount);
  } catch {
    return `${amount.toLocaleString("vi-VN")} ${currency}`;
  }
}

export function NeedResponseList({ needId }: { needId: string }) {
  return <NeedResponseListState key={needId} needId={needId} />;
}

function NeedResponseListState({ needId }: { needId: string }) {
  const [responses, setResponses] = useState<NeedResponse[]>([]);
  const [authRequired, setAuthRequired] = useState(false);
  const [addresses, setAddresses] = useState<NeedAddress[]>([]);
  const [addressId, setAddressId] = useState("");
  const [addressError, setAddressError] = useState(false);
  const [loadingAddresses, setLoadingAddresses] = useState(true);
  const [addressAttempt, setAddressAttempt] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [nextCursor, setNextCursor] = useState<string>();
  const [loadingMore, setLoadingMore] = useState(false);
  const [moreError, setMoreError] = useState(false);
  const requestGeneration = useRef(0);
  const morePending = useRef(false);

  useEffect(() => {
    let active = true;
    const generation = ++requestGeneration.current;
    listNeedResponses(needId)
      .then(page => {
        if (!active) return;
        setResponses(page.items);
        setNextCursor(page.hasMore ? page.nextCursor ?? undefined : undefined);
        setLoadError(false);
      })
      .catch(cause => {
        if (active) {
          if (cause instanceof CovieApiError && cause.status === 401) setAuthRequired(true);
          setLoadError(true);
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; requestGeneration.current = generation + 1; };
  }, [needId, attempt]);

  useEffect(() => {
    let active = true;
    listNeedAddresses().then(savedAddresses => {
      if (!active) return;
      setAddresses(savedAddresses);
      setAddressId(savedAddresses.find(item => item.defaultAddress)?.id ?? savedAddresses[0]?.id ?? "");
      setAddressError(false);
    }).catch(cause => {
      if (active) {
        if (cause instanceof CovieApiError && cause.status === 401) setAuthRequired(true);
        setAddressError(true);
      }
    }).finally(() => {
      if (active) setLoadingAddresses(false);
    });
    return () => { active = false; };
  }, [addressAttempt]);

  async function loadMore() {
    if (!nextCursor || morePending.current || loading || authRequired) return;
    morePending.current = true;
    setLoadingMore(true);
    setMoreError(false);
    const generation = requestGeneration.current;
    try {
      const page = await listNeedResponses(needId, nextCursor);
      if (generation !== requestGeneration.current) return;
      setResponses(previous => {
        const existing = new Set(previous.map(response => response.id));
        return [...previous, ...page.items.filter(response => {
          if (existing.has(response.id)) return false;
          existing.add(response.id);
          return true;
        })];
      });
      setNextCursor(page.hasMore ? page.nextCursor ?? undefined : undefined);
    } catch (cause) {
      if (generation === requestGeneration.current) {
        if (cause instanceof CovieApiError && cause.status === 401) setAuthRequired(true);
        setMoreError(true);
      }
    } finally {
      if (generation === requestGeneration.current) {
        morePending.current = false;
        setLoadingMore(false);
      }
    }
  }

  if (authRequired) return <ErrorState
    title="Vui lòng đăng nhập lại"
    description="Phiên đăng nhập đã hết hạn hoặc bạn chưa đăng nhập."
    action={<Link className="button-primary" href={`/login?returnTo=${encodeURIComponent(`/needs/${encodeURIComponent(needId)}`)}`}>Đăng nhập lại</Link>}
  />;

  return (
    <section className={`need-responses ${styles.responses}`} aria-labelledby="need-responses-title">
      <div className={`need-responses__header ${styles.responsesHeader}`}>
        <div>
          <h2 id="need-responses-title">Phản hồi Cần mua</h2>
          <p>Giá và khả dụng được lấy trực tiếp từ phản hồi hiện tại của hệ thống.</p>
        </div>
        {addresses.length ? (
          <label className={`need-responses__address ${styles.address}`}>
            <span>Địa chỉ giao hàng</span>
            <select value={addressId} onChange={(event) => setAddressId(event.target.value)}>
              {addresses.map((address) => address.id ? (
                <option key={address.id} value={address.id}>{address.label ? `${address.label} · ` : ""}{address.addressText ?? "Địa chỉ đã lưu"}</option>
              ) : null)}
            </select>
          </label>
        ) : null}
      </div>

      {loading ? <p className={`need-responses__loading ${styles.loading}`}>Đang tải phản hồi…</p> : null}
      {addressError ? <div className="form-alert" role="alert">
        <p>Không thể tải địa chỉ giao hàng. Bạn vẫn có thể xem phản hồi.</p>
        <button className="button-secondary" type="button" disabled={loadingAddresses} onClick={() => {
          setAddressError(false); setLoadingAddresses(true); setAddressAttempt(value => value + 1);
        }}>Thử lại tải địa chỉ</button>
      </div> : null}
      {loadError ? <div className="form-alert" role="alert"><p>Không thể tải phản hồi. Vui lòng thử lại.</p><button type="button" disabled={loading} onClick={() => {setLoadError(false); setLoading(true); setAttempt(value => value + 1);}}>Thử lại tải phản hồi</button></div> : null}
      {!loading && !loadError && !responses.length ? <div className={`need-empty ${styles.state}`}><h3>Chưa có phản hồi phù hợp</h3><p>Phản hồi mới sẽ xuất hiện ở đây khi người bán gửi.</p></div> : null}

      <div className={`need-response-cards ${styles.responseCards}`}>
        {responses.map((response) => (
          <article className={`need-response-card ${styles.responseCard}`} key={response.id}>
            <div className={`need-response-card__merchant ${styles.merchant}`}>
              <div>
                <h3>{response.merchantName || "Người bán"}</h3>
                <p>{response.merchantLocationName || "Điểm bán"}{response.distanceMeters != null ? ` · ${(response.distanceMeters / 1000).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} km` : ""}</p>
              </div>
              <span className={`need-status need-status--${response.status.toLowerCase()} ${styles.merchantStatus}`}>{response.status === "ACTIVE" ? "Đang hoạt động" : "Đã rút"}</span>
            </div>
            {response.note ? <p className={`need-response-card__note ${styles.note}`}>{response.note}</p> : null}
            <div className={`need-response-card__items ${styles.responseItems}`}>
              {response.items.map((item) => {
                const price = item.currentPrice || item.priceAtResponse;
                return (
                  <div className={`need-response-item ${styles.responseItem}`} key={item.id}>
                    <div>
                      {item.catalogItemId && item.catalogItemName ? <Link href={`/items/${encodeURIComponent(item.catalogItemId)}`}>{item.catalogItemName}</Link> : <span>{item.catalogItemName || "Mục phản hồi"}</span>}
                      <strong>{money(price?.amount, price?.currency)}</strong>
                      <small>{item.available ? "Đang có sẵn" : "Hiện không có sẵn"}</small>
                    </div>
                    {item.actionable ? (
                      <div className={`need-response-item__actions ${styles.responseActions}`}>
                        <Link className="button-primary" href={`/checkout?${new URLSearchParams({source:"NEED_REQUEST",needRequestId:needId,needResponseItemId:item.id,quantity:"1",...(addressId ? {addressId} : {})})}`}>Xem lại và đặt hàng</Link>
                      </div>
                    ) : <span className={`need-response-item__unavailable ${styles.unavailable}`}>Không còn khả dụng</span>}
                  </div>
                );
              })}
            </div>
          </article>
        ))}
      </div>
      {moreError ? <p className="form-alert" role="alert">Không thể tải thêm phản hồi. Vui lòng thử lại.</p> : null}
      {nextCursor ? <button className="button-secondary" type="button" disabled={loadingMore}
        onClick={() => void loadMore()}>{loadingMore ? "Đang tải thêm phản hồi…" : moreError ? "Thử lại tải thêm phản hồi" : "Tải thêm phản hồi"}</button> : null}
    </section>
  );
}
