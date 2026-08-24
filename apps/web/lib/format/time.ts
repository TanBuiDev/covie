const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

export type CountdownPresentation = {
  expired: boolean;
  urgent: boolean;
  label: string;
};

export function countdownPresentation(isoTime: string, now = Date.now()): CountdownPresentation {
  const target = Date.parse(isoTime);
  if (!Number.isFinite(target)) return { expired: false, urgent: false, label: "Thời gian đang được cập nhật" };
  const remaining = target - now;
  if (remaining <= 0) return { expired: true, urgent: true, label: "Đã tới hạn, đang cập nhật..." };
  if (remaining < HOUR) return { expired: false, urgent: true, label: "Sắp hết hạn" };
  if (remaining < DAY) return { expired: false, urgent: false, label: `Còn ${Math.ceil(remaining / HOUR)} giờ` };
  return { expired: false, urgent: false, label: `Còn ${Math.ceil(remaining / DAY)} ngày` };
}

export function formatDateTime(isoTime: string, locale = "vi-VN"): string {
  const value = new Date(isoTime);
  if (Number.isNaN(value.getTime())) return "—";
  return new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" }).format(value);
}
