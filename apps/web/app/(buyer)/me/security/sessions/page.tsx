import { SessionList } from "@/features/auth/session-list";
import { getActiveSessions } from "@/features/profile/profile-api";
import { requireUser } from "@/lib/auth/require-user";

export default async function SessionsPage() {
  await requireUser();
  const sessions = await getActiveSessions();
  return <main><h1>Bảo mật</h1><SessionList sessions={sessions} /></main>;
}
