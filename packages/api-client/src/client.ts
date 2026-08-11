import createClient from "openapi-fetch";
import type { paths } from "./generated/schema";

export function createCovieClient(baseUrl: string) {
  return createClient<paths>({ baseUrl });
}
