import Link from "next/link";
import { ConsumerShell } from "@/components/layout/consumer-shell";
import { LocationChooser } from "@/features/location/location-chooser";

export default function HomePage() {
  return (
    <ConsumerShell>
      <section>
        <h1>CoVie</h1>
        <p>Marketplace gần bạn.</p>
        <p><Link href="/search">Khám phá sản phẩm và dịch vụ</Link></p>
        <LocationChooser />
      </section>
    </ConsumerShell>
  );
}
