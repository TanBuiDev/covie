import type { components } from "@covie/api-client";
import { createApiClient } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";

export type BuyerReputation = components["schemas"]["BuyerReputationSummary"];

export async function getMyReputation(): Promise<BuyerReputation> {
  const { data, response } = await createApiClient().GET("/api/v1/me/reputation");
  if (!response.ok || !data) throw await readApiError(response);
  return data;
}
