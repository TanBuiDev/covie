import type { components } from "@covie/api-client";
import { createApiClient } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";

export async function createSavedAddress(input: components["schemas"]["CreateUserAddressRequest"]) {
  const client = createApiClient();
  const { data, response } = await client.POST("/api/v1/me/addresses", { body: input });
  if (!response.ok || !data) throw await readApiError(response);
  return data;
}
