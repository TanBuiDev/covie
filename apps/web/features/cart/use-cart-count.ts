"use client";

import { useEffect, useState } from "react";
import { listCarts } from "./cart-api";

export function useCartCount(): number {
  const [count, setCount] = useState<number>(0);

  useEffect(() => {
    let active = true;

    async function refresh() {
      try {
        const carts = await listCarts();
        if (!active) return;
        const total = (carts ?? []).reduce(
          (sum, cart) => sum + (cart.items ?? []).reduce((s, it) => s + (it.quantity ?? 0), 0),
          0,
        );
        setCount(total);
      } catch {
        if (active) setCount(0);
      }
    }

    void refresh();

    const handleUpdate = () => {
      void refresh();
    };

    window.addEventListener("covie:cart-updated", handleUpdate);
    window.addEventListener("focus", handleUpdate);

    return () => {
      active = false;
      window.removeEventListener("covie:cart-updated", handleUpdate);
      window.removeEventListener("focus", handleUpdate);
    };
  }, []);

  return count;
}
