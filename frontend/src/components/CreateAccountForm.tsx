import { useState, type FormEvent } from 'react';
import { getErrorMessage, getFieldErrors } from '../api/errors';
import { useCreateAccount } from '../hooks/useCreateAccount';
import { CURRENCIES } from '../types/api';
import { MAX_INITIAL_DEPOSIT_UNITS, isValidAmountInput, toScaledUnits } from '../utils/money';
import { FormField } from './FormField';
import { SelectField } from './SelectField';

type FieldErrors = Partial<Record<'name' | 'deposit', string>>;

interface CreateAccountFormProps {
  onDone: () => void;
  onCancel: () => void;
}

const CURRENCY_OPTIONS = CURRENCIES.map((code) => ({ value: code, label: code }));

export function CreateAccountForm({ onDone, onCancel }: CreateAccountFormProps) {
  const mutation = useCreateAccount();
  const [name, setName] = useState('');
  const [currency, setCurrency] = useState<string>(CURRENCIES[0]);
  const [deposit, setDeposit] = useState('');
  const [clientErrors, setClientErrors] = useState<FieldErrors>({});

  const serverErrors = getFieldErrors(mutation.error);
  const hasFieldErrors = Object.keys(serverErrors).length > 0;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmedName = name.trim();
    const trimmedDeposit = deposit.trim();

    const errors: FieldErrors = {};
    if (trimmedName.length === 0) {
      errors.name = 'Give the account a name.';
    } else if (trimmedName.length > 100) {
      errors.name = 'Use at most 100 characters.';
    }
    if (trimmedDeposit !== '') {
      if (!isValidAmountInput(trimmedDeposit)) {
        errors.deposit = 'Enter an amount like 100 or 100.50 (up to 4 decimals).';
      } else if (toScaledUnits(trimmedDeposit) > MAX_INITIAL_DEPOSIT_UNITS) {
        errors.deposit = 'The maximum starting balance is 1,000,000.';
      }
    }
    setClientErrors(errors);
    if (errors.name || errors.deposit) {
      return;
    }

    mutation.mutate(
      { name: trimmedName, currency, initialDeposit: trimmedDeposit === '' ? undefined : trimmedDeposit },
      { onSuccess: onDone },
    );
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="card inline-form">
      <h3>New account</h3>
      <FormField
        id="account-name"
        label="Account name"
        type="text"
        value={name}
        onChange={setName}
        autoComplete="off"
        placeholder="e.g. Everyday"
        maxLength={100}
        error={clientErrors.name ?? serverErrors.name}
        disabled={mutation.isPending}
      />
      <SelectField
        id="account-currency"
        label="Currency"
        value={currency}
        onChange={setCurrency}
        options={CURRENCY_OPTIONS}
        error={serverErrors.currency}
        disabled={mutation.isPending}
      />
      <FormField
        id="account-deposit"
        label="Starting balance (optional)"
        type="text"
        inputMode="decimal"
        value={deposit}
        onChange={setDeposit}
        autoComplete="off"
        placeholder="0.00"
        hint="Demo funding from the LedgerLite system account."
        error={clientErrors.deposit ?? serverErrors.initialDeposit}
        disabled={mutation.isPending}
      />

      {mutation.isError && !hasFieldErrors && (
        <div className="form-error" role="alert">
          {getErrorMessage(mutation.error)}
        </div>
      )}

      <div className="form-actions">
        <button type="submit" className="button" disabled={mutation.isPending}>
          {mutation.isPending ? 'Creating…' : 'Create account'}
        </button>
        <button type="button" className="button button-secondary" onClick={onCancel} disabled={mutation.isPending}>
          Cancel
        </button>
      </div>
    </form>
  );
}
