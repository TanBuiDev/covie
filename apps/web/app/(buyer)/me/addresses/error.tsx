"use client";

import { AddressList } from "@/features/profile/address-list";

export default function AddressesError({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return <AddressList addresses={[]} state="error" onRetry={reset} />;
}
