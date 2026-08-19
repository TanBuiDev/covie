"use client";

import { useEffect, useState } from "react";
import { CatalogCard } from "@/features/catalog/catalog-card";
import { discoverCatalogItems, type CatalogDiscoveryFilters } from "@/features/catalog/catalog-api";
import { LocationInput } from "@/features/location/location-input";
import type { DiscoveryLocation } from "@/features/location/location-context";
import type { components } from "@covie/api-client";

type Page = components["schemas"]["CursorPageResponseCatalogItemSummaryResponse"];

export default function ExplorePage() {
  const [location, setLocation] = useState<DiscoveryLocation>();
  const [filters, setFilters] = useState<Omit<CatalogDiscoveryFilters, "location" | "cursor">>({ sort: "RELEVANCE", limit: 20 });
  const [page, setPage] = useState<Page>();
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string>();

  useEffect(() => {
    if (!location) return;
    let cancelled = false;
    // The effect owns the request lifecycle for the selected location/filter context.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoading(true);
    setMessage(undefined);
    discoverCatalogItems({ ...filters, location }).then((result) => {
      if (!cancelled) setPage(result.page);
    }).catch(() => { if (!cancelled) setMessage("Không thể tải danh mục quanh vị trí này."); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [location, filters]);

  async function loadNext() {
    if (!location || !page?.hasMore || !page.nextCursor) return;
    setLoading(true);
    try {
      const next = await discoverCatalogItems({ ...filters, location, cursor: page.nextCursor });
      setPage((current) => ({ items: [...(current?.items ?? []), ...(next.page?.items ?? [])], hasMore: next.page?.hasMore, nextCursor: next.page?.nextCursor }));
    } catch { setMessage("Không thể tải thêm danh mục."); } finally { setLoading(false); }
  }

  function updateFilter(name: "q" | "type" | "categoryId" | "sort", value: string) {
    setFilters((current) => ({ ...current, [name]: value || undefined }));
  }

  return <main>
    <h1>Khám phá quanh bạn</h1>
    <p>Chọn vị trí để CoVie tìm đúng địa điểm và giá bán gần bạn.</p>
    <LocationInput value={location} onChange={setLocation} />
    {location && <section aria-label="Bộ lọc danh mục">
      <label>Tìm kiếm <input value={filters.q ?? ""} onChange={(event) => updateFilter("q", event.target.value)} /></label>
      <label>Loại <select value={filters.type ?? ""} onChange={(event) => updateFilter("type", event.target.value)}><option value="">Tất cả</option><option value="PRODUCT">Sản phẩm</option><option value="SERVICE">Dịch vụ</option></select></label>
      <label>Danh mục <input value={filters.categoryId ?? ""} onChange={(event) => updateFilter("categoryId", event.target.value)} /></label>
      <label>Sắp xếp <select value={filters.sort ?? "RELEVANCE"} onChange={(event) => updateFilter("sort", event.target.value)}><option value="RELEVANCE">Liên quan</option><option value="DISTANCE">Gần nhất</option><option value="PRICE_ASC">Giá thấp đến cao</option><option value="PRICE_DESC">Giá cao đến thấp</option><option value="NEWEST">Mới nhất</option></select></label>
    </section>}
    {loading && <p role="status">Đang tải…</p>}
    {message && <p role="alert">{message}</p>}
    {location && !loading && page?.items?.length === 0 && <p>Chưa có kết quả phù hợp.</p>}
    <section aria-label="Kết quả danh mục">{page?.items?.map((item) => <CatalogCard key={item.id} item={item} location={location} />)}</section>
    {page?.hasMore && <button type="button" onClick={loadNext} disabled={loading}>Tải thêm</button>}
  </main>;
}
