import { Component, type ErrorInfo, type ReactNode } from 'react';

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
}

/** Catches rendering crashes so one broken view cannot blank the whole app. */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('UI error:', error.message, info.componentStack);
  }

  render(): ReactNode {
    if (this.state.hasError) {
      return (
        <div className="card state state-error" role="alert">
          <p className="state-title">Something went wrong</p>
          <p className="muted">An unexpected error occurred. Reloading the page usually fixes it.</p>
          <button type="button" className="button button-secondary" onClick={() => window.location.reload()}>
            Reload the page
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}
