import { useMemo, useState } from 'react';
import { getErrorMessage } from '../api/errors';
import { EmptyState } from '../components/EmptyState';
import { ErrorState } from '../components/ErrorState';
import { FormField } from '../components/FormField';
import { Pagination } from '../components/Pagination';
import { SelectField } from '../components/SelectField';
import { Skeleton } from '../components/Skeleton';
import { TransactionTable } from '../components/TransactionTable';
import { useAccounts } from '../hooks/useAccounts';
import { useTransactions } from '../hooks/useTransactions';
import { isTransactionType, type TransactionQuery } from '../types/api';
import { endOfDayIso, startOfDayIso } from '../utils/dates';

const TYPE_OPTIONS = [
  { value: '', label: 'All types' },
  { value: 'TRANSFER', label: 'Transfers' },
  { value: 'DEPOSIT', label: 'Deposits' },
  { value: 'REVERSAL', label: 'Reversals' },
];

export default function TransactionsPage() {
  const accountsQuery = useAccounts();

  const [accountId, setAccountId] = useState('');
  const [type, setType] = useState('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);

  const rangeError =
    fromDate !== '' && toDate !== '' && fromDate > toDate ? 'The "From" date must not be after the "To" date.' : undefined;

  const query: TransactionQuery = {
    page,
    size,
    accountId: accountId === '' ? undefined : accountId,
    type: isTransactionType(type) ? type : undefined,
    from: fromDate === '' ? undefined : startOfDayIso(fromDate),
    to: toDate === '' ? undefined : endOfDayIso(toDate),
  };
  const transactionsQuery = useTransactions(query, rangeError === undefined);

  const accountNames = useMemo(
    () => new Map((accountsQuery.data ?? []).map((account): [string, string] => [account.id, account.name])),
    [accountsQuery.data],
  );
  const accountOptions = [
    { value: '', label: 'All accounts' },
    ...(accountsQuery.data ?? []).map((account) => ({
      value: account.id,
      label: `${account.name} (${account.accountNumber})`,
    })),
  ];

  const hasFilters = accountId !== '' || type !== '' || fromDate !== '' || toDate !== '';

  // Any filter change goes back to the first page.
  function changeFilter(update: () => void) {
    update();
    setPage(0);
  }

  function clearFilters() {
    setAccountId('');
    setType('');
    setFromDate('');
    setToDate('');
    setPage(0);
  }

  function renderResults() {
    if (rangeError !== undefined) {
      return <EmptyState title="Check the dates" description={rangeError} />;
    }
    if (transactionsQuery.isPending) {
      return (
        <div aria-busy="true" aria-label="Loading transactions" className="skeleton-rows">
          {[0, 1, 2, 3, 4, 5].map((key) => (
            <Skeleton key={key} height="1.25rem" />
          ))}
        </div>
      );
    }
    if (transactionsQuery.isError) {
      return (
        <ErrorState
          message={getErrorMessage(transactionsQuery.error)}
          onRetry={() => void transactionsQuery.refetch()}
        />
      );
    }

    const result = transactionsQuery.data;
    if (result.totalElements === 0) {
      return hasFilters ? (
        <EmptyState
          title="No transactions match these filters"
          description="Try a different account, type or date range."
          action={
            <button type="button" className="button button-secondary" onClick={clearFilters}>
              Clear filters
            </button>
          }
        />
      ) : (
        <EmptyState title="No transactions yet" description="Your deposits and transfers will show up here." />
      );
    }

    return (
      <div className={transactionsQuery.isPlaceholderData ? 'results is-updating' : 'results'}>
        <TransactionTable
          caption="Transaction history"
          transactions={result.content}
          accountNames={accountNames}
        />
        <Pagination
          page={result.page}
          size={result.size}
          totalElements={result.totalElements}
          totalPages={result.totalPages}
          onPageChange={setPage}
          onSizeChange={(newSize) => {
            setSize(newSize);
            setPage(0);
          }}
          disabled={transactionsQuery.isPlaceholderData}
        />
      </div>
    );
  }

  return (
    <section aria-labelledby="history-heading" className="section">
      <h2 id="history-heading">Transaction history</h2>

      <div className="card filters">
        <SelectField
          id="filter-account"
          label="Account"
          value={accountId}
          onChange={(value) => changeFilter(() => setAccountId(value))}
          options={accountOptions}
        />
        <SelectField
          id="filter-type"
          label="Type"
          value={type}
          onChange={(value) => changeFilter(() => setType(value))}
          options={TYPE_OPTIONS}
        />
        <FormField
          id="filter-from"
          label="From"
          type="date"
          value={fromDate}
          onChange={(value) => changeFilter(() => setFromDate(value))}
        />
        <FormField
          id="filter-to"
          label="To"
          type="date"
          value={toDate}
          onChange={(value) => changeFilter(() => setToDate(value))}
          error={rangeError}
        />
        {hasFilters && (
          <button type="button" className="button button-secondary filters-clear" onClick={clearFilters}>
            Clear filters
          </button>
        )}
      </div>

      <div className="card">{renderResults()}</div>
    </section>
  );
}
