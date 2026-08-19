import type { components } from "@covie/api-client";

type CatalogDetailData = components["schemas"]["CatalogItemDetailResponse"];

function money(value?: components["schemas"]["Money"]) {
  if (!value?.amount) return "Giá liên hệ";
  return `${new Intl.NumberFormat("vi-VN").format(value.amount)} ${value.currency ?? "VND"}`;
}

export function CatalogDetail({ item }: { item: CatalogDetailData }) {
  const location = item.resolvedLocation;
  return <article>
    <header>
      <h1>{item.name ?? "Sản phẩm"}</h1>
      <p>{item.merchant?.name ?? "Người bán"}</p>
      {location && <p>{location.name} — {location.addressText}{location.distanceMeters != null ? ` — ${Math.round(location.distanceMeters)} m` : ""}</p>}
    </header>
    {item.description && <p>{item.description}</p>}
    {item.media?.length ? <section aria-label="Hình ảnh sản phẩm">{item.media.map((media) => <img key={media.id} src={media.url} alt={item.name ?? "Hình ảnh sản phẩm"} width={640} height={480} />)}</section> : null}
    {item.options?.length ? <section><h2>Lựa chọn</h2>{item.options.map((option) => <div key={option.id}><strong>{option.name}</strong><ul>{option.values?.map((value) => <li key={value.id}>{value.value}</li>)}</ul></div>)}</section> : null}
    <section aria-label="Biến thể"><h2>Biến thể</h2><ul>{item.variants?.map((variant) => <li key={variant.id}>
      <span>{variant.name}</span>{variant.offer && <span> — {money(variant.offer.price)} ({variant.offer.locationVariantId}){variant.offer.available ? "" : " — Hết hàng"}</span>}
    </li>)}</ul></section>
  </article>;
}
