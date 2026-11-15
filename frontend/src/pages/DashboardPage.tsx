import { useMutation } from '@tanstack/react-query';
import { getErrorMessage } from '../api/errors';
import { useAuth } from '../auth/useAuth';

/** Placeholder: P7 replaces this with balances, recent transactions and the transfer form. */
export default function DashboardPage() {
  const { user, logout } = useAuth();
  const logoutMutation = useMutation({ mutationFn: logout });

  return (
    <main className="page">
      <header className="topbar">
        <span className="brand">LedgerLite</span>
        <div className="topbar-right">
          <span className="muted">{user?.email}</span>
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

      <section className="card">
        <h1>You are signed in</h1>
        <p className="muted">
          Signed in as <strong>{user?.email}</strong> ({user?.role}). Your accounts, balances and transfers arrive in
          the next phase.
        </p>
        {logoutMutation.isError && (
          <div className="form-error" role="alert">
            {getErrorMessage(logoutMutation.error)}
          </div>
        )}
      </section>
    </main>
  );
}
