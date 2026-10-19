import { notFound } from "next/navigation";
import { SocialActivityScreen } from "@/features/profile/social-activity-screen";
import { requireUser } from "@/lib/auth/require-user";

export default async function ActivityPage({ searchParams }: {
  searchParams: Promise<{ type?: string | string[] }>;
}) {
  if (process.env.COVIE_SOCIAL_V2_ACTIVITY_ENABLED !== "true") notFound();
  const requested = (await searchParams).type;
  const type = requested === "SAVED" || requested === "COMMENTED" ? requested : "LIKED";
  const user = await requireUser(`/me/activity?type=${type}`);
  return <main className="profile-surface"><SocialActivityScreen key={user.id} accountId={user.id} type={type} /></main>;
}
