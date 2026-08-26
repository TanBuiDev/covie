import type { components } from "@covie/api-client";
import { createApiClient } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";

export type CreateReportInput = components["schemas"]["CreateReportRequest"];
export type ReportCreated = components["schemas"]["ReportCreatedResponse"];
export type ReportTargetType = NonNullable<CreateReportInput["targetType"]>;

export async function createReport(input: CreateReportInput, idempotencyKey: string): Promise<ReportCreated> {
  const { data, response } = await createApiClient().POST("/api/v1/reports", {
    body: input,
    params: { header: { "Idempotency-Key": idempotencyKey } },
  });
  if (!response.ok || !data) throw await readApiError(response);
  return data;
}
