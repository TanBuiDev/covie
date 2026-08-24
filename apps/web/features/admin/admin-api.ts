import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import type { AdminSettingDefinition } from "./settings-editor";

export async function loadAdminSettingDefinitions(): Promise<AdminSettingDefinition[]> {
  const response = await requestBrowserApi("GET", "/api/v1/admin/settings");
  if (!response.ok) throw await readApiError(response);
  return (await response.json()) as AdminSettingDefinition[];
}

export async function updateGlobalAdminSetting(key: string, value: string, etag?: string): Promise<string | undefined> {
  let jsonValue: unknown;
  try {
    jsonValue = JSON.parse(value);
  } catch {
    jsonValue = value;
  }
  const headers: HeadersInit = { "Content-Type": "application/json" };
  if (etag) headers["If-Match"] = etag;
  const response = await requestBrowserApi("PATCH", `/api/v1/admin/settings/${encodeURIComponent(key)}`,
    { scopeType: "GLOBAL", value: jsonValue }, headers);
  if (!response.ok) throw await readApiError(response);
  return response.headers.get("etag") ?? undefined;
}
