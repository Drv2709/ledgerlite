import { useRef, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { getErrorMessage, getFieldErrors } from '../api/errors';
import { EmptyState } from '../components/EmptyState';
import { ErrorState } from '../components/ErrorState';
import { FormField } from '../components/FormField';
import { SelectField } from '../components/SelectField';
import { Skeleton } from '../components/Skeleton';
import { useAccounts } from '../hooks/useAccounts';
import { useTransfer } from '../hooks/useTransfer';
import type { Account, Transaction } from '../types/api';
import { formatMoney, isPositiveAmount, isValidAmountInput } from '../utils/money';

type FieldErrors = Partial<Record<'destination' | 'amount', string>>;

interface CompletedTransfer {
  transaction: Transaction;
  destination: string;
}

export default function TransferPage() {
  const [searchParams] = useSearchParams();
  const accountsQuery = useAccounts();
  const transfer = useTransfer();

  const [sourceId, setSourceId] = useState(searchParams.get('from') ?? '');
  const [destination, setDestination] = useState('');
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [clientErrors, setClientErrors] = useState<FieldErrors>({});
  const [completed, setCompleted] = useState<CompletedTransfer | null>(null);

  // One Idempotency-Key per "intent". Retrying the SAME request (for example after a network error whose
  // outcome is unknown) reuses the key, so the server can never move the money twice. Editing any field
  // starts a new intent, and so does a successful transfer.
  const idempotencyKey = useRef(crypto.randomUUID());
  function newIntent() {
    idempotencyKey.current = crypto.randomUUID();
    setCompleted(null);
    transfer.reset();
  }

  const accounts: Account[] = accountsQuery.data ?? [];
  const selected: Account | undefined = accounts.find((account) => account.id === sourceId) ?? accounts[0];
  const serverErrors = getFieldErrors(transfer.error);
  const hasFieldErrors = Object.keys(serverErrors).length > 0;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (selected === undefined) {
      return;
    }

    const destinationNumber = destination.trim().toUpperCase();
    const trimmedAmount = amount.trim();

    const errors: FieldErrors = {};
    if (!/^LL-[A-Z0-9-]+$/.test(destinationNumber)) {
      errors.destination = 'Enter an account number like LL-0000000001.';
    } else if (destinationNumber === selected.accountNumber) {
      errors.destination = 'Choose a different account than the one you are sending from.';
    }
    if (!isValidAmountInput(trimmedAmount)) {
      errors.amount = 'Enter an amount like 25 or 25.50 (up to 4 decimals).';
    } else if (!isPositiveAmount(trimmedAmount)) {
      errors.amount = 'The amount must be greater than zero.';
    }
    setClientErrors(errors);
    if (errors.destination || errors.amount) {
      return;
    }

    const note = description.trim();
    transfer.mutate(
      {
        body: {
          sourceAccountId: selected.id,
          destinationAccountNumber: destinationNumber,
          amount: trimmedAmount,
          description: note === '' ? undefined : note,
        },
        idempotencyKey: idempotencyKey.current,
      },
      {
        onSuccess: (transaction) => {
          setCompleted({ transaction, destination: destinationNumber });
          setDestination('');
          setAmount('');
          setDescription('');
          setClientErrors({});
          idempotencyKey.current = crypto.randomUUID();
        },
      },
    );
  }

  if (accountsQuery.isPending) {
    return (
      <section className="card" aria-busy="true" aria-label="Loading accounts">
        <div className="skeleton-rows">
          <Skeleton width="40%" height="1.5rem" />
          <Skeleton height="2.5rem" />
          <Skeleton height="2.5rem" />
          <Skeleton height="2.5rem" />
        </div>
      </section>
    );
  }

  if (accountsQuery.isError) {
    return (
      <section className="card">
        <ErrorState message={getErrorMessage(accountsQuery.error)} onRetry={() => void accountsQuery.refetch()} />
      </section>
    );
  }

  if (accounts.length === 0 || selected === undefined) {
    return (
      <section className="card">
        <EmptyState
          title="You need an account first"
          description="Create an account on the dashboard before sending money."
          action={
            <Link to="/" className="link-button">
              Go to the dashboard
            </Link>
          }
        />
      </section>
    );
  }

  const accountOptions = accounts.map((account) => ({
    value: account.id,
    label: `${account.name} (${account.accountNumber}) - ${formatMoney(account.balance, account.currency)}`,
  }));

  return (
    <section className="card narrow" aria-labelledby="transfer-heading">
      <h2 id="transfer-heading">Send money</h2>

      {completed && (
        <div className="form-success" role="status">
          Sent <strong>{formatMoney(completed.transaction.amount, completed.transaction.currency)}</strong> to{' '}
          {completed.destination}. <Link to="/transactions">View history</Link>
        </div>
      )}

      <form onSubmit={handleSubmit} noValidate>
        <SelectField
          id="source"
          label="From"
          value={selected.id}
          onChange={(value) => {
            setSourceId(value);
            newIntent();
          }}
          options={accountOptions}
          hint={`Available: ${formatMoney(selected.balance, selected.currency)}`}
          error={serverErrors.sourceAccountId}
          disabled={transfer.isPending}
        />
        <FormField
          id="destination"
          label="To (account number)"
          type="text"
          value={destination}
          onChange={(value) => {
            setDestination(value);
            newIntent();
          }}
          autoComplete="off"
          placeholder="LL-0000000001"
          error={clientErrors.destination ?? serverErrors.destinationAccountNumber}
          disabled={transfer.isPending}
        />
        <FormField
          id="amount"
          label={`Amount (${selected.currency})`}
          type="text"
          inputMode="decimal"
          value={amount}
          onChange={(value) => {
            setAmount(value);
            newIntent();
          }}
          autoComplete="off"
          placeholder="0.00"
          error={clientErrors.amount ?? serverErrors.amount}
          disabled={transfer.isPending}
        />
        <FormField
          id="description"
          label="Note (optional)"
          type="text"
          value={description}
          onChange={(value) => {
            setDescription(value);
            newIntent();
          }}
          autoComplete="off"
          maxLength={255}
          error={serverErrors.description}
          disabled={transfer.isPending}
        />

        {transfer.isError && !hasFieldErrors && (
          <div className="form-error" role="alert">
            {getErrorMessage(transfer.error)}
          </div>
        )}

        <button type="submit" className="button" disabled={transfer.isPending}>
          {transfer.isPending ? 'Sending…' : 'Send money'}
        </button>
      </form>
    </section>
  );
}
