import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createTransfer } from '../api/transfers';
import type { TransferBody } from '../types/api';
import { ACCOUNTS_KEY } from './useAccounts';
import { TRANSACTIONS_KEY } from './useTransactions';

export interface TransferVariables {
  body: TransferBody;
  idempotencyKey: string;
}

export function useTransfer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ body, idempotencyKey }: TransferVariables) => createTransfer(body, idempotencyKey),
    // Returning the promise keeps the mutation "pending" until balances are fresh again.
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ACCOUNTS_KEY }),
        queryClient.invalidateQueries({ queryKey: TRANSACTIONS_KEY }),
      ]);
    },
  });
}
