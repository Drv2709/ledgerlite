import type { Page, Transaction, TransactionQuery } from '../types/api';
import { api } from './client';

export async function fetchTransactions(query: TransactionQuery): Promise<Page<Transaction>> {
  // axios leaves out params whose value is undefined
  const { data } = await api.get<Page<Transaction>>('/api/transactions', {
    params: {
      page: query.page,
      size: query.size,
      accountId: query.accountId,
      type: query.type,
      from: query.from,
      to: query.to,
    },
  });
  return data;
}
