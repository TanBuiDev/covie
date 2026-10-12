import { z } from "zod";

const schema = z.object({
  API_INTERNAL_BASE_URL: z.string().url().default("http://localhost:8081"),
  COVIE_PUBLIC_URL: z.string().url().default("http://localhost:3000"),
  REDIS_URL: z.string().url().refine(value => ["redis:", "rediss:"].includes(new URL(value).protocol)).optional(),
});

export const serverEnv = schema.parse({
  API_INTERNAL_BASE_URL: process.env.API_INTERNAL_BASE_URL,
  COVIE_PUBLIC_URL: process.env.COVIE_PUBLIC_URL,
  REDIS_URL: process.env.REDIS_URL,
});
