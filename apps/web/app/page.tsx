import { ConsumerShell } from "@/components/layout/consumer-shell";
import { HomeScreen } from "@/features/home/home-screen";

export default function HomePage() {
  return (
    <ConsumerShell>
      <HomeScreen />
    </ConsumerShell>
  );
}
