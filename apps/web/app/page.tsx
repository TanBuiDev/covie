import { ConsumerShell } from "@/components/layout/consumer-shell";
import { HomeScreen } from "@/features/home/home-screen";
import { AdDiscoveryLayout } from "@/features/ads/ad-discovery-layout";

export default function HomePage() {
  return (
    <ConsumerShell>
      <AdDiscoveryLayout><HomeScreen /></AdDiscoveryLayout>
    </ConsumerShell>
  );
}
