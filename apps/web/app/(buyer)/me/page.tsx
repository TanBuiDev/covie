import { ProfileOverview } from "@/features/profile/profile-overview";
import { requireUser } from "@/lib/auth/require-user";

export default async function MePage() {
  const user = await requireUser();
  return (
    <main className="buyer-surface">
      <ProfileOverview user={user} notificationsAvailable={false} />
    </main>
  );
}
