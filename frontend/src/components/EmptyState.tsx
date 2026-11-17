import type { ReactNode } from 'react';

interface EmptyStateProps {
  title: string;
  description: string;
  action?: ReactNode;
}

export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className="state">
      <p className="state-title">{title}</p>
      <p className="muted">{description}</p>
      {action}
    </div>
  );
}
