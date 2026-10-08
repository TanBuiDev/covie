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

export class CovieApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly requestId?: string,
    public readonly fields?: Record<string, string[]>,
  ) {
    super(message);
    this.name = "CovieApiError";
  }
}

export async function readApiError(response: Response): Promise<CovieApiError> {
  const value: unknown = await response.json().catch(() => null);
  if (isApiErrorShape(value)) {
    return new CovieApiError(
      response.status,
      value.error.code,
      value.error.message ?? value.error.code,
      value.error.requestId,
      value.error.fields,
    );
  }
  return new CovieApiError(response.status, "HTTP_ERROR", `HTTP ${response.status}`);
}

/** Uses the error body already parsed by openapi-fetch before falling back to the raw response. */
export async function parsedApiError(response: Response, value?: unknown): Promise<CovieApiError> {
  if (isApiErrorShape(value)) {
    return new CovieApiError(
      response.status,
      value.error.code,
      value.error.message ?? value.error.code,
      value.error.requestId,
      value.error.fields,
    );
  }
  return readApiError(response);
}
