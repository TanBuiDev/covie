import { z } from "zod";

const schema = z.object({
  API_INTERNAL_BASE_URL: z.string().url().default("http://localhost:8080"),
});

export const serverEnv = schema.parse({
  API_INTERNAL_BASE_URL: process.env.API_INTERNAL_BASE_URL,
});
