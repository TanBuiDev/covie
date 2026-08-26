import { requireUser } from "@/lib/auth/require-user";
import { NotificationInbox } from "@/features/notifications/notification-inbox";

export default async function NotificationsPage() {
  await requireUser();
  return <main className="notifications-surface"><NotificationInbox /></main>;
}
