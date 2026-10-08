import { CheckoutPreview, CheckoutSafeError } from "@/features/checkout/checkout-preview";
import { parseCheckoutSource } from "@/features/checkout/checkout-source";
import { createServerReadClient } from "@/lib/api/server-api-client";
import { requireUser } from "@/lib/auth/require-user";

function checkSourceRead(response: Response) {
  if (!response.ok && ![400,404,410,422].includes(response.status)) throw new Error("Không thể tải thông tin đặt hàng.");
}

export default async function CheckoutPage({ searchParams }: { searchParams: Promise<Record<string,string|string[]|undefined>> }) {
  const params = await searchParams;
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (typeof value === "string") query.append(key, value);
    else if (Array.isArray(value)) for (const item of value) query.append(key, item);
  }
  await requireUser(`/checkout${query.size ? `?${query}` : ""}`);
  const source = parseCheckoutSource(params);
  if (params.source && !source) return <CheckoutSafeError />;
  const cartId = source?.type === "CART" ? source.cartId : typeof params.cartId === "string" ? params.cartId : undefined;
  const client = await createServerReadClient();
  if (source?.type === "DIRECT") {
    const itemId = typeof params.itemId === "string" ? params.itemId : undefined;
    const locationId = typeof params.locationId === "string" ? params.locationId : undefined;
    if (!itemId || !locationId) return <CheckoutSafeError />;
    const {data:item,response} = await client.GET("/api/v1/catalog/items/{itemId}",{
      params:{path:{itemId},query:{locationId}},
    });
    checkSourceRead(response);
    const variant = item?.variants?.find(variant=>variant.offer?.locationVariantId === source.locationVariantId);
    if (!response.ok || !item || !variant?.offer?.available || item.resolvedLocation?.id !== locationId ||
      (item.type !== "PRODUCT" && item.type !== "SERVICE") || ((item.type === "SERVICE" || item.listingKind === "RESALE") && source.quantity !== 1)) return <CheckoutSafeError />;
    const {data:addresses,response:addressResponse} = await client.GET("/api/v1/me/addresses");
    if (!addressResponse.ok || !addresses) throw new Error("Không thể tải địa chỉ nhận hàng.");
    return <main><CheckoutPreview key={`direct:${itemId}:${locationId}:${source.locationVariantId}:${source.quantity}`} carts={[]} addresses={addresses}
      directSelection={{itemId,locationId,locationVariantId:source.locationVariantId,itemName:item.name || "Món đã chọn",variantName:variant.name,fixedQuantity:item.listingKind === "RESALE",type:item.type,quantity:source.quantity}}/></main>;
  }
  if (source?.type === "NEED_REQUEST") {
    const needRequestId = source.needRequestId;
    const {data:need,response:needResponse} = await client.GET("/api/v1/me/need-requests/{needRequestId}",{
      params:{path:{needRequestId}},
    });
    checkSourceRead(needResponse);
    if (!needResponse.ok || !need || (need.type !== "PRODUCT" && need.type !== "SERVICE")) return <CheckoutSafeError />;
    const cursors = new Set<string>();
    let cursor: string | undefined;
    do {
      const {data:page,response:pageResponse} = await client.GET("/api/v1/me/need-requests/{needRequestId}/responses",{
        params:{path:{needRequestId},query:{limit:100,cursor}},
      });
      checkSourceRead(pageResponse);
      if (!pageResponse.ok || !page) return <CheckoutSafeError />;
      const responseGroup = page.items?.find(response=>response.items?.some(item=>item.id === source.needResponseItemId));
      const item = responseGroup?.items?.find(item=>item.id === source.needResponseItemId);
      if (item) {
        if (!item.actionable || !item.locationVariantId || (need.type === "SERVICE" && source.quantity !== 1)) return <CheckoutSafeError />;
        if (!item.catalogItemId || !responseGroup?.merchantLocationId) return <CheckoutSafeError />;
        const {data:catalog,response:catalogResponse} = await client.GET("/api/v1/catalog/items/{itemId}",{
          params:{path:{itemId:item.catalogItemId},query:{locationId:responseGroup.merchantLocationId}},
        });
        checkSourceRead(catalogResponse);
        const variant = catalog?.variants?.find(variant=>variant.offer?.locationVariantId === item.locationVariantId);
        if (!catalogResponse.ok || !catalog || !variant?.offer?.available || catalog.type !== need.type ||
          catalog.resolvedLocation?.id !== responseGroup.merchantLocationId || (catalog.listingKind === "RESALE" && source.quantity !== 1)) return <CheckoutSafeError />;
        const {data:addresses,response:addressResponse} = await client.GET("/api/v1/me/addresses");
        if (!addressResponse.ok || !addresses) throw new Error("Không thể tải địa chỉ nhận hàng.");
        const initialAddressId = typeof params.addressId === "string" && addresses.some(address=>address.id === params.addressId) ? params.addressId : undefined;
        return <main><CheckoutPreview key={`need:${needRequestId}:${item.id}:${source.quantity}:${initialAddressId ?? ""}`} carts={[]} addresses={addresses}
          initialAddressId={initialAddressId} needSelection={{needRequestId,needResponseItemId:source.needResponseItemId,
            locationVariantId:item.locationVariantId,itemName:item.catalogItemName || "Mục phản hồi đã chọn",variantName:variant.name,fixedQuantity:catalog.listingKind === "RESALE",type:need.type,quantity:source.quantity}}/></main>;
      }
      cursor = page.hasMore ? page.nextCursor ?? undefined : undefined;
      if (cursor && cursors.has(cursor)) return <CheckoutSafeError />;
      if (cursor) cursors.add(cursor);
    } while (cursor);
    return <CheckoutSafeError />;
  }
  const [{ data: carts, response: cartResponse }, { data: addresses, response: addressResponse }] = await Promise.all([
    client.GET("/api/v1/me/carts"), client.GET("/api/v1/me/addresses"),
  ]);
  if (!cartResponse.ok || !addressResponse.ok || !carts || !addresses) throw new Error("Không thể chuẩn bị checkout.");
  return <main><CheckoutPreview key={`cart:${cartId ?? ""}`} carts={carts} addresses={addresses} initialCartId={cartId} /></main>;
}
