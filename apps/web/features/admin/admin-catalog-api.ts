import type { components } from "@covie/api-client";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";

export type AdminCatalogItem = components["schemas"]["AdminCatalogItemSummaryResponse"];
export type AdminCatalogDetail = components["schemas"]["AdminCatalogItemDetailResponse"];
type AdminCatalogPage = components["schemas"]["AdminPageResponseAdminCatalogItemSummaryResponse"];
type AdminCatalogCasePage = components["schemas"]["AdminCatalogCasePageResponse"];
type AdminCatalogCaseOpened = components["schemas"]["AdminCatalogCaseOpenedResponse"];
export type AdminCatalogCaseReason = components["schemas"]["AdminReasonRequest"];

export async function openAdminCatalogCase(itemId: string, reason: AdminCatalogCaseReason, key: string): Promise<AdminCatalogCaseOpened> {
  const response = await requestBrowserApi("POST", "/api/v1/admin/catalog-items/" + encodeURIComponent(itemId)
    + "/moderation-cases", reason, { "Idempotency-Key": key });
  if (!response.ok) throw await readApiError(response);
  const payload: AdminCatalogCaseOpened = await response.json();
  if (!payload.caseId || !/^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i.test(payload.caseId)
    || typeof payload.created !== "boolean") throw new Error("Không thể đọc hồ sơ kiểm duyệt vừa mở.");
  return payload;
}

async function read<T>(path: string): Promise<T> {
  const response = await requestBrowserApi("GET", path);
  if (!response.ok) throw await readApiError(response);
  return response.json() as Promise<T>;
}

export async function fetchAdminCatalog(query: string, page: number): Promise<AdminCatalogPage> {
  const params = new URLSearchParams(query);
  params.set("page", String(page));
  params.set("size", "20");
  const payload = await read<AdminCatalogPage>("/api/v1/admin/catalog-items?" + params);
  if (!Array.isArray(payload.items) || typeof payload.total !== "number") throw new Error("Không thể đọc danh sách nội dung.");
  return payload;
}

export async function fetchAdminCatalogDetail(itemId: string): Promise<AdminCatalogDetail> {
  const payload = await read<AdminCatalogDetail>("/api/v1/admin/catalog-items/" + encodeURIComponent(itemId));
  if (payload.item?.id !== itemId) throw new Error("Không thể đọc chi tiết nội dung.");
  return payload;
}

export async function fetchAdminCatalogCases(itemId: string, cursor: string): Promise<AdminCatalogCasePage> {
  const query = new URLSearchParams({ limit: "50", cursor });
  const payload = await read<AdminCatalogCasePage>("/api/v1/admin/catalog-items/" + encodeURIComponent(itemId) + "/moderation-cases?" + query);
  if (!Array.isArray(payload.items) || typeof payload.hasMore !== "boolean"
    || payload.hasMore && !payload.nextCursor) throw new Error("Không thể đọc lịch sử hồ sơ.");
  return payload;
}
