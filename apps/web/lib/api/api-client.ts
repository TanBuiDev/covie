import { createCovieClient } from "@covie/api-client";

export function createApiClient(baseUrl = "") {
  return createCovieClient(baseUrl);
}
