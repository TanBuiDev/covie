import { ProfileOverview } from "@/features/profile/profile-overview";
import { requireUser } from "@/lib/auth/require-user";

export default async function MePage() {
  const user = await requireUser();
  return (
    <main className="profile-surface">
      <ProfileOverview user={user} notificationsAvailable />
    </main>
  );
}
