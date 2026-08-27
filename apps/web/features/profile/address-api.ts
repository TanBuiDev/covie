import type { components } from "@covie/api-client";
import { createApiClient } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";

async function dataOrThrow<T>(response: Response, data: T | undefined): Promise<T> {
  if (!response.ok || !data) throw await readApiError(response);
  return data;
}

export async function listSavedAddressesClient() {
  const { data, response } = await createApiClient().GET("/api/v1/me/addresses");
  return dataOrThrow(response, data);
}

export async function createSavedAddress(input: components["schemas"]["CreateUserAddressRequest"]) {
  const { data, response } = await createApiClient().POST("/api/v1/me/addresses", { body: input });
  return dataOrThrow(response, data);
}

export async function updateSavedAddress(
  addressId: string,
  input: components["schemas"]["UpdateUserAddressRequest"],
) {
  const { data, response } = await createApiClient().PATCH("/api/v1/me/addresses/{addressId}", {
    params: { path: { addressId } },
    body: input,
  });
  return dataOrThrow(response, data);
}

export async function deleteSavedAddress(addressId: string) {
  const { response } = await createApiClient().DELETE("/api/v1/me/addresses/{addressId}", {
    params: { path: { addressId } },
  });
  if (!response.ok) throw await readApiError(response);
}
