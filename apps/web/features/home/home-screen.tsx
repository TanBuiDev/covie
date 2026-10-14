"use client";

import Link from "next/link";
import { AdSlot } from "@/features/ads/ad-slot";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { components } from "@covie/api-client";
import {
  BowlFood,
  CaretLeft,
  CaretRight,
  Clock,
  Coffee,
  Handshake,
  Lightning,
  MapPin,
  Package,
  Recycle,
  ShieldCheck,
  ShoppingBag,
  SquaresFour,
  Star,
  Storefront,
  Wrench,
} from "@phosphor-icons/react";
import { CategoryBadge } from "@/components/ui/category-icon";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { discoverCatalogItems, listPublicCategories } from "@/features/catalog/catalog-api";
import { LocationChooser } from "@/features/location/location-chooser";
import { useBrowseLocation } from "@/features/location/use-browse-location";
import { locationToSearchParams, toDiscoveryLocation } from "@/features/location/location-context";
import { HOME_SECTIONS, homeDiscoveryFilters, type HomeSectionDefinition, type HomeSectionKey } from "./home-data";
import { MarketplaceProductCard } from "@/features/marketplace-view/marketplace-product-card";
import { MarketplaceViewToggle } from "@/features/marketplace-view/marketplace-view-toggle";
import { useMarketplaceView } from "@/features/marketplace-view/use-marketplace-view";
import type { MarketplaceViewMode } from "@/features/marketplace-view/marketplace-view-preference";
import viewStyles from "@/features/marketplace-view/marketplace-view.module.css";
import styles from "./home-screen.module.css";

type Category = components["schemas"]["CategorySummary"];
type CatalogItem = components["schemas"]["CatalogItemSummaryResponse"];
type SectionState = { status: "loading" | "ready" | "error"; items: CatalogItem[] };

const QUICK_TAGS = [
  { label: "Cà phê", icon: Coffee, href: "/search?q=Cà+phê" },
  { label: "Đồ ăn", icon: BowlFood, href: "/search?q=Đồ+ăn" },
  { label: "Sửa máy lạnh", icon: Wrench, href: "/search?type=SERVICE&q=máy+lạnh" },
  { label: "Đồ thanh lý", icon: Recycle, href: "/search?type=PRODUCT&listingKind=RESALE" },
  { label: "Quanh bạn", icon: Lightning, href: "/search?sort=DISTANCE" },
];

const DISCOVERY_SHORTCUTS = [
  { label: "Tất cả", icon: SquaresFour, href: "/search" },
  { label: "Gần nhất", icon: Lightning, href: "/search?sort=DISTANCE" },
  { label: "Đang mở cửa", icon: Storefront, href: "/search?openNow=true&sort=DISTANCE" },
  { label: "Đồ thanh lý", icon: Package, href: "/search?type=PRODUCT&listingKind=RESALE&sort=DISTANCE" },
  { label: "Dịch vụ tại nhà", icon: Wrench, href: "/search?type=SERVICE&sort=DISTANCE" },
];

function formatDistance(distance?: number) {
  if (distance == null) return null;
  return distance < 1_000 ? `${Math.round(distance)} m` : `${(distance / 1_000).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} km`;
}

function ProductSection({ definition, state, retry, mode }: {
  definition: HomeSectionDefinition;
  state: SectionState;
  retry: () => void;
  mode: MarketplaceViewMode;
}) {
  const searchParams = new URLSearchParams();
  if (definition.filters.type) searchParams.set("type", definition.filters.type);
  if (definition.filters.listingKind === "RESALE") searchParams.set("listingKind", "RESALE");
  searchParams.set("sort", "DISTANCE");
  // Item links must retain the previously supported location context.
  const { location } = useBrowseLocation();
  const itemLocationParams = locationToSearchParams(toDiscoveryLocation(location));
  const itemSuffix = itemLocationParams.size ? `?${itemLocationParams}` : "";
  return (
    <section className={styles.marketSection} aria-labelledby={`${definition.key}-title`}>
      <div className={styles.sectionHeading}>
        <div className={styles.headingTitleGroup}>
          <h2 id={`${definition.key}-title`}>{definition.title}</h2>
          {state.status === "ready" && state.items.length > 0 && (
            <span className={styles.itemCountBadge}>{state.items.length} món</span>
          )}
        </div>
        <div className={styles.headingActionGroup}>
          {state.status === "ready" && state.items.length > 0 && (
            <Link href={`/search?${searchParams}`}>Xem tất cả</Link>
          )}

        </div>
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
        <div className={`${viewStyles.grid} ${mode === "social" ? viewStyles.gridSocial : viewStyles.gridCatalog}`}>
          {state.items.map((item) => <MarketplaceProductCard key={item.id} item={item} mode={mode} href={`/items/${item.id}${itemSuffix}`} />)}
        </div>
      )}
    </section>
  );
}

