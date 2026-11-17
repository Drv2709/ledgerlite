import { useMutation } from '@tanstack/react-query';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { getErrorMessage } from '../api/errors';
import { useAuth } from '../auth/useAuth';
import { ErrorBoundary } from './ErrorBoundary';

function navClass({ isActive }: { isActive: boolean }): string {
  return isActive ? 'nav-link active' : 'nav-link';
}

/** Header, navigation and the page outlet for every signed-in screen. */
export function AppLayout() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const logoutMutation = useMutation({ mutationFn: logout });

  return (
    <div className="page">
      <header className="topbar">
        <span className="brand">LedgerLite</span>
        <nav aria-label="Main" className="nav">
          <NavLink to="/" end className={navClass}>
            Dashboard
          </NavLink>
          <NavLink to="/transactions" className={navClass}>
            Transactions
          </NavLink>
          <NavLink to="/transfer" className={navClass}>
            Send money
          </NavLink>
        </nav>
        <div className="topbar-right">
          <span className="muted small">{user?.email}</span>
          <button
            type="button"
            className="button button-secondary"
            onClick={() => logoutMutation.mutate()}
            disabled={logoutMutation.isPending}
          >
            {logoutMutation.isPending ? 'Signing out…' : 'Sign out'}
          </button>
        </div>
      </header>

      {logoutMutation.isError && (
        <div className="form-error" role="alert">
          {getErrorMessage(logoutMutation.error)}
        </div>
      )}

      <main>
        {/* key: navigating to another page resets a crashed boundary */}
        <ErrorBoundary key={location.pathname}>
          <Outlet />
        </ErrorBoundary>
      </main>
    </div>
  );
}
