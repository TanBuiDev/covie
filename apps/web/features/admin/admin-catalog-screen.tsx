"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { ArrowsClockwise, ImageSquare } from "@phosphor-icons/react";
import { CovieApiError } from "@/lib/api/api-error";
import { fetchAdminCatalog, fetchAdminCatalogCases, fetchAdminCatalogDetail, type AdminCatalogDetail, type AdminCatalogItem } from "./admin-catalog-api";
import { openAdminCatalogCase } from "./admin-catalog-api";
import { AdminPageHeader, AdminStatusChip, AdminSurface, formatAdminDate, presentModerationStatus } from "./admin-ui";
import styles from "./admin-catalog-screen.module.css";
import { SocialVideo } from "@/features/marketplace-view/social-video";

const filterNames = ["q", "type", "listingKind", "listingStatus", "moderationStatus", "merchantId", "sort"] as const;
const listingLabels = { DRAFT: "Bản nháp", ACTIVE: "Đang bán", PAUSED: "Tạm dừng", DELETED: "Đã xóa" };

function errorMessage(error: unknown) {
  if (error instanceof CovieApiError) {
    if (error.status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
    if (error.status === 403) return "Bạn không có quyền xem nội dung quản trị.";
    if (error.status === 404) return "Nội dung không khả dụng hoặc chức năng chưa được bật.";
    if (error.status === 429) return "Bạn thao tác quá nhanh. Hãy thử lại sau.";
  }
  return error instanceof Error ? error.message : "Không thể tải nội dung. Hãy thử lại.";
}

function isAccessDenied(error: unknown) {
  return error instanceof CovieApiError && [401, 403].includes(error.status);
}

function CatalogImage({ url, name, large = false }: { url?: string; name: string; large?: boolean }) {
  const [failed, setFailed] = useState(false);
  return <span className={large ? styles.media : styles.thumbnail}>
    {url && !failed ? <Image src={url} alt={name} width={large ? 320 : 48} height={large ? 240 : 48}
      unoptimized onError={() => setFailed(true)} /> : <ImageSquare size={large ? 32 : 22} aria-label="Không có ảnh" />}
  </span>;
}

function CaseOpeningForm({ itemId, onConflict, onDenied }: {
  itemId: string; onConflict: (message: string) => void; onDenied: (message: string) => void;
}) {
  const router = useRouter();
  const [reasonCode, setReasonCode] = useState("");
  const [note, setNote] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const pending = useRef(false);
  const active = useRef(true);
  const request = useRef<{ body: string; key: string } | undefined>(undefined);
  useEffect(() => { active.current = true; return () => { active.current = false; }; }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!confirmed || !reasonCode.trim() || pending.current) return;
    const reason = { reasonCode: reasonCode.trim(), note: note.trim() || undefined };
    const body = JSON.stringify(reason);
    if (request.current?.body !== body) request.current = { body, key: crypto.randomUUID() };
    pending.current = true;
    setBusy(true);
    setError(undefined);
    try {
      const result = await openAdminCatalogCase(itemId, reason, request.current.key);
      if (active.current) router.replace("/admin/moderation/" + encodeURIComponent(result.caseId!));
    } catch (failure) {
      if (!active.current) return;
      const message = errorMessage(failure);
      setError(message);
      if (failure instanceof CovieApiError && failure.status === 409) onConflict(message);
      if (failure instanceof CovieApiError && [401, 403].includes(failure.status)) onDenied(message);
    } finally {
      pending.current = false;
      if (active.current) setBusy(false);
    }
  }

  return <form className={styles.caseOpening} onSubmit={event => void submit(event)}>
    <h3>Mở hồ sơ kiểm duyệt</h3>
    <label className="admin-field"><span>Mã lý do</span><input required maxLength={60} disabled={busy}
      value={reasonCode} onChange={event => { setReasonCode(event.target.value); setConfirmed(false); }} /></label>
    <label className="admin-field"><span>Ghi chú</span><textarea maxLength={2000} rows={3} disabled={busy}
      value={note} onChange={event => { setNote(event.target.value); setConfirmed(false); }} /></label>
    <label className={styles.confirmation}><input type="checkbox" checked={confirmed} disabled={busy}
      onChange={event => setConfirmed(event.target.checked)} /><span>Tôi xác nhận mở hồ sơ; thao tác này không đổi trạng thái nội dung.</span></label>
    {error && <p className="admin-error" role="alert">{error}</p>}
    <button className="admin-button admin-button--primary" disabled={busy || !confirmed || !reasonCode.trim()}>
      {busy ? "Đang mở hồ sơ…" : "Xác nhận mở hồ sơ"}</button>
  </form>;
}

