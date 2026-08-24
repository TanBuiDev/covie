import type { ReactNode } from "react";

export function EmptyState({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <section className="state-card" aria-live="polite">
      <h2>{title}</h2>
      {description && <p>{description}</p>}
      {action && <div className="state-card__action">{action}</div>}
    </section>
  );
}
