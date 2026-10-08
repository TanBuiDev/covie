import { createApiClient } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";

export async function getGoogleAuthStatus(): Promise<boolean> {
  const {data,response} = await createApiClient().GET("/api/v1/auth/google/status");
  if (!response.ok) throw await readApiError(response);
  if (typeof data?.configured !== "boolean") throw new Error("Invalid Google capability response");
  return data.configured;
}
