"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import type { components } from "@covie/api-client";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { discoverCatalogItems, listPublicCategories } from "@/features/catalog/catalog-api";
import { LocationChooser } from "@/features/location/location-chooser";
import { toDiscoveryLocation } from "@/features/location/location-context";
import { useBrowseLocation } from "@/features/location/use-browse-location";
import { HOME_SECTIONS, homeDiscoveryFilters, type HomeSectionDefinition, type HomeSectionKey } from "./home-data";
import styles from "./home-screen.module.css";

type Category = components["schemas"]["CategorySummary"];
type CatalogItem = components["schemas"]["CatalogItemSummaryResponse"];
type SectionState = { status: "loading" | "ready" | "error"; items: CatalogItem[] };

const CATEGORY_GLYPHS = ["◒", "●", "◇", "✦"];

function formatPrice(item: CatalogItem) {
  if (item.priceFrom?.amount == null) return "Giá liên hệ";
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: item.priceFrom.currency ?? "VND",
    maximumFractionDigits: 0,
  }).format(item.priceFrom.amount);
}

function formatDistance(distance?: number) {
  if (distance == null) return null;
  return distance < 1_000 ? `${Math.round(distance)} m` : `${(distance / 1_000).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} km`;
}

function ProductCard({ item }: { item: CatalogItem }) {
  const { location } = useBrowseLocation();
  const discoveryLocation = toDiscoveryLocation(location);
  const params = new URLSearchParams();
  if (discoveryLocation?.kind === "coordinates") {
    params.set("lat", String(discoveryLocation.lat));
    params.set("lng", String(discoveryLocation.lng));
  } else if (discoveryLocation?.kind === "saved-address") {
    params.set("addressId", discoveryLocation.addressId);
  }
  const suffix = params.size ? `?${params.toString()}` : "";
  const distance = formatDistance(item.resolvedLocation?.distanceMeters);

  return (
    <article className={styles.card}>
      <Link className={styles.cardMedia} href={`/items/${item.id}${suffix}`} tabIndex={-1} aria-hidden="true">
        {item.primaryMedia?.url ? (
          <span
            className={styles.mediaImage}
            style={{ backgroundImage: `url(${JSON.stringify(item.primaryMedia.url)})` }}
            role="img"
            aria-label={item.name ? `Hình ảnh ${item.name}` : "Hình ảnh sản phẩm"}
          />
        ) : (
          <span className={styles.mediaPlaceholder} aria-hidden="true">CoVie</span>
        )}
      </Link>
      <div className={styles.cardBody}>
        <h3><Link href={`/items/${item.id}${suffix}`}>{item.name ?? "Sản phẩm"}</Link></h3>
        <p className={styles.price}>{formatPrice(item)}</p>
        <p className={styles.merchant}>{item.merchant?.name ?? "Người bán địa phương"}</p>
        <p className={styles.locationLine}>
          <span aria-hidden="true">●</span>
          {item.resolvedLocation?.name ?? "Chưa xác định vị trí"}{distance ? ` · ${distance}` : ""}
        </p>
      </div>
    </article>
  );
}

function ProductSection({ definition, state, retry }: {
  definition: HomeSectionDefinition;
  state: SectionState;
  retry: () => void;
}) {
  return (
    <section className={styles.marketSection} aria-labelledby={`${definition.key}-title`}>
      <div className={styles.sectionHeading}>
        <h2 id={`${definition.key}-title`}>{definition.title}</h2>
        {state.status === "ready" && state.items.length > 0 && <Link href="/search">Xem tất cả</Link>}
      </div>
      {state.status === "loading" && <div className={styles.skeletonGrid} aria-label={`Đang tải ${definition.title.toLocaleLowerCase("vi-VN")}`}><i /><i /></div>}
      {state.status === "error" && (
        <div className={styles.inlineState} role="alert">
          <p>{definition.errorMessage}</p>
          <button type="button" onClick={retry} aria-label={`Thử lại ${definition.title.toLocaleLowerCase("vi-VN")}`}>Thử lại</button>
        </div>
      )}
      {state.status === "ready" && state.items.length === 0 && <p className={styles.emptyState}>{definition.emptyMessage}</p>}
      {state.status === "ready" && state.items.length > 0 && (
        <div className={styles.cardGrid}>{state.items.map((item) => <ProductCard key={item.id} item={item} />)}</div>
      )}
    </section>
  );
}

