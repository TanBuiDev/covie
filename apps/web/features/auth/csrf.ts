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
    cached.catch(() => {
      cached = null;
    });
  }
  return cached;
}
