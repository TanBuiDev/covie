import { BuyerReputation } from "@/features/reputation/buyer-reputation";
import { readApiError } from "@/lib/api/api-error";
import { createServerReadClient } from "@/lib/api/server-api-client";

export default async function BuyerReputationPage() {
  const client = await createServerReadClient();
  const { data, response } = await client.GET("/api/v1/me/reputation");
  if (!response.ok || !data) throw await readApiError(response);
  return <main><BuyerReputation reputation={data} /></main>;
}
