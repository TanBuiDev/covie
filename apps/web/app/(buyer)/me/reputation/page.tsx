import { BuyerReputationScreen } from "@/features/reputation/buyer-reputation-screen";
import { requireUser } from "@/lib/auth/require-user";

export default async function ReputationPage() {
  await requireUser("/me/reputation");
  return <main><BuyerReputationScreen /></main>;
}
