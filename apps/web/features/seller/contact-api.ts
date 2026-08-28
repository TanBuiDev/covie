import type { components } from "@covie/api-client";
import { createApiClient } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";

export type MerchantContact = components["schemas"]["MerchantContactResponse"];
export type MerchantContactInput = components["schemas"]["MerchantContactRequest"];

async function requireData<T>(response: Response, data: T | undefined): Promise<T> {
  if (!response.ok || !data) throw await readApiError(response);
  return data;
}

export async function createMerchantContact(merchantId: string, input: MerchantContactInput) {
  const { data, response } = await createApiClient().POST("/api/v1/merchants/{merchantId}/contacts", {
    params: { path: { merchantId } },
    body: input,
  });
  return requireData(response, data);
}

export async function updateMerchantContact(
  merchantId: string,
  contactId: string,
  version: number,
  input: MerchantContactInput,
) {
  const { data, response } = await createApiClient().PATCH("/api/v1/merchants/{merchantId}/contacts/{contactId}", {
    params: {
      path: { merchantId, contactId },
      header: { "If-Match": `"v${version}"` },
    },
    body: input,
  });
  return requireData(response, data);
}

export async function deleteMerchantContact(merchantId: string, contactId: string) {
  const { response } = await createApiClient().DELETE("/api/v1/merchants/{merchantId}/contacts/{contactId}", {
    params: { path: { merchantId, contactId } },
  });
  if (!response.ok) throw await readApiError(response);
}
