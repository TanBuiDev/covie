import { z } from "zod";

const schema = z.object({
  API_INTERNAL_BASE_URL: z.string().url().default("http://localhost:8081"),
  COVIE_PUBLIC_URL: z.string().url().default("http://localhost:3000"),
});

export const serverEnv = schema.parse({
  API_INTERNAL_BASE_URL: process.env.API_INTERNAL_BASE_URL,
  COVIE_PUBLIC_URL: process.env.COVIE_PUBLIC_URL,
});
