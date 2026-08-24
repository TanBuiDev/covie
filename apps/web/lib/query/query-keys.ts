export const queryKeys = {
  me: ["me"] as const,
  carts: ["me", "carts"] as const,
  cart: (cartId: string) => ["me", "carts", cartId] as const,
  orders: (scope: "buyer" | "seller", ownerId?: string) =>
    ["orders", scope, ownerId ?? "me"] as const,
  order: (orderId: string) => ["orders", "detail", orderId] as const,
  needs: ["me", "needs"] as const,
  need: (needId: string) => ["me", "needs", needId] as const,
  notifications: ["me", "notifications"] as const,
  catalog: ["catalog"] as const,
  catalogSearch: (scope: Record<string, unknown>) => ["catalog", "search", scope] as const,
  merchant: (merchantId: string) => ["merchant", merchantId] as const,
  sellerInventory: (merchantId: string) => ["seller", merchantId, "inventory"] as const,
  adminSettings: ["admin", "settings"] as const,
  moderation: ["admin", "moderation"] as const,
} as const;
