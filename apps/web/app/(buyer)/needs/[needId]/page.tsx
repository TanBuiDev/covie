import { NeedDetail } from "@/features/needs/need-detail";

export default async function NeedDetailPage({ params }: { params: Promise<{ needId: string }> }) {
  const { needId } = await params;
  return <main className="needs-surface"><NeedDetail needId={needId} /></main>;
}
