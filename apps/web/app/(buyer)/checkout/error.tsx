"use client";

import { CheckoutSafeError } from "@/features/checkout/checkout-preview";

export default function CheckoutError({ retry }: { error: Error & { digest?: string }; retry: () => void }) {
  return <main><CheckoutSafeError retry={retry} /></main>;
}
