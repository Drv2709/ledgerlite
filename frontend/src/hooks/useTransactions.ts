import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { fetchTransactions } from '../api/transactions';
import type { TransactionQuery } from '../types/api';

export const TRANSACTIONS_KEY = ['transactions'] as const;

/** keepPreviousData: the old page stays on screen while the next one loads (no flicker). */
export function useTransactions(query: TransactionQuery, enabled = true) {
  return useQuery({
    queryKey: [...TRANSACTIONS_KEY, query],
    queryFn: () => fetchTransactions(query),
    placeholderData: keepPreviousData,
    enabled,
  });
}
