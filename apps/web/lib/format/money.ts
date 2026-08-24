export type MoneyLike = { amount: number; currency: string };

export function formatMoney(money: MoneyLike, locale = "vi-VN"): string {
  if (!Number.isFinite(money.amount) || !money.currency) return "—";
  try {
    return new Intl.NumberFormat(locale, {
      style: "currency",
      currency: money.currency,
      maximumFractionDigits: 0,
    }).format(money.amount);
  } catch {
    return `${money.amount.toLocaleString(locale)} ${money.currency}`;
  }
}

export function negotiatedShippingCopy(): string {
  return "Thỏa thuận với người bán";
}
