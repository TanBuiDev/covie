import { NextRequest, NextResponse } from "next/server";

/** Forward the actual page destination; never trust a client-supplied return header. */
export function proxy(request: NextRequest) {
  const headers = new Headers(request.headers);
  headers.set("x-covie-ssr-return-to", `${request.nextUrl.pathname}${request.nextUrl.search}`);
  return NextResponse.next({ request: { headers } });
}

export const config = { matcher: ["/((?!api/|_next/|favicon.ico).*)"] };
