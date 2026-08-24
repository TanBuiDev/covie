"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState, type FormEvent } from "react";
import { CatalogCard } from "@/features/catalog/catalog-card";
import { discoverCatalogItems, type CatalogDiscoveryFilters } from "@/features/catalog/catalog-api";
import { LocationChooser } from "@/features/location/location-chooser";
import { toDiscoveryLocation } from "@/features/location/location-context";
import { useBrowseLocation } from "@/features/location/use-browse-location";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import type { components } from "@covie/api-client";

type Page = components["schemas"]["CursorPageResponseCatalogItemSummaryResponse"];
type SearchType = "" | "PRODUCT" | "SERVICE";
type SearchSort = NonNullable<CatalogDiscoveryFilters["sort"]>;

export type SearchInitialFilters = {
  q?: string;
  type?: SearchType;
  categoryId?: string;
  sort?: SearchSort;
};

export function SearchResults({ initial }: { initial: SearchInitialFilters }) {
  const router = useRouter();
  const { location, hydrated } = useBrowseLocation();
  const [draftQ, setDraftQ] = useState(initial.q ?? "");
  const [draftType, setDraftType] = useState<SearchType>(initial.type ?? "");
  const [filters, setFilters] = useState<SearchInitialFilters>(initial);
  const [page, setPage] = useState<Page>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const discoveryLocation = useMemo(() => toDiscoveryLocation(location), [location]);

  useEffect(() => {
    if (!discoveryLocation) {
      return;
    }
    let cancelled = false;
    void Promise.resolve().then(() => {
      if (cancelled) return;
      setLoading(true);
      setError(false);
      return discoverCatalogItems({
      q: filters.q || undefined,
      type: filters.type || undefined,
      categoryId: filters.categoryId || undefined,
      sort: filters.sort ?? "RELEVANCE",
      limit: 20,
      location: discoveryLocation,
      });
    }).then((result) => {
      if (!result) return;
      if (!cancelled) setPage(result.page);
    }).catch(() => {
      if (!cancelled) setError(true);
    }).finally(() => {
      if (!cancelled) setLoading(false);
    });
    return () => { cancelled = true; };
  }, [discoveryLocation, filters]);

  const visiblePage = discoveryLocation ? page : undefined;

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next = { ...filters, q: draftQ.trim() || undefined, type: draftType || undefined };
    setFilters(next);
    const params = new URLSearchParams();
    if (next.q) params.set("q", next.q);
    if (next.type) params.set("type", next.type);
    if (next.categoryId) params.set("categoryId", next.categoryId);
    if (next.sort && next.sort !== "RELEVANCE") params.set("sort", String(next.sort));
    router.replace(`/search${params.size ? `?${params}` : ""}`, { scroll: false });
  }

  async function loadNext() {
    if (!discoveryLocation || !visiblePage?.hasMore || !visiblePage.nextCursor) return;
    setLoading(true);
    setError(false);
    try {
      const next = await discoverCatalogItems({
        q: filters.q || undefined,
        type: filters.type || undefined,
        categoryId: filters.categoryId || undefined,
        sort: filters.sort ?? "RELEVANCE",
        limit: 20,
        cursor: visiblePage.nextCursor,
        location: discoveryLocation,
      });
      setPage((current) => ({
        items: [...(current?.items ?? []), ...(next.page?.items ?? [])],
        hasMore: next.page?.hasMore,
        nextCursor: next.page?.nextCursor,
      }));
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  }

  if (!hydrated) return <LoadingState label="Đang đọc khu vực khám phá…" />;

  return (
    <section aria-labelledby="search-title">
      <h1 id="search-title">Khám phá quanh bạn</h1>
      <LocationChooser />
      <form onSubmit={submit} className="search-form" role="search">
        <label>Tìm kiếm <input value={draftQ} onChange={(event) => setDraftQ(event.target.value)} /></label>
        <label>Loại <select value={draftType} onChange={(event) => setDraftType(event.target.value as SearchType)}>
          <option value="">Tất cả</option>
          <option value="PRODUCT">Sản phẩm</option>
          <option value="SERVICE">Dịch vụ</option>
        </select></label>
        <button type="submit">Tìm kiếm</button>
      </form>

      {!location && <EmptyState title="Chọn khu vực để xem kết quả gần bạn" description="CoVie chỉ định vị khi bạn chủ động chọn hoặc cho phép GPS." />}
      {loading && <LoadingState />}
      {error && <ErrorState description="Không thể tải kết quả quanh khu vực này. Vui lòng thử lại." />}
      {location && !loading && !error && visiblePage?.items?.length === 0 && (
        <EmptyState title="Chưa có kết quả phù hợp" action={<Link href="/needs/new">Đăng nhu cầu cần mua</Link>} />
      )}
      <section className="catalog-grid" aria-label="Kết quả tìm kiếm">
        {visiblePage?.items?.map((item) => <CatalogCard key={item.id} item={item} location={discoveryLocation} />)}
      </section>
      {visiblePage?.hasMore && <button type="button" onClick={loadNext} disabled={loading}>Tải thêm</button>}
    </section>
  );
}
