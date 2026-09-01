"use client";
import Link from "next/link";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState, type FormEvent } from "react";
import { discoverCatalogItems, listPublicCategories, type CatalogDiscoveryFilters } from "@/features/catalog/catalog-api";
import { toDiscoveryLocation } from "@/features/location/location-context";
import { useBrowseLocation } from "@/features/location/use-browse-location";
import { CoVieIcon } from "@/components/ui/covie-icon";
import type { components } from "@covie/api-client";
import styles from "./search-results.module.css";
import categoryStyles from "./category-state.module.css";

type Page = components["schemas"]["CursorPageResponseCatalogItemSummaryResponse"];
type Item = components["schemas"]["CatalogItemSummaryResponse"];
type SearchType = "" | "PRODUCT" | "SERVICE";
type ListingKind = "" | "RESALE";
type SearchSort = NonNullable<CatalogDiscoveryFilters["sort"]>;
export type SearchInitialFilters = { q?: string; type?: SearchType; categoryId?: string; sort?: SearchSort; listingKind?: ListingKind; radiusMeters?: number; openNow?: boolean };
const hasIntent = (f: SearchInitialFilters) => Boolean(f.q || f.type || f.categoryId || f.listingKind || f.radiusMeters || f.openNow);
const price = (item: Item) => item.priceFrom?.amount == null ? "Giá liên hệ" : `${new Intl.NumberFormat("vi-VN").format(item.priceFrom.amount)} ₫`;

function ResultCard({ item }: { item: Item }) {
  return <article className={styles.card}>
    <Link className={styles.media} href={`/items/${item.id}`} aria-label={`Xem ${item.name ?? "sản phẩm"}`}>
      {item.primaryMedia?.url ? <Image src={item.primaryMedia.url} alt="" fill sizes="112px" unoptimized /> : <span aria-hidden="true">CoVie</span>}<small>{item.type === "SERVICE" ? "Dịch vụ" : "Sản phẩm"}</small>
    </Link>
    <div className={styles.cardBody}><h2><Link href={`/items/${item.id}`}>{item.name ?? "Sản phẩm"}</Link></h2><strong>{price(item)}</strong>
      <p>● {item.resolvedLocation?.distanceMeters != null ? `${Math.round(item.resolvedLocation.distanceMeters)} m · ` : ""}{item.resolvedLocation?.name ?? "Chưa xác định vị trí"}</p><p>● {item.merchant?.name ?? "Người bán CoVie"}</p>
    </div>
  </article>;
}

