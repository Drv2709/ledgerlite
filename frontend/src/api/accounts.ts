import type { Account, CreateAccountBody } from '../types/api';
import { api } from './client';

export async function fetchAccounts(): Promise<Account[]> {
  const { data } = await api.get<Account[]>('/api/accounts');
  return data;
}

export async function createAccount(body: CreateAccountBody): Promise<Account> {
  const { data } = await api.post<Account>('/api/accounts', body);
  return data;
}
