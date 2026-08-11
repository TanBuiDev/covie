import { createServerReadClient } from "@/lib/api/server-api-client";

export async function getActiveSessions() {
  const client = await createServerReadClient();
  const { data, response } = await client.GET("/api/v1/me/security/sessions");
  if (!response.ok || !data) throw new Error(`Unable to load active sessions (${response.status}).`);
  return data;
}

export async function getSavedAddresses() {
  const client = await createServerReadClient();
  const { data, response } = await client.GET("/api/v1/me/addresses");
  if (!response.ok || !data) throw new Error(`Unable to load saved addresses (${response.status}).`);
  return data;
}
