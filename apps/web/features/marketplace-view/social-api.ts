import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";

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
  for (const id of ids) query.append("itemIds", id);
  return json(await requestBrowserApi("GET", "/api/v1/catalog/social-summary?" + query.toString()));
}

export async function changeSocialReaction(itemId: string, kind: "likes" | "saves", enabled: boolean): Promise<SocialSummary> {
  const path = "/api/v1/me/catalog/items/" + encodeURIComponent(itemId) + "/" + kind;
  return json(await requestBrowserApi(enabled ? "PUT" : "DELETE", path));
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
  const data = error as { status?: number; code?: string };
  return data.status === 401 || data.code === "AUTHENTICATION_REQUIRED" || data.code === "AUTH_SESSION_INVALID";
}
