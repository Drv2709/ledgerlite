import axios from 'axios';
import type { Credentials, User } from '../types/api';
import { api } from './client';

/** The current user, or null when nobody is logged in (a 401 here is a normal answer). */
export async function fetchMe(): Promise<User | null> {
  try {
    const { data } = await api.get<User>('/api/auth/me');
    return data;
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      return null;
    }
    throw error;
  }
}

export async function loginRequest(credentials: Credentials): Promise<User> {
  const { data } = await api.post<User>('/api/auth/login', credentials);
  return data;
}

export async function registerRequest(credentials: Credentials): Promise<User> {
  const { data } = await api.post<User>('/api/auth/register', credentials);
  return data;
}

export async function logoutRequest(): Promise<void> {
  await api.post('/api/auth/logout');
}
