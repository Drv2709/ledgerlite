export function FullPageSpinner() {
  return (
    <div className="center-screen" role="status" aria-live="polite">
      <div className="spinner" aria-hidden="true" />
      <span className="visually-hidden">Loading</span>
    </div>
  );
}
