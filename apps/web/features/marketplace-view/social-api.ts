import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError, type CovieApiError } from "@/lib/api/api-error";

export type SocialSummary = {
  itemId: string;
  likes: number;
  comments: number;
  liked: boolean;
  saved: boolean;
};
export type SocialComment = {
  id: string;
  itemId: string;
  authorName: string;
  body: string;
  createdAt: string;
  own: boolean;
};
export type SocialCommentPage = {
  items: SocialComment[];
  nextCursor: string | null;
};

async function json<T>(response: Response): Promise<T> {
  if (!response.ok) throw await readApiError(response);
  return response.json() as Promise<T>;
}

export async function fetchSocialSummaries(ids: string[]): Promise<Record<string, SocialSummary>> {
  if (ids.length === 0) return {};
  if (ids.length > 40) throw new Error("Tối đa 40 sản phẩm mỗi lần yêu cầu.");
  const query = new URLSearchParams();
  for (const id of Array.from(new Set(ids))) query.append("itemIds", id);
  return json(await requestBrowserApi("GET", "/api/v1/catalog/social-summary?" + query));
}

// Serialize competing like/save updates for the same product within a tab.
// Each request returns the authoritative *complete* summary, so overlapping
// mutations could otherwise overwrite the confirmed state of another action.
const pendingItems = new Set<string>();
export async function changeSocialReaction(itemId: string, kind: "likes" | "saves", enabled: boolean): Promise<SocialSummary | null> {
  if (pendingItems.has(itemId)) return null;
  pendingItems.add(itemId);
  try {
    const path = "/api/v1/me/catalog/items/" + encodeURIComponent(itemId) + "/" + kind;
    return await json<SocialSummary>(await requestBrowserApi(enabled ? "PUT" : "DELETE", path));
  } finally {
    pendingItems.delete(itemId);
  }
}

export async function fetchSocialComments(itemId: string, before?: string): Promise<SocialCommentPage> {
  const query = new URLSearchParams({ limit: "5" });
  if (before) query.set("before", before);
  return json(await requestBrowserApi("GET",
    "/api/v1/catalog/items/" + encodeURIComponent(itemId) + "/comments?" + query));
}
export async function addSocialComment(itemId: string, body: string): Promise<SocialComment> {
  return json(await requestBrowserApi("POST",
    "/api/v1/me/catalog/items/" + encodeURIComponent(itemId) + "/comments", { body }));
}
export async function removeSocialComment(itemId: string, commentId: string): Promise<void> {
  const response = await requestBrowserApi("DELETE",
    "/api/v1/me/catalog/items/" + encodeURIComponent(itemId) + "/comments/" + encodeURIComponent(commentId));
  if (!response.ok) throw await readApiError(response);
}
export function isSignInRequired(error: unknown): boolean {
  if (typeof error !== "object" || error === null) return false;
  const data = error as Partial<CovieApiError>;
  return data.status === 401 || data.code === "AUTHENTICATION_REQUIRED" || data.code === "AUTH_SESSION_INVALID";
}
export function socialErrorMessage(error: unknown): string {
  if (isSignInRequired(error)) return "Đăng nhập để tương tác với sản phẩm.";
  if (typeof error === "object" && error !== null && "status" in error) {
    const status = (error as { status?: number }).status;
    if (status === 429) return "Bạn thao tác quá nhanh. Hãy thử lại sau.";
    if (status === 404) return "Sản phẩm hoặc nội dung này hiện không còn khả dụng.";
    if (status === 403) return "Bạn không có quyền thực hiện thao tác này.";
  }
  return "Không thể thực hiện. Kiểm tra kết nối và thử lại.";
}
