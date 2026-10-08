import type { components } from "@covie/api-client";
import { createApiClient } from "@/lib/api/api-client";
import { parsedApiError } from "@/lib/api/api-error";

export type Profile = components["schemas"]["MeResponse"] & {
  phone?: string | null;
  avatarMediaId?: string | null;
  avatarUrl?: string | null;
};

export type ProfileUpdate = components["schemas"]["UpdateMeRequest"] & {
  phone?: string | null;
  avatarMediaId?: string | null;
};

export async function updateProfile(input: ProfileUpdate): Promise<Profile> {
  const { data, response, error } = await createApiClient().PATCH("/api/v1/me", {
    body: input as components["schemas"]["UpdateMeRequest"],
  });
  if (!response.ok) throw await parsedApiError(response, error);
  if (!data || typeof data.id !== "string" || !data.id || typeof data.displayName !== "string" || !data.displayName.trim()) {
    throw new Error("PROFILE_RESPONSE_UNAVAILABLE");
  }
  return data as Profile;
}
