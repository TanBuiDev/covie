import { describe, expect, it } from "vitest";
import { BROWSE_LOCATION_STORAGE_KEY, readBrowseLocation, writeBrowseLocation } from "@/lib/location/browse-location-storage";

function memoryStorage() {
  const values = new Map<string, string>();
  return {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
    raw: values,
  };
}

describe("browse location storage", () => {
  it("stores only the versioned browse selector preference", () => {
    const storage = memoryStorage();
    writeBrowseLocation(storage, { kind: "address", addressId: "address-1", label: "Nhà" });
    const raw = storage.raw.get(BROWSE_LOCATION_STORAGE_KEY) ?? "";
    expect(raw).toContain("address-1");
    expect(raw).not.toContain("phone");
    expect(readBrowseLocation(storage)).toEqual({ kind: "address", addressId: "address-1", label: "Nhà" });
  });

  it("clears malformed or old-version data", () => {
    const storage = memoryStorage();
    storage.setItem(BROWSE_LOCATION_STORAGE_KEY, JSON.stringify({ version: 0, location: { kind: "coordinates", lat: 10, lng: 20, label: "Old" } }));
    expect(readBrowseLocation(storage)).toBeNull();
    expect(storage.raw.has(BROWSE_LOCATION_STORAGE_KEY)).toBe(false);
  });
});
