import type { BrowseLocation } from "@/features/location/location-context";

export const BROWSE_LOCATION_STORAGE_KEY = "covie:browse-location:v1";

type StoredBrowseLocation = { version: 1; location: BrowseLocation };

function validCoordinates(value: BrowseLocation): value is Extract<BrowseLocation, { kind: "coordinates" }> {
  return value.kind === "coordinates"
    && Number.isFinite(value.lat)
    && value.lat >= -90
    && value.lat <= 90
    && Number.isFinite(value.lng)
    && value.lng >= -180
    && value.lng <= 180
    && typeof value.label === "string"
    && value.label.trim().length > 0;
}

function validAddress(value: BrowseLocation): value is Extract<BrowseLocation, { kind: "address" }> {
  return value.kind === "address"
    && typeof value.addressId === "string"
    && value.addressId.trim().length > 0
    && typeof value.label === "string"
    && value.label.trim().length > 0;
}

export function isBrowseLocation(value: unknown): value is BrowseLocation {
  if (!value || typeof value !== "object" || !("kind" in value)) return false;
  const candidate = value as BrowseLocation;
  return validCoordinates(candidate) || validAddress(candidate);
}

export function readBrowseLocation(storage: Pick<Storage, "getItem" | "removeItem">): BrowseLocation | null {
  const raw = storage.getItem(BROWSE_LOCATION_STORAGE_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Partial<StoredBrowseLocation>;
    if (parsed.version !== 1 || !isBrowseLocation(parsed.location)) throw new Error("invalid browse location");
    return parsed.location;
  } catch {
    storage.removeItem(BROWSE_LOCATION_STORAGE_KEY);
    return null;
  }
}

export function writeBrowseLocation(storage: Pick<Storage, "setItem">, location: BrowseLocation): void {
  const safe: StoredBrowseLocation = { version: 1, location };
  storage.setItem(BROWSE_LOCATION_STORAGE_KEY, JSON.stringify(safe));
}

export function clearBrowseLocation(storage: Pick<Storage, "removeItem">): void {
  storage.removeItem(BROWSE_LOCATION_STORAGE_KEY);
}
