import { NeedDetail } from "@/features/needs/need-detail";

export default async function NeedDetailPage({ params }: { params: Promise<{ needId: string }> }) {
  const { needId } = await params;
  return <main style={{ padding: 24 }}><NeedDetail needId={needId} /></main>;
}