type FeaturedMerchant = {
  id: string;
  name: string;
  locationName?: string;
  distanceMeters?: number;
  itemCount: number;
};

function getMerchantInitials(name?: string) {
  const words = name?.trim().split(/\s+/).filter(Boolean) ?? [];
  return words.slice(-2).map((w) => w[0]?.toLocaleUpperCase("vi-VN")).join("") || "CV";
}

function FeaturedMerchantsSection({ merchants }: { merchants: FeaturedMerchant[] }) {
  const scrollRef = useRef<HTMLDivElement>(null);

  if (merchants.length === 0) return null;

  const scrollLeft = () => {
    scrollRef.current?.scrollBy({ left: -260, behavior: "smooth" });
  };
  const scrollRight = () => {
    scrollRef.current?.scrollBy({ left: 260, behavior: "smooth" });
  };

  return (
    <section className={styles.featuredMerchantsSection} aria-labelledby="featured-merchants-title">
      <div className={styles.sectionHeading}>
        <div className={styles.headingTitleGroup}>
          <h2 id="featured-merchants-title">Gian hàng &amp; Thợ uy tín gần bạn</h2>
          <span className={styles.itemCountBadge}>
            <Star size={13} weight="fill" aria-hidden="true" /> Tin chọn
          </span>
        </div>
        <div className={styles.headingActionGroup}>
          <Link href="/search?sort=DISTANCE">Khám phá chợ</Link>
          {merchants.length > 2 && (
            <div className={styles.scrollControls} aria-hidden="true">
              <button type="button" onClick={scrollLeft} className={styles.scrollBtn} aria-label="Cuộn trái">
                <CaretLeft size={16} weight="bold" />
              </button>
              <button type="button" onClick={scrollRight} className={styles.scrollBtn} aria-label="Cuộn phải">
                <CaretRight size={16} weight="bold" />
              </button>
            </div>
          )}
        </div>
      </div>
      <div ref={scrollRef} className={styles.merchantCarousel}>
        {merchants.map((merchant) => {
          const distanceStr = formatDistance(merchant.distanceMeters);
          return (
            <div key={merchant.id} className={styles.merchantCard}>
              <div className={styles.merchantHeader}>
                <div className={styles.merchantAvatar} aria-hidden="true">
                  {getMerchantInitials(merchant.name)}
                </div>
                <div className={styles.merchantInfo}>
                  <h3 className={styles.merchantName}>{merchant.name}</h3>
                  <div className={styles.merchantMeta}>
                    <span className={styles.merchantRating}>
                      <Star size={12} weight="fill" aria-hidden="true" /> 4.9+
                    </span>
                    <span className={styles.merchantVerified}>
                      <ShieldCheck size={13} weight="duotone" aria-hidden="true" /> Đã xác thực
                    </span>
                  </div>
                </div>
              </div>
              <div className={styles.merchantSubtext}>
                <span className={styles.merchantSubtextItem}>
                  <MapPin size={13} weight="duotone" aria-hidden="true" /> {distanceStr ? `${distanceStr} · ` : ""}{merchant.locationName ?? "Nội khu"}
                </span>
                <span className={styles.merchantSubtextItem}>
                  <ShoppingBag size={13} weight="duotone" aria-hidden="true" /> {merchant.itemCount} món &amp; dịch vụ
                </span>
              </div>
              <Link href={`/merchants/${merchant.id}`} className={styles.merchantCta}>
                Ghé gian hàng &rsaquo;
              </Link>
            </div>
          );
        })}
      </div>
    </section>
  );
}