export function SearchResults({ initial }: { initial: SearchInitialFilters }) {
  const router = useRouter(); const { location, hydrated } = useBrowseLocation();
  const [draftQ, setDraftQ] = useState(initial.q ?? ""); const [filters, setFilters] = useState(initial);
  const [sheet, setSheet] = useState(false); const [draftFilter, setDraftFilter] = useState(initial);
  const [page, setPage] = useState<Page>(); const [loading, setLoading] = useState(hasIntent(initial));
  const [error, setError] = useState(false); const [attempt, setAttempt] = useState(0);
  const [categoryName, setCategoryName] = useState("Danh mục");
  const discoveryLocation = useMemo(() => toDiscoveryLocation(location), [location]); const searching = hasIntent(filters);
  const categoryMode = Boolean(filters.categoryId);
  useEffect(() => {
    if (!filters.categoryId) return; let cancelled = false;
    listPublicCategories().then(categories => {
      const match = categories.find(category => category.id === filters.categoryId);
      if (!cancelled) setCategoryName(match?.name ?? "Danh mục");
    }).catch(() => { if (!cancelled) setCategoryName("Danh mục"); });
    return () => { cancelled = true; };
  }, [filters.categoryId]);
  useEffect(() => {
    if (!discoveryLocation || !searching) return; let cancelled = false;
    discoverCatalogItems({ q: filters.q || undefined, type: filters.type || undefined, categoryId: filters.categoryId || undefined, listingKind: filters.listingKind || undefined, radiusMeters: filters.radiusMeters, openNow: filters.openNow || undefined, sort: filters.sort === "RELEVANCE" && !filters.q ? "DISTANCE" : filters.sort ?? "RELEVANCE", limit: 20, location: discoveryLocation })
      .then(result => { if (!cancelled) { setPage(result.page); setError(false); } }).catch(() => { if (!cancelled) setError(true); }).finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [attempt, discoveryLocation, filters, searching]);
  function navigate(next: SearchInitialFilters) { const p = new URLSearchParams(); Object.entries(next).forEach(([k,v]) => { if (v !== undefined && v !== "" && v !== false && !(k === "sort" && v === "RELEVANCE")) p.set(k,String(v)); }); router.replace(`/search${p.size ? `?${p}` : ""}`, { scroll:false }); }
  function update(next: SearchInitialFilters) { setLoading(hasIntent(next)); setError(false); setFilters(next); navigate(next); }
  function submit(e: FormEvent) { e.preventDefault(); update({ ...filters, q: draftQ.trim() || undefined }); }
  if (!hydrated) return <div className={styles.center}>Đang đọc khu vực khám phá…</div>;
  return <section className={styles.screen}>
    <header className={styles.appBar}><button type="button" onClick={() => router.back()} aria-label="Quay lại"><CoVieIcon name="back" /></button><h1>{categoryMode ? categoryName : searching ? "Kết quả tìm kiếm" : "Khám phá"}</h1></header>
    <div className={styles.content}>
      <form className={styles.searchBlock} role="search" onSubmit={submit}><p className={styles.location}><CoVieIcon name="location" /><span>{location?.label ?? "Chọn vị trí"}</span></p><div className={styles.searchRow}><label className={styles.searchInput}><CoVieIcon name="search" /><input type="search" aria-label={categoryMode ? `Tìm trong ${categoryName}` : "Tìm món đồ, dịch vụ"} placeholder={categoryMode ? `Tìm trong ${categoryName}` : "Tìm món đồ, dịch vụ"} value={draftQ} onChange={e => setDraftQ(e.target.value)} /></label><button className={styles.filterButton} type="button" aria-label="Mở bộ lọc" onClick={() => { setDraftFilter(filters); setSheet(true); }}><CoVieIcon name="filter" /></button></div></form>
      {!searching && <div className={styles.hint}><strong>Tìm quanh vị trí bạn đã chọn</strong><p>Nhập từ khóa để tìm sản phẩm, dịch vụ hoặc đồ thanh lý. Vị trí không được đưa vào URL tìm kiếm.</p></div>}
      {searching && <>
        <div className={categoryStyles.filterBar}>
          <div className={styles.chips}>
            <button className={categoryMode ? categoryStyles.selectedChip : undefined} aria-label={categoryMode ? "Danh mục đã chọn" : undefined} onClick={() => setSheet(true)}>{categoryMode ? "✓ Danh mục" : "Danh mục⌄"}</button>
            <button onClick={() => setSheet(true)}>Khoảng cách⌄</button>
            {categoryMode && <><button onClick={() => setSheet(true)}>Giao nhận</button><button onClick={() => setSheet(true)}>Giá</button><button onClick={() => setSheet(true)}>Loại</button></>}
          </div>
          <button className={categoryStyles.filterAction} onClick={() => setSheet(true)}><CoVieIcon name="filter" />Bộ lọc</button>
        </div>
        <label className={`${styles.sort} ${categoryMode ? categoryStyles.categorySort : ""}`}>Sắp xếp
          <select aria-label="Sắp xếp" value={filters.sort ?? "RELEVANCE"} onChange={e => update({ ...filters, sort:e.target.value as SearchSort })}><option value="RELEVANCE">Phù hợp nhất</option><option value="DISTANCE">Gần nhất</option><option value="PRICE_ASC">Giá thấp nhất</option><option value="PRICE_DESC">Giá cao nhất</option><option value="NEWEST">Mới nhất</option></select>
        </label>
      </>}
      {!location && <div className={styles.center}><strong>Chọn khu vực để xem kết quả gần bạn</strong></div>}
      {loading && searching && <div className={styles.list} role="status" aria-label="Đang tải kết quả">{[1,2,3].map(n => <div className={styles.skeleton} key={n}/>)}</div>}
      {error && <div className={styles.center}><strong>Không thể tải kết quả tìm kiếm.</strong><p>Kiểm tra kết nối rồi thử lại.</p><button onClick={() => { setLoading(true); setAttempt(v=>v+1); }}>Thử lại tìm kiếm</button></div>}
      {!loading && !error && searching && page?.items?.length === 0 && <div className={styles.center}><strong>Chưa tìm thấy kết quả phù hợp</strong><p>Thử từ khóa hoặc bộ lọc khác.</p><Link href="/needs/new">Đăng nhu cầu cần mua</Link></div>}
      {!loading && !error && <div className={styles.list}>{page?.items?.map(item => <ResultCard key={item.id} item={item}/>)}</div>}
    </div>
    {sheet && <div className={styles.backdrop} onMouseDown={() => setSheet(false)}><section className={styles.sheet} role="dialog" aria-modal="true" aria-label="Bộ lọc tìm kiếm" onMouseDown={e=>e.stopPropagation()}><header><h2>Bộ lọc</h2><button aria-label="Đóng bộ lọc" onClick={() => setSheet(false)}><CoVieIcon name="close" /></button></header>
      <label>Loại<select aria-label="Loại" value={draftFilter.type ?? ""} onChange={e=>setDraftFilter({...draftFilter,type:e.target.value as SearchType})}><option value="">Tất cả</option><option value="PRODUCT">Sản phẩm</option><option value="SERVICE">Dịch vụ</option></select></label>
      <label>Loại tin<select aria-label="Loại tin" value={draftFilter.listingKind ?? ""} onChange={e=>setDraftFilter({...draftFilter,listingKind:e.target.value as ListingKind})}><option value="">Tất cả</option><option value="RESALE">Thanh lý</option></select></label>
      <label>Khoảng cách<select aria-label="Khoảng cách" value={draftFilter.radiusMeters ?? ""} onChange={e=>setDraftFilter({...draftFilter,radiusMeters:Number(e.target.value)||undefined})}><option value="">Mọi khoảng cách</option><option value="1000">1 km</option><option value="5000">5 km</option><option value="10000">10 km</option></select></label>
      <label className={styles.check}><input type="checkbox" checked={draftFilter.openNow ?? false} onChange={e=>setDraftFilter({...draftFilter,openNow:e.target.checked})}/>Đang mở cửa</label><button className={styles.apply} onClick={() => { setSheet(false); update(draftFilter); }}>Áp dụng</button>
    </section></div>}
  </section>;
}
