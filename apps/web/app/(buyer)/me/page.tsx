import { ProfileOverview } from "@/features/profile/profile-overview";
import { requireUser } from "@/lib/auth/require-user";

export default async function MePage() {
  const user = await requireUser("/me");
  return (
    <main className="profile-surface">
      <ProfileOverview user={user} notificationsAvailable />
    </main>
  );
}
