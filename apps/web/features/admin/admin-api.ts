import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import type { AdminSettingDefinition } from "./settings-editor";

export async function loadAdminSettingDefinitions(): Promise<AdminSettingDefinition[]> {
  const response = await requestBrowserApi("GET", "/api/v1/admin/settings");
  if (!response.ok) throw await readApiError(response);
  return (await response.json()) as AdminSettingDefinition[];
}
