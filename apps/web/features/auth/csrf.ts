export type CsrfToken = {
  headerName: string;
  token: string;
};

let cached: Promise<CsrfToken> | null = null;

export function invalidateCsrfToken() {
  cached = null;
}

export function getCsrfToken(): Promise<CsrfToken> {
  if (!cached) {
    cached = fetch("/api/v1/auth/csrf", {
      method: "GET",
      credentials: "same-origin",
      cache: "no-store",
    }).then(async (response) => {
      if (!response.ok) throw new Error("Unable to obtain CSRF token");
      return (await response.json()) as CsrfToken;
    });
    cached.finally(() => {
      // Deduplicate only concurrent callers. A later mutation must read the
      // token that currently matches the browser cookie (auth/session flows
      // can rotate or invalidate that cookie between requests).
      cached = null;
    }).catch(() => undefined);
  }
  return cached;
}
