interface ErrorStateProps {
  message: string;
  onRetry?: () => void;
}

export function ErrorState({ message, onRetry }: ErrorStateProps) {
  return (
    <div className="state state-error" role="alert">
      <p className="state-title">We could not load this</p>
      <p className="muted">{message}</p>
      {onRetry && (
        <button type="button" className="button button-secondary" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}
