import type { ReactNode } from "react";

type MetricCardProps = {
  label: string;
  value: string;
  hint: string;
  icon: ReactNode;
};

export function MetricCard({ label, value, hint, icon }: MetricCardProps) {
  return (
    <article className="metric-card panel">
      <div className="metric-icon" aria-hidden="true">
        {icon}
      </div>
      <p className="metric-label">{label}</p>
      <p className="metric-value">{value}</p>
      <p className="metric-hint">{hint}</p>
    </article>
  );
}
