import { LoadingState } from "@/components/ui/loading-state";

export default function SellerLoading() {
  return (
    <main className="seller-entry">
      <LoadingState label="Đang tải Seller Center…" />
    </main>
  );
}
