import { SellerReputation } from "@/features/seller/seller-reputation";
import { readApiError } from "@/lib/api/api-error";
import { createServerReadClient } from "@/lib/api/server-api-client";
import { ShieldCheck } from "@phosphor-icons/react/dist/ssr";

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
      <header className="surface-header seller-desktop-heading">
        <p className="eyebrow">Seller Center / Tín nhiệm</p>
        <div style={{ display: "flex", alignItems: "center", gap: "12px", marginTop: "4px" }}>
          <div
            style={{
              width: "38px",
              height: "38px",
              borderRadius: "10px",
              background: "rgba(4, 120, 87, 0.08)",
              color: "#047857",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              flexShrink: 0,
            }}
          >
            <ShieldCheck size={22} weight="duotone" />
          </div>
          <h1 style={{ margin: 0 }}>Uy tín gian hàng</h1>
        </div>
        <p>Theo dõi đánh giá hiệu suất giao dịch và chỉ số hoàn thành đơn thực tế.</p>
      </header>
      <SellerReputation reputation={result.data?.reputation} />
    </section>
  );
}
