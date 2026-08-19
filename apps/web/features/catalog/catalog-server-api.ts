import { readApiError } from "@/lib/api/api-error";
import { createServerReadClient } from "@/lib/api/server-api-client";

export async function listCatalogItemsForServer(merchantId: string) {
  const client = await createServerReadClient();
  const { data, response } = await client.GET("/api/v1/merchants/{merchantId}/catalog-items", { params: { path: { merchantId } } });
  if (!response.ok || !data) throw await readApiError(response);
  return data;
}
