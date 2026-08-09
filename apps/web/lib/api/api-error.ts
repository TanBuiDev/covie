export type ApiErrorShape = {
  error: {
    code: string;
    message?: string;
    details?: Record<string, unknown>;
    fields?: Record<string, string[]>;
    requestId?: string;
  };
};

export function isApiErrorShape(value: unknown): value is ApiErrorShape {
  if (typeof value !== "object" || value === null || !("error" in value)) return false;
  const error = (value as { error?: unknown }).error;
  return typeof error === "object" && error !== null && "code" in error;
}
