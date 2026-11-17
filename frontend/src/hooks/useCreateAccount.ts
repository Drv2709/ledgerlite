import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createAccount } from '../api/accounts';
import { ACCOUNTS_KEY } from './useAccounts';
import { TRANSACTIONS_KEY } from './useTransactions';

export function useCreateAccount() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createAccount,
    // An initial deposit creates a transaction too, so refresh both.
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ACCOUNTS_KEY }),
        queryClient.invalidateQueries({ queryKey: TRANSACTIONS_KEY }),
      ]);
    },
  });
}
