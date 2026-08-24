import type { ReactNode } from "react";

export function ErrorState({ title = "Không thể tải nội dung", description, action }: { title?: string; description?: string; action?: ReactNode }) {
  return (
    <section className="state-card state-card--error" role="alert">
      <h2>{title}</h2>
      {description && <p>{description}</p>}
      {action && <div className="state-card__action">{action}</div>}
    </section>
  );
}
