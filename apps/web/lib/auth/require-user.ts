import { redirect } from "next/navigation";
import { createServerReadClient } from "@/lib/api/server-api-client";

export async function requireUser() {
  const client = await createServerReadClient();
  const { data, response } = await client.GET("/api/v1/me");

  if (response.status === 401 || response.status === 403) redirect("/login");
  if (!response.ok || !data) throw new Error(`Unable to load the authenticated account (${response.status}).`);

  return data;
}