export function AdminCatalogScreen({ caseOpeningEnabled = false }: { caseOpeningEnabled?: boolean }) {
  const router = useRouter();
  const pathname = usePathname();
  const search = useSearchParams();
  const params = new URLSearchParams();
  for (const name of filterNames) { const value = search.get(name); if (value) params.set(name, value); }
  const query = params.toString();
  const itemId = search.get("item");
  const [attempt, setAttempt] = useState(0);
  const [accessDenial, setAccessDenial] = useState<{ attempt: number; message: string }>();
  const denyAccess = useCallback((message: string) => {
    setAccessDenial(previous => previous && previous.attempt > attempt ? previous : { attempt, message });
  }, [attempt]);
  const requestKey = query + "|" + attempt;
  const [list, setList] = useState<{ key: string; items: AdminCatalogItem[]; total: number; page: number; error?: string }>({
    key: "", items: [], total: 0, page: 0,
  });
  const [detail, setDetail] = useState<{ key: string; data?: AdminCatalogDetail; error?: string; caseError?: string }>({ key: "" });
  const [loadingMore, setLoadingMore] = useState(false);
  const moreInFlight = useRef(false);
  const casesInFlight = useRef("");
  const [casesBusy, setCasesBusy] = useState("");
  const [openingNotice, setOpeningNotice] = useState<{ itemId: string; message: string }>();
  const detailKey = (itemId ?? "") + "|" + attempt;

  useEffect(() => {
    let active = true;
    void fetchAdminCatalog(query, 0).then(payload => {
      if (active) setList({ key: requestKey, items: payload.items ?? [], total: payload.total ?? 0, page: 0 });
    }).catch(error => {
      if (!active) return;
      if (isAccessDenied(error)) denyAccess(errorMessage(error));
      setList({ key: requestKey, items: [], total: 0, page: 0, error: errorMessage(error) });
    });
    return () => { active = false; };
  }, [query, requestKey, denyAccess]);

  useEffect(() => {
    if (!itemId) return;
    let active = true;
    void fetchAdminCatalogDetail(itemId).then(data => { if (active) setDetail({ key: detailKey, data }); })
      .catch(error => {
        if (!active) return;
        if (isAccessDenied(error)) denyAccess(errorMessage(error));
        setDetail({ key: detailKey, error: errorMessage(error) });
      });
    return () => { active = false; };
  }, [itemId, detailKey, denyAccess]);

  function navigate(next: URLSearchParams) { router.replace(pathname + (next.size ? "?" + next : ""), { scroll: false }); }
  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const fields = new FormData(event.currentTarget);
    const next = new URLSearchParams();
    for (const name of filterNames) { const value = String(fields.get(name) ?? "").trim(); if (value) next.set(name, value); }
    navigate(next);
  }
  function selectItem(id: string) { const next = new URLSearchParams(query); next.set("item", id); navigate(next); }

  async function loadMore() {
    if (moreInFlight.current || list.key !== requestKey) return;
    moreInFlight.current = true;
    setLoadingMore(true);
    try {
      const payload = await fetchAdminCatalog(query, list.page + 1);
      setList(previous => previous.key !== requestKey ? previous : { ...previous,
        items: [...previous.items, ...(payload.items ?? []).filter(candidate => !previous.items.some(entry => entry.id === candidate.id))],
        total: payload.total ?? previous.total, page: previous.page + 1, error: undefined });
    } catch (error) {
      if (isAccessDenied(error)) denyAccess(errorMessage(error));
      setList(previous => previous.key !== requestKey ? previous : { ...previous,
        items: error instanceof CovieApiError && [401, 403].includes(error.status) ? [] : previous.items, error: errorMessage(error) });
    } finally { moreInFlight.current = false; setLoadingMore(false); }
  }

  async function loadMoreCases() {
    const cursor = detail.data?.moderationCaseNextCursor;
    if (!itemId || !cursor || detail.key !== detailKey || casesInFlight.current === detailKey) return;
    casesInFlight.current = detailKey;
    setCasesBusy(detailKey);
    try {
      const payload = await fetchAdminCatalogCases(itemId, cursor);
      setDetail(previous => previous.key !== detailKey || !previous.data ? previous : {
        ...previous, caseError: undefined, data: { ...previous.data,
          moderationCases: [...(previous.data.moderationCases ?? []), ...(payload.items ?? [])
            .filter(candidate => !previous.data?.moderationCases?.some(entry => entry.id === candidate.id))],
          moderationCaseTotal: payload.total ?? previous.data.moderationCaseTotal,
          moderationCaseNextCursor: payload.hasMore ? payload.nextCursor : undefined,
        },
      });
    } catch (error) {
      if (isAccessDenied(error)) denyAccess(errorMessage(error));
      setDetail(previous => previous.key !== detailKey ? previous
        : error instanceof CovieApiError && [401, 403].includes(error.status)
          ? { key: detailKey, error: errorMessage(error) } : { ...previous, caseError: errorMessage(error) });
    } finally {
      if (casesInFlight.current === detailKey) casesInFlight.current = "";
      setCasesBusy(previous => previous === detailKey ? "" : previous);
    }
  }

  const ready = list.key === requestKey;
  const selected = detail.key === detailKey ? detail.data : undefined;
  if (accessDenial?.attempt === attempt) return <div className="admin-page-stack">
    <AdminPageHeader title="Nội dung đăng bán" subtitle="CoVie Admin · Quản trị vận hành" />
    <AdminSurface><p className="admin-error" role="alert">{accessDenial.message}</p>
      <button className="admin-button admin-button--secondary" onClick={() => setAttempt(value => value + 1)}>Thử lại</button>
    </AdminSurface>
  </div>;
  return <div className="admin-page-stack">
    <AdminPageHeader title="Nội dung đăng bán" subtitle="CoVie Admin · Quản trị vận hành" />
    <AdminSurface>
      <form key={query} className={styles.filters} onSubmit={applyFilters}>
        <label className="admin-field"><span>Tìm nội dung</span><input name="q" defaultValue={search.get("q") ?? ""} maxLength={220} placeholder="Tên, mã nội dung hoặc cửa hàng" /></label>
        <label className="admin-field"><span>Loại nội dung</span><select name="type" defaultValue={search.get("type") ?? ""}><option value="">Tất cả</option><option value="PRODUCT">Sản phẩm</option><option value="SERVICE">Dịch vụ</option></select></label>
        <label className="admin-field"><span>Kiểu tin</span><select name="listingKind" defaultValue={search.get("listingKind") ?? ""}><option value="">Tất cả</option><option value="STANDARD">Thông thường</option><option value="RESALE">Thanh lý</option></select></label>
        <label className="admin-field"><span>Trạng thái bán</span><select name="listingStatus" defaultValue={search.get("listingStatus") ?? ""}><option value="">Tất cả</option>{Object.entries(listingLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <label className="admin-field"><span>Kiểm duyệt</span><select name="moderationStatus" defaultValue={search.get("moderationStatus") ?? ""}><option value="">Tất cả</option>{["NOT_REQUIRED", "PENDING", "APPROVED", "REJECTED", "HIDDEN"].map(value => <option key={value} value={value}>{presentModerationStatus(value).label}</option>)}</select></label>
        <label className="admin-field"><span>Mã cửa hàng</span><input name="merchantId" defaultValue={search.get("merchantId") ?? ""} placeholder="UUID cửa hàng" /></label>
        <label className="admin-field"><span>Sắp xếp</span><select name="sort" defaultValue={search.get("sort") ?? "UPDATED_DESC"}><option value="UPDATED_DESC">Mới cập nhật</option><option value="CREATED_DESC">Mới tạo</option><option value="CREATED_ASC">Tạo trước</option></select></label>
        <div className={styles.actions}><button className="admin-button admin-button--primary" type="submit">Áp dụng</button><button className="admin-button admin-button--secondary" type="button" onClick={() => navigate(new URLSearchParams())}>Xóa bộ lọc</button></div>
      </form>
    </AdminSurface>
    <div className={styles.toolbar}><h2>Nội dung gần đây</h2><button className="admin-button admin-button--secondary" onClick={() => setAttempt(value => value + 1)}><ArrowsClockwise size={16} />Tải lại</button></div>
    {!ready ? <AdminSurface><p role="status">Đang tải nội dung…</p></AdminSurface> : <>
      {list.error && <AdminSurface><p className="admin-error" role="alert">{list.error}</p><button className="admin-button admin-button--secondary" disabled={loadingMore} onClick={() => list.items.length ? void loadMore() : setAttempt(value => value + 1)}>Thử lại</button></AdminSurface>}
      {!list.error && list.items.length === 0 && <AdminSurface><p>Không có nội dung phù hợp.</p></AdminSurface>}
      {list.items.length > 0 && <AdminSurface className={styles.tableSurface}>
        <div className={styles.tableScroll}><table className={styles.table}><thead><tr><th>Nội dung</th><th>Cửa hàng</th><th>Trạng thái</th><th>Kiểm duyệt</th><th>Cập nhật</th><th>Chi tiết</th></tr></thead>
          <tbody>{list.items.map(entry => { const moderation = presentModerationStatus(entry.moderationStatus); return <tr key={entry.id}>
            <td><div className={styles.identity}><CatalogImage key={entry.primaryMedia?.id ?? entry.id} url={entry.primaryMedia?.url} name={entry.name ?? "Ảnh nội dung"} /><div><strong>{entry.name}</strong><small>{entry.listingKind === "RESALE" ? "Thanh lý" : entry.type === "SERVICE" ? "Dịch vụ" : "Sản phẩm"} · {entry.id}</small>{typeof entry.hasCaption === "boolean" && <small>{entry.hasCaption ? "Có caption" : "Chưa có caption"}</small>}{typeof entry.hasVideo === "boolean" && <small>{entry.hasVideo ? "Có video" : "Chưa có video"}</small>}</div></div></td>
            <td>{entry.merchantName}</td><td>{entry.listingStatus ? listingLabels[entry.listingStatus] : "Trạng thái đã được cập nhật"}</td>
            <td><AdminStatusChip label={moderation.label} tone={moderation.tone} /></td><td>{formatAdminDate(entry.updatedAt)}</td>
            <td><button className="admin-button admin-button--secondary" aria-label={"Xem " + entry.name} onClick={() => entry.id && selectItem(entry.id)}>Xem</button></td>
          </tr>; })}</tbody></table></div>
        <div className={styles.footer}><span>{list.items.length} / {list.total} nội dung</span>{list.items.length < list.total && <button className="admin-button admin-button--secondary" disabled={loadingMore} onClick={() => void loadMore()}>{loadingMore ? "Đang tải…" : "Xem thêm"}</button>}</div>
      </AdminSurface>}
      {itemId && !list.error && <AdminSurface>
        <div className={styles.toolbar}><h2>Chi tiết nội dung</h2><button className="admin-button admin-button--secondary" onClick={() => navigate(new URLSearchParams(query))}>Đóng</button></div>
        {detail.key !== detailKey ? <p role="status">Đang tải chi tiết…</p> : detail.error ? <p role="alert" className="admin-error">{detail.error}</p> : selected && <>
          <div className={styles.detailGrid}><div><h3>{selected.item?.name}</h3><p>{selected.item?.merchantName} · {selected.item?.categoryName}</p>
            <h3>Mô tả thương mại</h3><p className={styles.text}>{selected.description || "Chưa có mô tả."}</p>
            <h3>Nội dung hiển thị trên Khám phá</h3><p className={styles.text}>{selected.socialCaption || "Chưa có caption."}</p>
            <div className={styles.gallery}>{selected.media?.filter(media => !media.contentType?.startsWith("video/") || process.env.NEXT_PUBLIC_COVIE_SOCIAL_V2_VIDEO_ENABLED === "true")
              .map(media => <div key={media.id}>{media.contentType?.startsWith("video/") ? <div className={styles.media}>
                {media.status === "READY" && media.url ? <SocialVideo key={media.id} url={media.url} name={selected.item?.name ?? "Nội dung Catalog"} />
                  : <span aria-label="Video chưa sẵn sàng">Video chưa sẵn sàng</span>}
              </div> : <CatalogImage key={media.id} url={media.url} name={selected.item?.name ?? "Ảnh nội dung"} large />}
              <small>{media.contentType} · {media.status === "READY" ? "Sẵn sàng" : "Chưa sẵn sàng"}</small></div>)}</div>
            {selected.locations?.map(location => <p key={location.id}>{location.name} · {location.status}</p>)}</div>
            <div><h3>Kiểm duyệt &amp; trạng thái</h3><p>Tải lại nội dung sau mỗi thao tác để xem trạng thái mới nhất.</p>
              {!selected.moderationCases?.length ? <p>Chưa có hồ sơ kiểm duyệt cho nội dung này.</p> : <ul className={styles.cases}>{selected.moderationCases.map(moderationCase => <li key={moderationCase.id}>
                <AdminStatusChip {...presentModerationStatus(moderationCase.status)} /><span>{moderationCase.reasonCode}</span>
                <Link className="admin-inline-link" href={"/admin/moderation/" + encodeURIComponent(moderationCase.id ?? "")}>Mở hồ sơ {moderationCase.id}</Link>
              </li>)}</ul>}
              {(selected.moderationCaseTotal ?? 0) > (selected.moderationCases?.length ?? 0) && <p>Đang hiển thị {selected.moderationCases?.length} / {selected.moderationCaseTotal} hồ sơ gần đây.</p>}
              {detail.caseError && <div><p role="alert" className="admin-error">{detail.caseError}</p><button className="admin-button admin-button--secondary" disabled={casesBusy === detailKey} onClick={() => void loadMoreCases()}>Thử lại hồ sơ</button></div>}
              {selected.moderationCaseNextCursor && !detail.caseError && <button className="admin-button admin-button--secondary" disabled={casesBusy === detailKey} onClick={() => void loadMoreCases()}>{casesBusy === detailKey ? "Đang tải hồ sơ…" : "Xem thêm hồ sơ"}</button>}
              {openingNotice?.itemId === itemId && <p role="alert" className="admin-error">{openingNotice.message} Đã yêu cầu tải lại trạng thái từ máy chủ.</p>}
              {caseOpeningEnabled && <CaseOpeningForm key={detailKey} itemId={itemId} onConflict={message => {
                setOpeningNotice({ itemId, message }); setAttempt(value => value + 1);
              }} onDenied={message => {
                denyAccess(message);
                setDetail({ key: detailKey, error: message });
                setList(previous => ({ ...previous, items: [], error: message }));
              }} />}
              <Link className="admin-inline-link" href="/admin/reports">Hàng đợi báo cáo</Link>
            </div></div>
        </>}
      </AdminSurface>}
    </>}
  </div>;
}
