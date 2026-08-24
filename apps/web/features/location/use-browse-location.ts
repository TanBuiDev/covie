"use client";

import { useContext } from "react";
import { BrowseLocationContext } from "./browse-location-provider";

export function useBrowseLocation() {
  const value = useContext(BrowseLocationContext);
  if (!value) throw new Error("useBrowseLocation must be used within BrowseLocationProvider");
  return value;
}
