import { refreshServerSession } from "@/lib/auth/ssr-refresh";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export const GET = refreshServerSession;
