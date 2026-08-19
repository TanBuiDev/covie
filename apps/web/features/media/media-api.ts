import type { components } from "@covie/api-client";
import { createApiClient } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";

export type MediaPurpose = components["schemas"]["CreateMediaUploadRequest"]["purpose"];
export type CreateMediaUploadRequest = components["schemas"]["CreateMediaUploadRequest"];
export type MediaUploadResponse = components["schemas"]["MediaUploadResponse"];
export type MediaResponse = components["schemas"]["MediaResponse"];

async function requireData<T>(response: Response, data: T | undefined): Promise<T> {
  if (!response.ok || !data) throw await readApiError(response);
  return data;
}

export async function createUpload(input: CreateMediaUploadRequest): Promise<MediaUploadResponse> {
  const client = createApiClient();
  const { data, response } = await client.POST("/api/v1/media/uploads", { body: input });
  return requireData(response, data);
}

export async function completeUpload(mediaId: string, idempotencyKey = crypto.randomUUID()): Promise<MediaResponse> {
  const client = createApiClient();
  const { data, response } = await client.POST("/api/v1/media/{mediaId}/complete", {
    params: { path: { mediaId }, header: { "Idempotency-Key": idempotencyKey } },
  });
  return requireData(response, data);
}
