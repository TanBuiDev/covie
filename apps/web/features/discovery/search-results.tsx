"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Fragment, useEffect, useMemo, useRef, useState, type FormEvent, type KeyboardEvent } from "react";
import { AdSlot } from "@/features/ads/ad-slot";
import { discoverCatalogItems, listPublicCategories, type CatalogDiscoveryFilters } from "@/features/catalog/catalog-api";
import { toDiscoveryLocation } from "@/features/location/location-context";
import { useBrowseLocation } from "@/features/location/use-browse-location";
import { LocationChooser } from "@/features/location/location-chooser";
import { CategoryBadge } from "@/components/ui/category-icon";
import {
  ArrowLeft,
  MagnifyingGlass,
  MapPin,
  SlidersHorizontal,
  X,
  CaretDown,
  Check,
  Tag,
  Bicycle,
  Clock,
  ArrowCounterClockwise,
  Sparkle,
  Package,
  Wrench,
} from "@phosphor-icons/react";
import type { components } from "@covie/api-client";
import { SelectDropdown, type DropdownOption } from "@/components/ui/select-dropdown";
import styles from "./search-results.module.css";
import categoryStyles from "./category-state.module.css";
import { MarketplaceProductCard } from "@/features/marketplace-view/marketplace-product-card";
import { SocialFeedProvider } from "@/features/marketplace-view/social-feed";
import { MarketplaceViewToggle } from "@/features/marketplace-view/marketplace-view-toggle";
import { useMarketplaceView } from "@/features/marketplace-view/use-marketplace-view";
import viewStyles from "@/features/marketplace-view/marketplace-view.module.css";

type Page = components["schemas"]["CursorPageResponseCatalogItemSummaryResponse"];
type SearchType = "" | "PRODUCT" | "SERVICE";
type ListingKind = "" | "RESALE";
type SearchSort = NonNullable<CatalogDiscoveryFilters["sort"]>;

const SORT_OPTIONS: DropdownOption<SearchSort>[] = [
  { value: "RELEVANCE", label: "Phù hợp nhất" },
  { value: "DISTANCE", label: "Gần nhất" },
  { value: "PRICE_ASC", label: "Giá thấp nhất" },
  { value: "PRICE_DESC", label: "Giá cao nhất" },
  { value: "NEWEST", label: "Mới nhất" },
];
export type SearchInitialFilters = {
  q?: string;
  type?: SearchType;
  categoryId?: string;
  sort?: SearchSort;
  listingKind?: ListingKind;
  radiusMeters?: number;
  openNow?: boolean;
  minPrice?: number;
  maxPrice?: number;
  pickup?: boolean;
  canServe?: boolean;
  shipping?: string;
};

const hasIntent = (f: SearchInitialFilters) =>
  Boolean(
    f.q ||
      f.type ||
      f.categoryId ||
      f.listingKind ||
      f.radiusMeters ||
      f.openNow ||
      f.minPrice != null ||
      f.maxPrice != null ||
      f.pickup ||
      f.canServe ||
      f.shipping,
  );

export function SearchResults({ initial }: { initial: SearchInitialFilters }) {
  const queryKey = JSON.stringify([
    initial.q ?? "",
    initial.type ?? "",
    initial.categoryId ?? "",
    initial.sort ?? "RELEVANCE",
    initial.listingKind ?? "",
    initial.radiusMeters ?? null,
    initial.openNow ?? false,
    initial.minPrice ?? null,
    initial.maxPrice ?? null,
    initial.pickup ?? false,
    initial.canServe ?? false,
    initial.shipping ?? "",
  ]);
  return <SearchResultsState key={queryKey} initial={initial} />;
}

