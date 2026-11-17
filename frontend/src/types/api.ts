export type Role = 'USER' | 'ADMIN';

export interface User {
  id: string;
  email: string;
  role: Role;
}

export interface Credentials {
  email: string;
  password: string;
}

export interface ApiFieldError {
  field: string;
  message: string;
}

/** Error body returned by every backend error (see docs/API_CONTRACT.md). */
export interface ApiErrorBody {
  type: string;
  title: string;
  status: number;
  code: string;
  detail: string;
  timestamp: string;
  fieldErrors?: ApiFieldError[];
}

// ---------------------------------------------------------------- money domain
// Every amount and balance is a decimal STRING (e.g. "100.0000"), never a JavaScript number.

export const CURRENCIES = ['USD', 'CAD', 'EUR', 'GBP', 'INR'] as const;

export type TransactionType = 'TRANSFER' | 'REVERSAL' | 'DEPOSIT';
export type TransactionStatus = 'COMPLETED' | 'REVERSED';
export type Direction = 'IN' | 'OUT';

export const TRANSACTION_TYPES: readonly TransactionType[] = ['TRANSFER', 'REVERSAL', 'DEPOSIT'];

export function isTransactionType(value: string): value is TransactionType {
  return TRANSACTION_TYPES.some((type) => type === value);
}

export interface Account {
  id: string;
  accountNumber: string;
  name: string;
  currency: string;
  balance: string;
  createdAt: string;
}

export interface LedgerEntry {
  accountId: string;
  entryType: 'DEBIT' | 'CREDIT';
  amount: string;
}

export interface Transaction {
  id: string;
  type: TransactionType;
  status: TransactionStatus;
  amount: string;
  currency: string;
  sourceAccountId: string;
  destinationAccountId: string;
  direction: Direction;
  reversesTransactionId?: string;
  description?: string;
  createdAt: string;
  entries?: LedgerEntry[];
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface TransactionQuery {
  page: number;
  size: number;
  accountId?: string;
  type?: TransactionType;
  from?: string;
  to?: string;
}

export interface CreateAccountBody {
  name: string;
  currency: string;
  initialDeposit?: string;
}

export interface TransferBody {
  sourceAccountId: string;
  destinationAccountNumber: string;
  amount: string;
  description?: string;
}
