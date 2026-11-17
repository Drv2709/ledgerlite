import type { Transaction, TransferBody } from '../types/api';
import { api } from './client';

export async function createTransfer(body: TransferBody, idempotencyKey: string): Promise<Transaction> {
  const { data } = await api.post<Transaction>('/api/transfers', body, {
    headers: { 'Idempotency-Key': idempotencyKey },
  });
  return data;
}