function SearchResultsState({ initial }: { initial: SearchInitialFilters }) {
  const router = useRouter();
  const { location, hydrated } = useBrowseLocation();
  const [draftQ, setDraftQ] = useState(initial.q ?? "");
  const [filters, setFilters] = useState(initial);
  const [sheet, setSheet] = useState(false);
  const [draftFilter, setDraftFilter] = useState(initial);
  const [page, setPage] = useState<Page>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const [moreError, setMoreError] = useState(false);
  const [showLocationModal, setShowLocationModal] = useState(false);
  const { mode: viewMode, changeMode: setViewMode } = useMarketplaceView("catalog");
  const requestGeneration = useRef(0);
  const dialogRef = useRef<HTMLElement>(null);
  const [categoryName, setCategoryName] = useState("Danh mục");
  const [categories, setCategories] = useState<components["schemas"]["CategorySummary"][]>([]);
  const [categoryError, setCategoryError] = useState(false);

  // Sidebar local price inputs
  const [sidebarMinPrice, setSidebarMinPrice] = useState(
    filters.minPrice != null ? String(filters.minPrice) : "",
  );
  const [sidebarMaxPrice, setSidebarMaxPrice] = useState(
    filters.maxPrice != null ? String(filters.maxPrice) : "",
  );

  // Search history state
  const [recentSearches, setRecentSearches] = useState<string[]>([]);
  const [searchFocused, setSearchFocused] = useState(false);

  function handleSearchFocus() {
    setSearchFocused(true);
    try {
      const saved = localStorage.getItem("covie_search_history");
      if (saved) {
        setRecentSearches(JSON.parse(saved).slice(0, 6));
      }
    } catch {}
  }

  function saveRecentSearch(keyword: string) {
    const trimmed = keyword.trim();
    if (!trimmed) return;
    try {
      const next = [trimmed, ...recentSearches.filter((k) => k !== trimmed)].slice(0, 6);
      setRecentSearches(next);
      localStorage.setItem("covie_search_history", JSON.stringify(next));
    } catch {}
  }

  function removeRecentSearch(keyword: string) {
    try {
      const next = recentSearches.filter((k) => k !== keyword);
      setRecentSearches(next);
      localStorage.setItem("covie_search_history", JSON.stringify(next));
    } catch {}
  }

  function clearAllRecentSearches() {
    try {
      setRecentSearches([]);
      localStorage.removeItem("covie_search_history");
    } catch {}
  }

  const discoveryLocation = useMemo(() => toDiscoveryLocation(location), [location]);
  const searching = hasIntent(filters);
  const browsing = Boolean(discoveryLocation);
  const categoryMode = Boolean(filters.categoryId);

  const activeFilterCount = useMemo(() => {
    let count = 0;
    if (filters.categoryId) count++;
    if (filters.type) count++;
    if (filters.listingKind) count++;
    if (filters.radiusMeters) count++;
    if (filters.openNow) count++;
    if (filters.minPrice != null || filters.maxPrice != null) count++;
    if (filters.pickup) count++;
    if (filters.canServe) count++;
    if (filters.shipping) count++;
    return count;
  }, [filters]);

  const invalidPriceRange =
    [draftFilter.minPrice, draftFilter.maxPrice].some(
      (value) => value != null && (!Number.isSafeInteger(value) || value < 0),
    ) ||
    (draftFilter.minPrice != null &&
      draftFilter.maxPrice != null &&
      draftFilter.minPrice > draftFilter.maxPrice);

  useEffect(() => {
    if (!sheet) return;
    const previousFocus =
      document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    dialogRef.current?.querySelector<HTMLElement>("button:not(:disabled)")?.focus();
    return () => {
      document.body.style.overflow = previousOverflow;
      if (previousFocus?.isConnected) previousFocus.focus();
    };
  }, [sheet]);

  function handleDialogKey(event: KeyboardEvent<HTMLElement>) {
    if (event.key === "Escape") {
      event.preventDefault();
      setSheet(false);
      return;
    }
    if (event.key !== "Tab") return;
    const controls = Array.from(
      dialogRef.current?.querySelectorAll<HTMLElement>(
        'button:not(:disabled), input:not(:disabled):not([type="hidden"]), select:not(:disabled), textarea:not(:disabled), [tabindex="0"]',
      ) ?? [],
    );
    const first = controls[0];
    const last = controls.at(-1);
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last?.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first?.focus();
    }
  }

  useEffect(() => {
    let cancelled = false;
    listPublicCategories()
      .then((cats) => {
        const match = cats.find((category) => category.id === filters.categoryId);
        if (!cancelled) {
          setCategories(cats);
          setCategoryError(false);
          setCategoryName(match?.name ?? "Danh mục");
        }
      })
      .catch(() => {
        if (!cancelled) {
          setCategoryError(true);
          setCategoryName("Danh mục");
        }
      });
    return () => {
      cancelled = true;
    };
  }, [filters.categoryId]);

  useEffect(() => {
    requestGeneration.current += 1;
    if (!discoveryLocation) return;
    let cancelled = false;
    discoverCatalogItems({
      q: filters.q || undefined,
      type: filters.type || undefined,
      categoryId: filters.categoryId || undefined,
      listingKind: filters.listingKind || undefined,
      radiusMeters: filters.radiusMeters,
      openNow: filters.openNow || undefined,
      minPrice: filters.minPrice,
      maxPrice: filters.maxPrice,
      pickup: filters.pickup || undefined,
      canServe: filters.canServe || undefined,
      shipping: filters.shipping || undefined,
      sort: filters.sort === "RELEVANCE" && !filters.q ? "DISTANCE" : filters.sort ?? "RELEVANCE",
      limit: 20,
      location: discoveryLocation,
    })
      .then((result) => {
        if (!cancelled) {
          setPage(result.page);
          setError(false);
          setLoadingMore(false);
          setMoreError(false);
        }
      })
      .catch(() => {
        if (!cancelled) setError(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
      requestGeneration.current += 1;
    };
  }, [attempt, discoveryLocation, filters, searching]);

  async function loadMore() {
    if (!discoveryLocation || !page?.nextCursor || loadingMore || loading) return;
    const generation = requestGeneration.current;
    setLoadingMore(true);
    setMoreError(false);
    try {
      const result = await discoverCatalogItems({
        q: filters.q || undefined,
        type: filters.type || undefined,
        categoryId: filters.categoryId || undefined,
        listingKind: filters.listingKind || undefined,
        radiusMeters: filters.radiusMeters,
        openNow: filters.openNow || undefined,
        minPrice: filters.minPrice,
        maxPrice: filters.maxPrice,
        pickup: filters.pickup || undefined,
        canServe: filters.canServe || undefined,
        shipping: filters.shipping || undefined,
        sort: filters.sort === "RELEVANCE" && !filters.q ? "DISTANCE" : filters.sort ?? "RELEVANCE",
        limit: 20,
        location: discoveryLocation,
        cursor: page.nextCursor,
      });
      if (generation !== requestGeneration.current) return;
      const nextPage = result.page;
      if (!nextPage) throw new Error("Missing discovery page");
      setPage((previous) => ({
        ...nextPage,
        items: [...(previous?.items ?? []), ...(nextPage.items ?? [])].filter(
          (item, index, items) => items.findIndex((candidate) => candidate.id === item.id) === index,
        ),
      }));
    } catch {
      if (generation === requestGeneration.current) setMoreError(true);
    } finally {
      if (generation === requestGeneration.current) setLoadingMore(false);
    }
  }

  function navigate(next: SearchInitialFilters) {
    const p = new URLSearchParams();
    Object.entries(next).forEach(([k, v]) => {
      if (v !== undefined && v !== "" && v !== false && !(k === "sort" && v === "RELEVANCE")) {
        p.set(k, String(v));
      }
    });
    router.replace(`/search${p.size ? `?${p}` : ""}`, { scroll: false });
  }

  function openFilters() {
    setDraftFilter(filters);
    setSheet(true);
  }

  function update(next: SearchInitialFilters) {
    requestGeneration.current += 1;
    setLoadingMore(false);
    setMoreError(false);
    setPage(undefined);
    setLoading(true);
    setError(false);
    setFilters(next);
    navigate(next);
  }

  function submit(e: FormEvent) {
    e.preventDefault();
    const keyword = draftQ.trim();
    if (keyword) saveRecentSearch(keyword);
    setSearchFocused(false);
    update({ ...filters, q: keyword || undefined });
  }

  function applySidebarPrice() {
    const min = sidebarMinPrice === "" ? undefined : Number(sidebarMinPrice);
    const max = sidebarMaxPrice === "" ? undefined : Number(sidebarMaxPrice);
    if (min != null && max != null && min > max) return;
    update({ ...filters, minPrice: min, maxPrice: max });
  }

  if (!hydrated) return <div className={styles.center}>Đang đọc khu vực khám phá…</div>;

  return (
    <section className={styles.screen}>
      <header className={styles.appBar}>
        <button type="button" onClick={() => router.back()} aria-label="Quay lại">
          <ArrowLeft size={22} weight="bold" />
        </button>
        <h1>{categoryMode ? categoryName : searching ? "Kết quả tìm kiếm" : "Khám phá"}</h1>
      </header>

      <div className={styles.content}>
        {/* Mobile Search Form with Autocomplete */}
        <div className={styles.searchBlockWrapper}>
          <form className={styles.searchBlock} role="search" onSubmit={submit}>
            <p className={styles.location}>
              <button
                type="button"
                className={styles.locationButton}
                onClick={() => setShowLocationModal(true)}
                aria-label="Chọn vị trí"
              >
                <MapPin size={15} weight="duotone" className={styles.locationPinIcon} />
                <span>{location?.label ?? "Chọn vị trí"}</span>
                <CaretDown size={13} weight="bold" className={styles.locationCaretIcon} />
              </button>
            </p>
            <div className={styles.searchRow}>
              <label className={styles.searchInput}>
                <MagnifyingGlass size={20} weight="bold" className={styles.searchIcon} />
                <input
                  type="search"
                  aria-label={categoryMode ? `Tìm trong ${categoryName}` : "Tìm món đồ, dịch vụ"}
                  placeholder={categoryMode ? `Tìm trong ${categoryName}` : "Tìm món đồ, dịch vụ"}
                  value={draftQ}
                  onFocus={handleSearchFocus}
                  onChange={(e) => setDraftQ(e.target.value)}
                />
              </label>
              <button
                className={styles.filterButton}
                type="button"
                aria-label="Mở bộ lọc"
                onClick={() => {
                  setDraftFilter(filters);
                  setSheet(true);
                }}
              >
                <SlidersHorizontal size={20} weight="duotone" />
                {activeFilterCount > 0 && (
                  <span className={styles.filterButtonBadge}>{activeFilterCount}</span>
                )}
              </button>
            </div>
          </form>

          {/* Autocomplete / Recent Searches Popover */}
          {searchFocused && recentSearches.length > 0 && (
            <div className={styles.searchSuggestionsPopover}>
              <div className={styles.suggestionsHeader}>
                <span>Lịch sử tìm kiếm</span>
                <button type="button" onClick={clearAllRecentSearches}>
                  Xóa tất cả
                </button>
              </div>
              <div className={styles.suggestionsList}>
                {recentSearches.map((item) => (
                  <div key={item} className={styles.suggestionItem}>
                    <button
                      type="button"
                      className={styles.suggestionTextBtn}
                      onClick={() => {
                        setDraftQ(item);
                        setSearchFocused(false);
                        update({ ...filters, q: item });
                      }}
                    >
                      <Clock size={14} weight="duotone" />
                      <span>{item}</span>
                    </button>
                    <button
                      type="button"
                      className={styles.suggestionDeleteBtn}
                      aria-label={`Xóa ${item}`}
                      onClick={() => removeRecentSearch(item)}
                    >
                      <X size={13} weight="bold" />
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Explore Hero for Zero-Intent Search */}
        {!searching && (
          <div className={styles.exploreHero}>
            <div className={styles.hint}>
              <strong>Tìm quanh vị trí bạn đã chọn</strong>
              <p>
                Nhập từ khóa để tìm sản phẩm, dịch vụ hoặc đồ thanh lý. Vị trí không được đưa vào
                URL tìm kiếm.
              </p>
            </div>

            {categories.length > 0 && (
              <div className={styles.popularCategories}>
                <div className={styles.sectionHeader}>
                  <Sparkle size={18} weight="duotone" className={styles.sectionIcon} />
                  <h3>Danh mục khám phá</h3>
                </div>
                <div className={styles.categoryGrid}>
                  {categories.slice(0, 8).map((cat) => (
                    <button
                      key={cat.id}
                      type="button"
                      className={styles.categoryTile}
                      onClick={() => update({ ...filters, categoryId: cat.id })}
                    >
                      <CategoryBadge category={cat} size="small" />
                      <span className={styles.categoryTileLabel}>{cat.name}</span>
                    </button>
                  ))}
                </div>
              </div>
            )}

            <div className={styles.trendingKeywords}>
              <span className={styles.trendingLabel}>Gợi ý cư dân hay tìm:</span>
              <div className={styles.trendingList}>
                {["Cà phê & Đồ uống", "Cơm trưa", "Sửa máy lạnh", "Pass đồ", "Giặt sấy", "Tạp hóa"].map(
                  (tag) => (
                    <button
                      key={tag}
                      type="button"
                      className={styles.trendingChip}
                      onClick={() => {
                        setDraftQ(tag);
                        saveRecentSearch(tag);
                        update({ ...filters, q: tag });
                      }}
                    >
                      {tag}
                    </button>
                  ),
                )}
              </div>
            </div>
          </div>
        )}

        {/* 2-Column Desktop Architecture with Left Sticky Sidebar */}
        <div className={styles.searchLayout}>
          {/* Desktop Left Sticky Sidebar (Active on Desktop when sheet is closed) */}
          {!sheet && (
            <aside className={styles.desktopSidebar} aria-label="Bộ lọc tìm kiếm">
              <div className={styles.sidebarCard}>
                <div className={styles.sidebarHeader}>
                  <div className={styles.sidebarTitleGroup}>
                    <SlidersHorizontal size={18} weight="duotone" className={styles.sidebarHeaderIcon} />
                    <h2>Bộ lọc</h2>
                  </div>
                  {activeFilterCount > 0 && (
                    <button
                      type="button"
                      className={styles.sidebarResetBtn}
                      onClick={() => update({ q: filters.q, sort: filters.sort })}
                    >
                      Đặt lại ({activeFilterCount})
                    </button>
                  )}
                </div>

                {/* Section: Danh mục */}
                <div className={styles.sidebarSection}>
                  <span className={styles.sidebarSectionTitle}>Danh mục</span>
                  <div className={styles.sidebarCategoryList}>
                    <button
                      type="button"
                      className={`${styles.sidebarCategoryItem} ${!filters.categoryId ? styles.sidebarCategoryActive : ""}`}
                      onClick={() => update({ ...filters, categoryId: undefined })}
                    >
                      <span>Tất cả danh mục</span>
                      {!filters.categoryId && <Check size={14} weight="bold" />}
                    </button>
                    {categories.map((cat) => {
                      const isSelected = filters.categoryId === cat.id;
                      return (
                        <button
                          key={cat.id}
                          type="button"
                          className={`${styles.sidebarCategoryItem} ${isSelected ? styles.sidebarCategoryActive : ""}`}
                          onClick={() => update({ ...filters, categoryId: isSelected ? undefined : cat.id })}
                        >
                          <div className={styles.sidebarCatNameGroup}>
                            <CategoryBadge category={cat} size="small" />
                            <span>{cat.name}</span>
                          </div>
                          {isSelected && <Check size={14} weight="bold" />}
                        </button>
                      );
                    })}
                  </div>
                </div>

                {/* Section: Khoảng cách */}
                <div className={styles.sidebarSection}>
                  <span className={styles.sidebarSectionTitle}>Khoảng cách</span>
                  <div className={styles.sidebarRadioGroup}>
                    {[
                      { label: "Mọi khoảng cách", val: undefined },
                      { label: "Dưới 1 km", val: 1000 },
                      { label: "Dưới 5 km", val: 5000 },
                      { label: "Dưới 10 km", val: 10000 },
                    ].map((opt) => (
                      <button
                        key={opt.label}
                        type="button"
                        className={`${styles.sidebarPill} ${filters.radiusMeters === opt.val ? styles.sidebarPillActive : ""}`}
                        onClick={() => update({ ...filters, radiusMeters: opt.val })}
                      >
                        {opt.label}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Section: Khoảng giá */}
                <div className={styles.sidebarSection}>
                  <span className={styles.sidebarSectionTitle}>Khoảng giá</span>
                  <div className={styles.sidebarPriceInputs}>
                    <input
                      type="number"
                      min="0"
                      placeholder="Từ (₫)"
                      value={sidebarMinPrice}
                      onChange={(e) => setSidebarMinPrice(e.target.value)}
                    />
                    <span>-</span>
                    <input
                      type="number"
                      min="0"
                      placeholder="Đến (₫)"
                      value={sidebarMaxPrice}
                      onChange={(e) => setSidebarMaxPrice(e.target.value)}
                    />
                  </div>
                  <button
                    type="button"
                    className={styles.sidebarApplyPriceBtn}
                    onClick={applySidebarPrice}
                  >
                    Áp dụng giá
                  </button>
                </div>

                {/* Section: Giao nhận & Tiện ích */}
                <div className={styles.sidebarSection}>
                  <span className={styles.sidebarSectionTitle}>Tiện ích & Trạng thái</span>
                  <label className={styles.sidebarCheckboxLabel}>
                    <input
                      type="checkbox"
                      checked={filters.openNow ?? false}
                      onChange={(e) => update({ ...filters, openNow: e.target.checked })}
                    />
                    <span>Đang mở cửa</span>
                  </label>
                  <label className={styles.sidebarCheckboxLabel}>
                    <input
                      type="checkbox"
                      checked={filters.canServe ?? false}
                      onChange={(e) => update({ ...filters, canServe: e.target.checked })}
                    />
                    <span>Giao tận nơi</span>
                  </label>
                  <label className={styles.sidebarCheckboxLabel}>
                    <input
                      type="checkbox"
                      checked={filters.pickup ?? false}
                      onChange={(e) => update({ ...filters, pickup: e.target.checked })}
                    />
                    <span>Nhận tại điểm bán</span>
                  </label>
                  <label className={styles.sidebarCheckboxLabel}>
                    <input
                      type="checkbox"
                      checked={filters.listingKind === "RESALE"}
                      onChange={(e) =>
                        update({
                          ...filters,
                          listingKind: e.target.checked ? "RESALE" : "",
                        })
                      }
                    />
                    <span>Chỉ tin thanh lý</span>
                  </label>
                </div>
              </div>
            </aside>
          )}

          {/* Main Area: Tabs, Toolbars & Results */}
          <div className={styles.mainArea}>
            {/* Result Type Tabs */}
            <div className={styles.resultTabsRow} role="tablist" aria-label="Phân loại kết quả">
              <button
                type="button"
                role="tab"
                aria-selected={!filters.type && !filters.listingKind}
                className={`${styles.resultTab} ${!filters.type && !filters.listingKind ? styles.resultTabActive : ""}`}
                onClick={() => update({ ...filters, type: "", listingKind: "" })}
              >
                Tất cả
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={filters.type === "PRODUCT" && filters.listingKind !== "RESALE"}
                className={`${styles.resultTab} ${filters.type === "PRODUCT" && filters.listingKind !== "RESALE" ? styles.resultTabActive : ""}`}
                onClick={() => update({ ...filters, type: "PRODUCT", listingKind: "" })}
              >
                <Package size={15} weight="duotone" />
                Sản phẩm
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={filters.type === "SERVICE"}
                className={`${styles.resultTab} ${filters.type === "SERVICE" ? styles.resultTabActive : ""}`}
                onClick={() => update({ ...filters, type: "SERVICE", listingKind: "" })}
              >
                <Wrench size={15} weight="duotone" />
                Dịch vụ
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={filters.listingKind === "RESALE"}
                className={`${styles.resultTab} ${filters.listingKind === "RESALE" ? styles.resultTabActive : ""}`}
                onClick={() => update({ ...filters, listingKind: "RESALE", type: "" })}
              >
                <Tag size={15} weight="duotone" />
                Đồ thanh lý
              </button>
            </div>

            {/* Mobile Horizontal Quick-Filter Bar (Clean scrolling, no text clipping) */}
            {searching && (
              <div className={categoryStyles.filterBar}>
                <div className={styles.chips}>
                  <button
                    className={categoryMode ? categoryStyles.selectedChip : undefined}
                    aria-label={categoryMode ? "Danh mục đã chọn" : undefined}
                    onClick={openFilters}
                  >
                    {categoryMode ? "✓ Danh mục" : "Danh mục⌄"}
                  </button>
                  <button onClick={openFilters}>Khoảng cách⌄</button>
                  <button onClick={openFilters}>Giá⌄</button>
                  <button onClick={openFilters}>Giao nhận⌄</button>
                  {categoryMode && <button onClick={openFilters}>Loại</button>}

                  <button
                    type="button"
                    className={`${styles.quickToggleChip} ${filters.openNow ? styles.quickToggleActive : ""}`}
                    onClick={() => update({ ...filters, openNow: !filters.openNow })}
                  >
                    <Clock size={14} weight={filters.openNow ? "fill" : "duotone"} />
                    Đang mở cửa
                  </button>
                  <button
                    type="button"
                    className={`${styles.quickToggleChip} ${filters.listingKind === "RESALE" ? styles.quickToggleActive : ""}`}
                    onClick={() =>
                      update({
                        ...filters,
                        listingKind: filters.listingKind === "RESALE" ? "" : "RESALE",
                      })
                    }
                  >
                    <Tag size={14} weight={filters.listingKind === "RESALE" ? "fill" : "duotone"} />
                    Thanh lý
                  </button>
                  <button
                    type="button"
                    className={`${styles.quickToggleChip} ${filters.radiusMeters === 1000 ? styles.quickToggleActive : ""}`}
                    onClick={() =>
                      update({
                        ...filters,
                        radiusMeters: filters.radiusMeters === 1000 ? undefined : 1000,
                      })
                    }
                  >
                    <MapPin size={14} weight={filters.radiusMeters === 1000 ? "fill" : "duotone"} />
                    Dưới 1km
                  </button>
                  <button
                    type="button"
                    className={`${styles.quickToggleChip} ${filters.canServe ? styles.quickToggleActive : ""}`}
                    onClick={() => update({ ...filters, canServe: !filters.canServe })}
                  >
                    <Bicycle size={14} weight={filters.canServe ? "fill" : "duotone"} />
                    Giao tận nơi
                  </button>
                </div>
                <button className={categoryStyles.filterAction} onClick={openFilters}>
                  <SlidersHorizontal size={18} weight="duotone" />
                  Bộ lọc
                  {activeFilterCount > 0 && (
                    <span className={styles.filterActionBadge}>{activeFilterCount}</span>
                  )}
                </button>
              </div>
            )}

            {/* Active Filter Tags */}
            {activeFilterCount > 0 && (
              <div className={styles.activeFiltersRow}>
                <span className={styles.activeFiltersLabel}>Đang lọc:</span>
                <div className={styles.activeFiltersList}>
                  {filters.categoryId && (
                    <button
                      type="button"
                      className={styles.activeFilterPill}
                      onClick={() => update({ ...filters, categoryId: undefined })}
                      title="Bỏ lọc danh mục"
                    >
                      <span>{categoryName}</span>
                      <X size={12} weight="bold" />
                    </button>
                  )}
                  {filters.listingKind === "RESALE" && (
                    <button
                      type="button"
                      className={styles.activeFilterPill}
                      onClick={() => update({ ...filters, listingKind: "" })}
                      title="Bỏ lọc thanh lý"
                    >
                      <span>Thanh lý</span>
                      <X size={12} weight="bold" />
                    </button>
                  )}
                  {filters.type && (
                    <button
                      type="button"
                      className={styles.activeFilterPill}
                      onClick={() => update({ ...filters, type: "" })}
                      title="Bỏ lọc loại"
                    >
                      <span>{filters.type === "SERVICE" ? "Dịch vụ" : "Sản phẩm"}</span>
                      <X size={12} weight="bold" />
                    </button>
                  )}
                  {filters.radiusMeters && (
                    <button
                      type="button"
                      className={styles.activeFilterPill}
                      onClick={() => update({ ...filters, radiusMeters: undefined })}
                      title="Bỏ lọc khoảng cách"
                    >
                      <span>
                        Bán kính &le;{" "}
                        {filters.radiusMeters >= 1000
                          ? `${filters.radiusMeters / 1000}km`
                          : `${filters.radiusMeters}m`}
                      </span>
                      <X size={12} weight="bold" />
                    </button>
                  )}
                  {filters.openNow && (
                    <button
                      type="button"
                      className={styles.activeFilterPill}
                      onClick={() => update({ ...filters, openNow: false })}
                      title="Bỏ lọc đang mở cửa"
                    >
                      <span>Đang mở cửa</span>
                      <X size={12} weight="bold" />
                    </button>
                  )}
                  {(filters.minPrice != null || filters.maxPrice != null) && (
                    <button
                      type="button"
                      className={styles.activeFilterPill}
                      onClick={() =>
                        update({ ...filters, minPrice: undefined, maxPrice: undefined })
                      }
                      title="Bỏ lọc khoảng giá"
                    >
                      <span>
                        {filters.minPrice != null
                          ? `${new Intl.NumberFormat("vi-VN").format(filters.minPrice)}₫`
                          : "0₫"}
                        {" - "}
                        {filters.maxPrice != null
                          ? `${new Intl.NumberFormat("vi-VN").format(filters.maxPrice)}₫`
                          : "tối đa"}
                      </span>
                      <X size={12} weight="bold" />
                    </button>
                  )}
                  {filters.canServe && (
                    <button
                      type="button"
                      className={styles.activeFilterPill}
                      onClick={() => update({ ...filters, canServe: false })}
                      title="Bỏ lọc giao tận nơi"
                    >
                      <span>Giao tận nơi</span>
                      <X size={12} weight="bold" />
                    </button>
                  )}
                  {filters.pickup && (
                    <button
                      type="button"
                      className={styles.activeFilterPill}
                      onClick={() => update({ ...filters, pickup: false })}
                      title="Bỏ lọc nhận tại điểm bán"
                    >
                      <span>Nhận tại điểm bán</span>
                      <X size={12} weight="bold" />
                    </button>
                  )}
                  <button
                    type="button"
                    className={styles.clearAllBtn}
                    onClick={() => update({ q: filters.q, sort: filters.sort })}
                  >
                    <ArrowCounterClockwise size={13} weight="bold" />
                    Xóa tất cả
                  </button>
                </div>
              </div>
            )}

            {/* Toolbar Row: Results Count, View Switcher & Sort */}
            <div className={styles.toolbarRow}>
              <div className={styles.toolbarLeft}>
                {browsing && page?.items && page.items.length > 0 && (
                  <span className={styles.resultCountText}>
                    Tìm thấy <strong>{page.items.length}</strong> kết quả
                  </span>
                )}
              </div>
              <div className={styles.toolbarRight}>
                <MarketplaceViewToggle mode={viewMode} onModeChange={setViewMode} />

                <div className={`${styles.sort} ${categoryMode ? categoryStyles.categorySort : ""}`}>
                  <span>Sắp xếp</span>
                  <SelectDropdown<SearchSort>
                    aria-label="Sắp xếp"
                    value={filters.sort ?? "RELEVANCE"}
                    options={SORT_OPTIONS}
                    onChange={(val) => update({ ...filters, sort: val })}
                    radius="md"
                    size="sm"
                    className={styles.sortDropdown}
                  />
                </div>
              </div>
            </div>

            {!location && (
              <div className={styles.center}>
                <div className={styles.emptyIconBox}>
                  <MapPin size={32} weight="duotone" />
                </div>
                <strong>Chọn khu vực để xem kết quả gần bạn</strong>
                <p>Vị trí giúp hiển thị chính xác các quán ăn, dịch vụ và đồ thanh lý tại sảnh của bạn.</p>
                <button
                  type="button"
                  className={styles.primaryActionButton}
                  onClick={() => setShowLocationModal(true)}
                >
                  Chọn vị trí ngay
                </button>
              </div>
            )}

            {browsing && loading && (
              <div className={styles.list} aria-label="Đang tải kết quả">
                {[1, 2, 3, 4, 5, 6].map((n) => (
                  <div className={styles.skeleton} key={n} />
                ))}
              </div>
            )}

            {error && (
              <div className={styles.center}>
                <div className={styles.emptyIconBox}>
                  <ArrowCounterClockwise size={32} weight="duotone" />
                </div>
                <strong>Không thể tải kết quả tìm kiếm.</strong>
                <p>Kiểm tra kết nối rồi thử lại.</p>
                <button
                  className={styles.primaryActionButton}
                  onClick={() => {
                    setLoading(true);
                    setAttempt((v) => v + 1);
                  }}
                >
                  Thử lại tìm kiếm
                </button>
              </div>
            )}

            {!loading && !error && browsing && page?.items?.length === 0 && (
              <div className={styles.center}>
                <div className={styles.emptyIconBox}>
                  <MagnifyingGlass size={36} weight="duotone" />
                </div>
                <strong>Chưa tìm thấy kết quả phù hợp</strong>
                <p>Thử từ khóa hoặc bộ lọc khác.</p>
                {activeFilterCount > 0 && (
                  <div className={styles.emptyActions}>
                    <button
                      type="button"
                      className={styles.resetQuickBtn}
                      onClick={() => update({ q: filters.q, sort: filters.sort })}
                    >
                      <ArrowCounterClockwise size={15} weight="bold" />
                      Xóa bớt bộ lọc
                    </button>
                  </div>
                )}
                <Link href="/needs/new" className={styles.needLinkCta}>
                  <Sparkle size={16} weight="duotone" />
                  Đăng nhu cầu cần mua
                </Link>
              </div>
            )}

            {!loading && !error && (
              <SocialFeedProvider itemIds={(page?.items ?? []).flatMap((item) => item.id ? [item.id] : [])} enabled={viewMode === "social"}>
              <div
                className={`${viewStyles.grid} ${viewMode === "social" ? viewStyles.gridSocial : viewStyles.gridCatalog}`}
              >
                {page?.items?.map((item, index, items) => (
                  <Fragment key={item.id}>
                    <MarketplaceProductCard item={item} mode={viewMode} />
                    {index === Math.min(5, items.length - 1) && <div className={styles.adFeed}><AdSlot placement="IN_FEED" /></div>}
                  </Fragment>
                ))}
              </div>
              </SocialFeedProvider>
            )}

            {moreError && <p role="alert">Không thể tải thêm kết quả.</p>}

            {browsing && !loading && !error && page?.nextCursor && (
              <div className={styles.loadMoreContainer}>
                <button
                  type="button"
                  className={styles.loadMoreButton}
                  disabled={loadingMore}
                  onClick={() => void loadMore()}
                >
                  {loadingMore
                    ? "Đang tải thêm…"
                    : moreError
                      ? "Thử lại tải thêm"
                      : "Tải thêm kết quả"}
                </button>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Filter Dialog Sheet (Triggered on mobile or via filter button) */}
      {sheet && (
        <div className={styles.backdrop} onMouseDown={() => setSheet(false)}>
          <section
            ref={dialogRef}
            onKeyDown={handleDialogKey}
            className={styles.sheet}
            role="dialog"
            aria-modal="true"
            aria-label="Bộ lọc tìm kiếm"
            onMouseDown={(e) => e.stopPropagation()}
          >
            <header className={styles.sheetHeader}>
              <div className={styles.sheetTitle}>
                <SlidersHorizontal size={20} weight="duotone" className={styles.sheetTitleIcon} />
                <h2>Bộ lọc</h2>
              </div>
              <div className={styles.sheetHeaderControls}>
                <button
                  className={styles.reset}
                  type="button"
                  onClick={() => setDraftFilter({ q: filters.q })}
                >
                  Đặt lại
                </button>
                <button
                  type="button"
                  className={styles.sheetCloseButton}
                  aria-label="Đóng bộ lọc"
                  onClick={() => setSheet(false)}
                >
                  <X size={20} weight="bold" />
                </button>
              </div>
            </header>

            <div className={styles.sheetBody}>
              <label>
                Danh mục
                <SelectDropdown<string>
                  aria-label="Danh mục"
                  value={draftFilter.categoryId ?? ""}
                  radius="md"
                  size="md"
                  options={[
                    { value: "", label: "Tất cả danh mục" },
                    ...categories
                      .filter((category) => Boolean(category.id))
                      .map((category) => ({
                        value: category.id!,
                        label: category.name ?? "Danh mục",
                      })),
                  ]}
                  onChange={(val) =>
                    setDraftFilter({
                      ...draftFilter,
                      categoryId: val || undefined,
                    })
                  }
                />
              </label>
              {categoryError && (
                <p role="alert">Không thể tải danh mục. Đóng bộ lọc và tải lại trang để thử lại.</p>
              )}

              <label>
                Loại
                <SelectDropdown<string>
                  aria-label="Loại"
                  value={draftFilter.type ?? ""}
                  radius="md"
                  size="md"
                  options={[
                    { value: "", label: "Tất cả" },
                    { value: "PRODUCT", label: "Sản phẩm" },
                    { value: "SERVICE", label: "Dịch vụ" },
                  ]}
                  onChange={(val) =>
                    setDraftFilter({
                      ...draftFilter,
                      type: (val || undefined) as SearchType | undefined,
                    })
                  }
                />
              </label>

              <label>
                Loại tin
                <SelectDropdown<string>
                  aria-label="Loại tin"
                  value={draftFilter.listingKind ?? ""}
                  radius="md"
                  size="md"
                  options={[
                    { value: "", label: "Tất cả" },
                    { value: "RESALE", label: "Thanh lý" },
                  ]}
                  onChange={(val) =>
                    setDraftFilter({
                      ...draftFilter,
                      listingKind: (val || undefined) as ListingKind | undefined,
                    })
                  }
                />
              </label>

              <label>
                Khoảng cách
                <SelectDropdown<string>
                  aria-label="Khoảng cách"
                  value={draftFilter.radiusMeters ? String(draftFilter.radiusMeters) : ""}
                  radius="md"
                  size="md"
                  options={[
                    { value: "", label: "Mọi khoảng cách" },
                    { value: "1000", label: "1 km" },
                    { value: "5000", label: "5 km" },
                    { value: "10000", label: "10 km" },
                  ]}
                  onChange={(val) =>
                    setDraftFilter({
                      ...draftFilter,
                      radiusMeters: Number(val) || undefined,
                    })
                  }
                />
              </label>

              <fieldset className={styles.filterGroup}>
                <legend>Khoảng giá</legend>
                <label>
                  Giá từ (₫)
                  <input
                    type="number"
                    min="0"
                    step="1"
                    value={draftFilter.minPrice ?? ""}
                    onChange={(e) =>
                      setDraftFilter({
                        ...draftFilter,
                        minPrice: e.target.value === "" ? undefined : Number(e.target.value),
                      })
                    }
                  />
                </label>
                <label>
                  Giá đến (₫)
                  <input
                    type="number"
                    min="0"
                    step="1"
                    value={draftFilter.maxPrice ?? ""}
                    onChange={(e) =>
                      setDraftFilter({
                        ...draftFilter,
                        maxPrice: e.target.value === "" ? undefined : Number(e.target.value),
                      })
                    }
                  />
                </label>
                {invalidPriceRange && (
                  <p role="alert">Nhập giá nguyên không âm; giá đến phải lớn hơn hoặc bằng giá từ.</p>
                )}
              </fieldset>

              <fieldset className={styles.filterGroup}>
                <legend>Giao nhận</legend>
                <label className={styles.check}>
                  <input
                    type="checkbox"
                    checked={draftFilter.canServe ?? false}
                    onChange={(e) => setDraftFilter({ ...draftFilter, canServe: e.target.checked })}
                  />
                  Giao tận nơi
                </label>
                <label className={styles.check}>
                  <input
                    type="checkbox"
                    checked={draftFilter.pickup ?? false}
                    onChange={(e) => setDraftFilter({ ...draftFilter, pickup: e.target.checked })}
                  />
                  Nhận tại điểm bán
                </label>
                {draftFilter.canServe && draftFilter.pickup && (
                  <small>Chỉ hiển thị kết quả đáp ứng cả hai hình thức.</small>
                )}
              </fieldset>

              <label className={styles.check}>
                <input
                  type="checkbox"
                  checked={draftFilter.openNow ?? false}
                  onChange={(e) => setDraftFilter({ ...draftFilter, openNow: e.target.checked })}
                />
                Đang mở cửa
              </label>
            </div>

            <footer className={styles.sheetFooter}>
              <button
                className={styles.apply}
                disabled={invalidPriceRange}
                onClick={() => {
                  if (invalidPriceRange) return;
                  setSheet(false);
                  update(draftFilter);
                }}
              >
                Áp dụng
              </button>
            </footer>
          </section>
        </div>
      )}

      {/* Location Chooser Modal */}
      {showLocationModal && (
        <div
          className={styles.locationBackdrop}
          onClick={() => setShowLocationModal(false)}
        >
          <div
            className={styles.locationModalSheet}
            onClick={(e) => e.stopPropagation()}
          >
            <LocationChooser
              onComplete={() => setShowLocationModal(false)}
              onCancel={() => setShowLocationModal(false)}
            />
          </div>
        </div>
      )}
    </section>
  );
}
