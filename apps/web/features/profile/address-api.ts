import type { components } from "@covie/api-client";
import { createApiClient } from "@/lib/api/api-client";
import { CovieApiError, isApiErrorShape, readApiError } from "@/lib/api/api-error";

async function dataOrThrow<T>(response: Response, data: T | undefined, error?: unknown): Promise<T> {
  if (!response.ok) {
    if (isApiErrorShape(error)) {
      throw new CovieApiError(
        response.status,
        error.error.code,
        error.error.message ?? error.error.code,
        error.error.requestId,
        error.error.fields,
      );
    }
    throw await readApiError(response);
  }
  if (!data) throw await readApiError(response);
  return data;
}

export async function listSavedAddressesClient() {
  const { data, error, response } = await createApiClient().GET("/api/v1/me/addresses");
  return dataOrThrow(response, data, error);
}

export async function createSavedAddress(input: components["schemas"]["CreateUserAddressRequest"]) {
  const { data, error, response } = await createApiClient().POST("/api/v1/me/addresses", { body: input });
  return dataOrThrow(response, data, error);
}

export async function updateSavedAddress(
  addressId: string,
  input: components["schemas"]["UpdateUserAddressRequest"],
) {
  const { data, error, response } = await createApiClient().PATCH("/api/v1/me/addresses/{addressId}", {
    params: { path: { addressId } },
    body: input,
  });
  return dataOrThrow(response, data, error);
}

export async function deleteSavedAddress(addressId: string) {
  const { error, response } = await createApiClient().DELETE("/api/v1/me/addresses/{addressId}", {
    params: { path: { addressId } },
  });
  if (!response.ok) {
    const parsedError: unknown = error;
    if (isApiErrorShape(parsedError)) {
      throw new CovieApiError(
        response.status,
        parsedError.error.code,
        parsedError.error.message ?? parsedError.error.code,
        parsedError.error.requestId,
        parsedError.error.fields,
      );
    }
    throw await readApiError(response);
  }
}
