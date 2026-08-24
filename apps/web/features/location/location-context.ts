export type BrowseLocation =
  | { kind: "coordinates"; lat: number; lng: number; label: string }
  | { kind: "address"; addressId: string; label: string };

/** Legacy Plan 03 discovery shape kept while Plan 09 migrates screens incrementally. */
export type DiscoveryLocation =
  | { kind: "coordinates"; lat: number; lng: number }
  | { kind: "saved-address"; addressId: string };

export type DiscoveryLocationQuery = Record<string, string | number>;

export function toCatalogLocationParams(location: BrowseLocation | null | undefined): DiscoveryLocationQuery {
  if (!location) return {};
  return location.kind === "address"
    ? { addressId: location.addressId }
    : { lat: location.lat, lng: location.lng };
}

export function toDiscoveryLocation(location: BrowseLocation | null | undefined): DiscoveryLocation | undefined {
  if (!location) return undefined;
  return location.kind === "address"
    ? { kind: "saved-address", addressId: location.addressId }
    : { kind: "coordinates", lat: location.lat, lng: location.lng };
}

export function locationToQuery(location: DiscoveryLocation | undefined): DiscoveryLocationQuery {
  if (!location) return {};
  return location.kind === "coordinates"
    ? { lat: location.lat, lng: location.lng }
    : { addressId: location.addressId };
}

export const toLocationQuery = locationToQuery;

export function locationToSearchParams(location: DiscoveryLocation | undefined): URLSearchParams {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(locationToQuery(location))) params.set(key, String(value));
  return params;
}

type SearchParamValue = string | string[] | undefined;
type SearchParamsLike = URLSearchParams | Record<string, SearchParamValue>;

function first(value: SearchParamValue): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

function read(params: SearchParamsLike, key: string): string | undefined {
  return params instanceof URLSearchParams ? params.get(key) ?? undefined : first(params[key]);
}

export function locationFromSearchParams(params: SearchParamsLike): DiscoveryLocation | undefined {
  const addressId = read(params, "addressId");
  if (addressId) return { kind: "saved-address", addressId };
  const lat = Number(read(params, "lat"));
  const lng = Number(read(params, "lng"));
  if (Number.isFinite(lat) && Number.isFinite(lng)) return { kind: "coordinates", lat, lng };
  return undefined;
}

export const readLocationFromParams = locationFromSearchParams;
