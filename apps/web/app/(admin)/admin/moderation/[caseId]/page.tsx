import { AdminModerationDetail } from "@/features/admin/admin-moderation-detail";

export default async function ModerationCasePage({ params }: { params: Promise<{ caseId: string }> }) {
  const { caseId } = await params;
  return <AdminModerationDetail caseId={caseId} />;
}
