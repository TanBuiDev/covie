import type { components, paths } from "@covie/api-client";
import { createApiClient } from "@/lib/api/api-client";
import { parsedApiError } from "@/lib/api/api-error";

export type SocialActivityType = NonNullable<paths["/api/v1/me/social/activity"]["get"]["parameters"]["query"]>["type"];
export type SocialActivityEntry = components["schemas"]["SocialActivityEntry"];
export type SocialActivityPage = components["schemas"]["SocialActivityPage"];

export async function fetchMySocialActivity(type: SocialActivityType, cursor?: string, signal?: AbortSignal): Promise<SocialActivityPage> {
  const { data, response, error } = await createApiClient().GET("/api/v1/me/social/activity", {
    params: { query: { type, limit: 20, ...(cursor ? { cursor } : {}) } }, credentials: "include", cache: "no-store", signal,
  });
  if (!response.ok || !data) throw await parsedApiError(response, error);
  return data;
}
