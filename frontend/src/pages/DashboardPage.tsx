import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { getErrorMessage } from '../api/errors';
import { AccountCard } from '../components/AccountCard';
import { CreateAccountForm } from '../components/CreateAccountForm';
import { EmptyState } from '../components/EmptyState';
import { ErrorState } from '../components/ErrorState';
import { Skeleton } from '../components/Skeleton';
import { TransactionTable } from '../components/TransactionTable';
import { useAccounts } from '../hooks/useAccounts';
import { useTransactions } from '../hooks/useTransactions';

const RECENT_COUNT = 5;

export default function DashboardPage() {
  const accountsQuery = useAccounts();
  const recentQuery = useTransactions({ page: 0, size: RECENT_COUNT });
  const [showCreate, setShowCreate] = useState(false);

  const accountNames = useMemo(
    () => new Map((accountsQuery.data ?? []).map((account): [string, string] => [account.id, account.name])),
    [accountsQuery.data],
  );

  return (
    <>
      <section aria-labelledby="accounts-heading" className="section">
        <div className="section-header">
          <h2 id="accounts-heading">Your accounts</h2>
          {!showCreate && (
            <button type="button" className="button button-secondary" onClick={() => setShowCreate(true)}>
              New account
            </button>
          )}
        </div>

        {showCreate && <CreateAccountForm onDone={() => setShowCreate(false)} onCancel={() => setShowCreate(false)} />}

        {accountsQuery.isPending && (
          <div className="grid" aria-busy="true" aria-label="Loading accounts">
            {[0, 1, 2].map((key) => (
              <div className="card" key={key}>
                <Skeleton width="50%" />
                <Skeleton width="35%" height="0.8rem" />
                <Skeleton width="70%" height="2rem" />
              </div>
            ))}
          </div>
        )}

        {accountsQuery.isError && (
          <ErrorState message={getErrorMessage(accountsQuery.error)} onRetry={() => void accountsQuery.refetch()} />
        )}

        {accountsQuery.isSuccess && accountsQuery.data.length === 0 && !showCreate && (
          <EmptyState
            title="No accounts yet"
            description="Create your first account to start sending and receiving money."
            action={
              <button type="button" className="button" onClick={() => setShowCreate(true)}>
                Create an account
              </button>
            }
          />
        )}

        {accountsQuery.isSuccess && accountsQuery.data.length > 0 && (
          <div className="grid">
            {accountsQuery.data.map((account) => (
              <AccountCard key={account.id} account={account} />
            ))}
          </div>
        )}
      </section>

      <section aria-labelledby="recent-heading" className="section">
        <div className="section-header">
          <h2 id="recent-heading">Recent transactions</h2>
          <Link to="/transactions">View all</Link>
        </div>

        <div className="card">
          {recentQuery.isPending && (
            <div aria-busy="true" aria-label="Loading transactions" className="skeleton-rows">
              {[0, 1, 2, 3, 4].map((key) => (
                <Skeleton key={key} height="1.25rem" />
              ))}
            </div>
          )}

          {recentQuery.isError && (
            <ErrorState message={getErrorMessage(recentQuery.error)} onRetry={() => void recentQuery.refetch()} />
          )}

          {recentQuery.isSuccess && recentQuery.data.content.length === 0 && (
            <EmptyState
              title="No transactions yet"
              description="Your deposits and transfers will show up here."
              action={
                <Link to="/transfer" className="link-button">
                  Send money
                </Link>
              }
            />
          )}

          {recentQuery.isSuccess && recentQuery.data.content.length > 0 && (
            <TransactionTable
              caption="Your most recent transactions"
              transactions={recentQuery.data.content}
              accountNames={accountNames}
            />
          )}
        </div>
      </section>
    </>
  );
}