export function HomeScreen() {
  const { hydrated, location } = useBrowseLocation();
  const { mode: viewMode, changeMode: setViewMode } = useMarketplaceView("social");
  const [categories, setCategories] = useState<Category[]>([]);
  const [categoriesStatus, setCategoriesStatus] = useState<"loading" | "ready" | "error">("loading");
  const [showLocationChooser, setShowLocationChooser] = useState(false);
  const [needQuery, setNeedQuery] = useState("");
  const initialSections = useMemo<Record<HomeSectionKey, SectionState>>(() => ({
    nearby: { status: "loading", items: [] },
    services: { status: "loading", items: [] },
    resale: { status: "loading", items: [] },
  }), []);
  const [sections, setSections] = useState(initialSections);

  const loadCategories = useCallback(async () => {
    try {
      const result = await listPublicCategories();
      setCategories([...result].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)).slice(0, 8));
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
      setCategories([...result].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)).slice(0, 8));
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


  const featuredMerchants = useMemo(() => {
    const map = new Map<string, FeaturedMerchant>();
    for (const item of [...sections.nearby.items, ...sections.services.items]) {
      if (!item.merchant?.id) continue;
      const existing = map.get(item.merchant.id);
      if (existing) {
        existing.itemCount++;
      } else {
        map.set(item.merchant.id, {
          id: item.merchant.id,
          name: item.merchant.name ?? "Gian hàng địa phương",
          locationName: item.resolvedLocation?.name,
          distanceMeters: item.resolvedLocation?.distanceMeters,
          itemCount: 1,
        });
      }
    }
    return Array.from(map.values()).slice(0, 6);
  }, [sections.nearby.items, sections.services.items]);

  const nearbyDef = HOME_SECTIONS.find((s) => s.key === "nearby");
  const servicesDef = HOME_SECTIONS.find((s) => s.key === "services");
  const resaleDef = HOME_SECTIONS.find((s) => s.key === "resale");

  return (
    <div className={styles.home}>
      <div className={styles.searchContainer}>
        <Link className={styles.search} href="/search" aria-label="Tìm món đồ, dịch vụ...">
          <CoVieIcon name="search" /><span>Tìm món đồ, dịch vụ...</span><CoVieIcon className={styles.filterIcon} name="filter" />
        </Link>
        <div className={styles.quickTags} aria-label="Gợi ý tìm kiếm nhanh">
          {QUICK_TAGS.map((tag) => {
            const Icon = tag.icon;
            return (
              <Link key={tag.label} href={tag.href} className={styles.quickTag}>
                <Icon size={14} weight="duotone" aria-hidden="true" />
                <span>{tag.label}</span>
              </Link>
            );
          })}
        </div>
      </div>

      {hydrated && location && (
        <section className={styles.contextBanner} aria-label="Khu vực chợ hiện tại">
          <div className={styles.pulseBar}>
            <span className={styles.pulseDot} aria-hidden="true" />
            <span className={styles.pulseLiveLabel}>CHỢ NỘI KHU ĐANG HOẠT ĐỘNG</span>
            <span className={styles.pulseSep}>&bull;</span>
            <span className={styles.pulseNotice}>
              <Lightning size={12} weight="fill" aria-hidden="true" /> 15 phút nhận hàng &bull; Thợ &amp; quán ăn sẵn sàng phục vụ
            </span>
          </div>
          <div className={styles.contextHeader}>
            <div className={styles.contextBadge}>
              <CoVieIcon name="location" />
              <span>{location.label ?? "Khu vực lân cận"}</span>
            </div>
            <div className={styles.contextMetrics}>
              <span className={styles.contextPill}>
                <Lightning size={13} weight="fill" aria-hidden="true" /> Giao nhanh nội khu
              </span>
              <span className={styles.openNowPill}>
                <Clock size={13} weight="duotone" aria-hidden="true" /> Đang mở cửa
              </span>
            </div>
          </div>
          <h1 className={styles.contextTitle}>Chợ cư dân quanh bạn hôm nay có gì?</h1>
          <p className={styles.contextSubtitle}>
            Món ăn nóng hổi, dịch vụ tại nhà và đồ thanh lý giá tốt từ hàng xóm
          </p>
          <div className={styles.contextHighlights}>
            <div className={styles.contextHighlightItem}>
              <span className={`${styles.highlightIcon} ${styles.highlightFoodIcon}`}>
                <BowlFood size={18} weight="duotone" aria-hidden="true" />
              </span>
              <span>Đồ ăn &amp; nước uống giao tận cửa</span>
            </div>
            <div className={styles.contextHighlightItem}>
              <span className={`${styles.highlightIcon} ${styles.highlightServiceIcon}`}>
                <Wrench size={18} weight="duotone" aria-hidden="true" />
              </span>
              <span>Thợ sửa chữa, vệ sinh tại nhà</span>
            </div>
            <div className={styles.contextHighlightItem}>
              <span className={`${styles.highlightIcon} ${styles.highlightResaleIcon}`}>
                <Recycle size={18} weight="duotone" aria-hidden="true" />
              </span>
              <span>Pass đồ cũ giá hời cùng toà</span>
            </div>
          </div>
        </section>
      )}

      <section className={styles.categories} aria-labelledby="categories-title">
        <div className={styles.sectionHeading}><h2 id="categories-title">Khám phá danh mục</h2></div>
        {categoriesStatus === "loading" && <div className={styles.categorySkeletons} aria-label="Đang tải danh mục"><i /><i /><i /><i /></div>}
        {categoriesStatus === "error" && (
          <div className={styles.inlineState} role="alert"><p>Không thể tải danh mục.</p><button type="button" onClick={() => { setCategoriesStatus("loading"); void loadCategories(); }}>Thử lại</button></div>
        )}
        {categoriesStatus === "ready" && categories.length === 0 && <p className={styles.emptyState}>Chưa có danh mục để khám phá.</p>}
        {categoriesStatus === "ready" && categories.length > 0 && (
          <div className={styles.categoryGrid}>
            {categories.map((category) => (
              <Link href={`/search?categoryId=${category.id}`} key={category.id ?? category.slug ?? category.name}>
                <CategoryBadge category={category} size="large" />
                <span>{category.name ?? "Danh mục"}</span>
              </Link>
            ))}
          </div>
        )}
      </section>

      {hydrated && location && (
        <div className={styles.discoveryShortcuts} aria-label="Bộ lọc nhanh">
          {DISCOVERY_SHORTCUTS.map((shortcut) => {
            const Icon = shortcut.icon;
            return (
              <Link key={shortcut.label} href={shortcut.href} className={styles.shortcutPill}>
                <Icon size={16} weight="duotone" aria-hidden="true" />
                <span>{shortcut.label}</span>
              </Link>
            );
          })}
        </div>
      )}

      {hydrated && location && (
        <div className={styles.marketplaceModeToolbar}>
          <span>Hiển thị sản phẩm</span>
          <MarketplaceViewToggle mode={viewMode} onModeChange={setViewMode} />
        </div>
      )}
      <AdSlot placement="HOME_HERO" />
      {!hydrated && <div className={styles.pageLoading} aria-label="Đang tải trang chủ" />}
      {hydrated && !location && (
        <section className={styles.locationState} aria-labelledby="location-state-title">
          <span className={styles.locationArtwork}><CoVieIcon name="location" /></span>
          <h2 id="location-state-title">Chọn vị trí để xem chợ gần bạn</h2>
          <p>CoVie dùng vị trí để ưu tiên sản phẩm, dịch vụ và đồ thanh lý ở khu vực của bạn.</p>
          <button type="button" onClick={() => setShowLocationChooser((open) => !open)}>Chọn vị trí</button>
          {showLocationChooser && (
            <div className={styles.locationChooser}>
              <LocationChooser
                onComplete={() => setShowLocationChooser(false)}
                onCancel={() => setShowLocationChooser(false)}
              />
            </div>
          )}
        </section>
      )}

      {hydrated && location && nearbyDef && (
        <ProductSection definition={nearbyDef} state={sections[nearbyDef.key]} mode={viewMode} retry={() => {
          setSections((current) => ({ ...current, [nearbyDef.key]: { status: "loading", items: [] } }));
          void loadSection(nearbyDef);
        }} />
      )}

      {hydrated && location && (
        <section className={styles.needSpotlight} aria-labelledby="need-spotlight-title">
          <div className={styles.needBadge}>
            <CoVieIcon name="need" />
            <span>Nhu cầu cư dân &bull; Khớp lệnh trực tiếp</span>
          </div>
          <div className={styles.needBody}>
            <div className={styles.needText}>
              <h2 id="need-spotlight-title">Bạn đang cần tìm món đồ hay thợ sửa gấp?</h2>
              <p>Đăng nhu cầu &ldquo;Cần mua&rdquo; để các quán ăn, cửa hàng và thợ quanh toà nhà gửi báo giá ngay.</p>
            </div>

            <form action="/needs/new" method="GET" className={styles.needQuickForm}>
              <input
                type="text"
                name="q"
                className={styles.needInput}
                value={needQuery}
                onChange={(e) => setNeedQuery(e.target.value)}
                placeholder="Gõ món đồ hoặc dịch vụ cần tìm (ví dụ: Bún bò toà S5, thợ điện...)"
                aria-label="Nhu cầu cần tìm"
              />
              <button type="submit" className={styles.needSubmitBtn}>
                Đăng nhu cầu ngay
              </button>
            </form>

            <div className={styles.needTrustHighlights}>
              <div className={styles.needHighlightItem}>
                <span className={styles.needHighlightIcon}>
                  <Lightning size={15} weight="fill" aria-hidden="true" />
                </span>
                <span>Trung bình 15 phút nhận báo giá</span>
              </div>
              <div className={styles.needHighlightItem}>
                <span className={styles.needHighlightIcon}>
                  <Handshake size={16} weight="duotone" aria-hidden="true" />
                </span>
                <span>Kết nối trực tiếp thợ &amp; quán nội khu</span>
              </div>
              <div className={styles.needHighlightItem}>
                <span className={styles.needHighlightIcon}>
                  <ShieldCheck size={16} weight="duotone" aria-hidden="true" />
                </span>
                <span>Bảo mật SĐT cá nhân</span>
              </div>
            </div>

            <div className={styles.needActions}>
              <Link href="/needs/new" className={styles.needBtnPrimary}>
                + Đăng nhu cầu đầy đủ
              </Link>
              <Link href="/needs" className={styles.needBtnSecondary}>
                Xem tin cần mua quanh đây
              </Link>
            </div>
          </div>
        </section>
      )}

      {hydrated && location && (
        <><AdSlot placement="IN_FEED" /><FeaturedMerchantsSection merchants={featuredMerchants} /></>
      )}

      {hydrated && location && servicesDef && (
        <ProductSection definition={servicesDef} state={sections[servicesDef.key]} mode={viewMode} retry={() => {
          setSections((current) => ({ ...current, [servicesDef.key]: { status: "loading", items: [] } }));
          void loadSection(servicesDef);
        }} />
      )}

      {hydrated && location && resaleDef && (
        <ProductSection definition={resaleDef} state={sections[resaleDef.key]} mode={viewMode} retry={() => {
          setSections((current) => ({ ...current, [resaleDef.key]: { status: "loading", items: [] } }));
          void loadSection(resaleDef);
        }} />
      )}

      {hydrated && location && (
        <section className={styles.sellerBanner} aria-labelledby="seller-banner-title">
          <div className={styles.sellerBannerContent}>
            <div className={styles.sellerBannerIcon} aria-hidden="true">
              <Package size={28} weight="duotone" />
            </div>
            <div className={styles.sellerBannerText}>
              <h2 id="seller-banner-title">Dọn nhà cuối tuần? Pass đồ cùng CoVie</h2>
              <p>Thanh lý đồ không dùng cho hàng xóm cùng toà nhà hoặc mở gian hàng kinh doanh trong 1 phút.</p>
            </div>
          </div>
          <Link href="/seller" className={styles.sellerBannerCta}>
            Mở quầy / Pass đồ
          </Link>
        </section>
      )}
    </div>
  );
}
