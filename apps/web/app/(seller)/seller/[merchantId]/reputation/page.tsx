import { SellerReputation } from "@/features/seller/seller-reputation";
import { readApiError } from "@/lib/api/api-error";
import { createServerReadClient } from "@/lib/api/server-api-client";

export default async function SellerReputationPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const client = await createServerReadClient();
  const result = await client.GET("/api/v1/catalog/merchants/{merchantId}", {
    params: { path: { merchantId } },
  });

  if (!result.response.ok && result.response.status !== 404) {
    throw await readApiError(result.response);
  }

  return (
    <section className="seller-stack">
      <header className="surface-header seller-desktop-only">
        <h1>Uy tín cửa hàng</h1>
        <p>Trung tâm bán hàng</p>
      </header>
      <SellerReputation reputation={result.data?.reputation} />
    </section>
  );
}