export function HomeScreen() {
  const { hydrated, location } = useBrowseLocation();
  const [categories, setCategories] = useState<Category[]>([]);
  const [categoriesStatus, setCategoriesStatus] = useState<"loading" | "ready" | "error">("loading");
  const [showLocationChooser, setShowLocationChooser] = useState(false);
  const initialSections = useMemo<Record<HomeSectionKey, SectionState>>(() => ({
    nearby: { status: "loading", items: [] },
    services: { status: "loading", items: [] },
    resale: { status: "loading", items: [] },
  }), []);
  const [sections, setSections] = useState(initialSections);

  const loadCategories = useCallback(async () => {
    try {
      const result = await listPublicCategories();
      setCategories([...result].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)).slice(0, 4));
      setCategoriesStatus("ready");
    } catch {
      setCategoriesStatus("error");
    }
  }, []);

  const loadSection = useCallback(async (definition: HomeSectionDefinition) => {
    if (!location) return;
    try {
      const result = await discoverCatalogItems(homeDiscoveryFilters(definition, location));
      setSections((current) => ({ ...current, [definition.key]: { status: "ready", items: result.page?.items ?? [] } }));
    } catch {
      setSections((current) => ({ ...current, [definition.key]: { status: "error", items: [] } }));
    }
  }, [location]);

  useEffect(() => {
    let active = true;
    void listPublicCategories().then((result) => {
      if (!active) return;
      setCategories([...result].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)).slice(0, 4));
      setCategoriesStatus("ready");
    }).catch(() => {
      if (active) setCategoriesStatus("error");
    });
    return () => { active = false; };
  }, []);
  useEffect(() => {
    if (!hydrated || !location) return;
    let active = true;
    for (const definition of HOME_SECTIONS) {
      void discoverCatalogItems(homeDiscoveryFilters(definition, location)).then((result) => {
        if (!active) return;
        setSections((current) => ({ ...current, [definition.key]: { status: "ready", items: result.page?.items ?? [] } }));
      }).catch(() => {
        if (active) setSections((current) => ({ ...current, [definition.key]: { status: "error", items: [] } }));
      });
    }
    return () => { active = false; };
  }, [hydrated, location]);

  return (
    <div className={styles.home}>
      <Link className={styles.search} href="/search" aria-label="Tìm món đồ, dịch vụ...">
        <CoVieIcon name="search" /><span>Tìm món đồ, dịch vụ...</span><CoVieIcon className={styles.filterIcon} name="filter" />
      </Link>

      <section className={styles.categories} aria-labelledby="categories-title">
        <div className={styles.sectionHeading}><h2 id="categories-title">Khám phá danh mục</h2></div>
        {categoriesStatus === "loading" && <div className={styles.categorySkeletons} aria-label="Đang tải danh mục"><i /><i /><i /><i /></div>}
        {categoriesStatus === "error" && (
          <div className={styles.inlineState} role="alert"><p>Không thể tải danh mục.</p><button type="button" onClick={() => { setCategoriesStatus("loading"); void loadCategories(); }}>Thử lại</button></div>
        )}
        {categoriesStatus === "ready" && categories.length === 0 && <p className={styles.emptyState}>Chưa có danh mục để khám phá.</p>}
        {categoriesStatus === "ready" && categories.length > 0 && (
          <div className={styles.categoryGrid}>
            {categories.map((category, index) => (
              <Link href={`/search?categoryId=${category.id}`} key={category.id ?? category.slug ?? category.name}>
                <span className={styles.categoryIcon} aria-hidden="true">{CATEGORY_GLYPHS[index]}</span>
                <span>{category.name ?? "Danh mục"}</span>
              </Link>
            ))}
          </div>
        )}
      </section>

      {!hydrated && <div className={styles.pageLoading} aria-label="Đang tải trang chủ" />}
      {hydrated && !location && (
        <section className={styles.locationState} aria-labelledby="location-state-title">
          <span className={styles.locationArtwork}><CoVieIcon name="location" /></span>
          <h2 id="location-state-title">Chọn vị trí để xem chợ gần bạn</h2>
          <p>CoVie dùng vị trí để ưu tiên sản phẩm, dịch vụ và đồ thanh lý ở khu vực của bạn.</p>
          <button type="button" onClick={() => setShowLocationChooser((open) => !open)}>Chọn vị trí</button>
          {showLocationChooser && <div className={styles.locationChooser}><LocationChooser /></div>}
        </section>
      )}
      {hydrated && location && HOME_SECTIONS.map((definition) => (
        <ProductSection key={definition.key} definition={definition} state={sections[definition.key]} retry={() => {
          setSections((current) => ({ ...current, [definition.key]: { status: "loading", items: [] } }));
          void loadSection(definition);
        }} />
      ))}
    </div>
  );
}
