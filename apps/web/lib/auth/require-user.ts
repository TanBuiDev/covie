import { redirect } from "next/navigation";
import { createServerReadClient } from "@/lib/api/server-api-client";
import { safeReturnTo } from "./return-to";

export async function requireUser(returnTo?: string) {
  const client = await createServerReadClient();
  const { data, response } = await client.GET("/api/v1/me");

  if (response.status === 401 || response.status === 403) {
    const destination = safeReturnTo(returnTo);
    redirect(destination === "/" ? "/login" : `/login?returnTo=${encodeURIComponent(destination)}`);
  }
  if (!response.ok || !data) throw new Error(`Unable to load the authenticated account (${response.status}).`);

  return data;
}
