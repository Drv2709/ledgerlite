import { createContext } from 'react';
import type { Credentials, User } from '../types/api';

/** TanStack Query key holding the current user (User, or null when logged out). */
export const ME_QUERY_KEY = ['auth', 'me'] as const;

export interface AuthContextValue {
  user: User | null;
  isLoading: boolean;
  login: (credentials: Credentials) => Promise<void>;
  register: (credentials: Credentials) => Promise<void>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
