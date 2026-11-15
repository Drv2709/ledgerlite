import { useCallback, useEffect, useMemo, type ReactNode } from 'react';
import { useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { fetchMe, loginRequest, logoutRequest, registerRequest } from '../api/auth';
import { setUnauthorizedHandler } from '../api/client';
import type { Credentials, User } from '../types/api';
import { AuthContext, ME_QUERY_KEY, type AuthContextValue } from './AuthContext';

/** Drops every cached query except the session itself, so one user's data never shows for the next. */
function removeUserData(queryClient: QueryClient): void {
  queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== 'auth' });
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ME_QUERY_KEY,
    queryFn: fetchMe,
    staleTime: Infinity,
  });
  const user: User | null = data ?? null;

  // An expired or invalid session anywhere in the app logs the user out locally.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      removeUserData(queryClient);
      queryClient.setQueryData<User | null>(ME_QUERY_KEY, null);
    });
    return () => setUnauthorizedHandler(null);
  }, [queryClient]);

  const login = useCallback(
    async (credentials: Credentials) => {
      const loggedIn = await loginRequest(credentials);
      removeUserData(queryClient);
      queryClient.setQueryData<User | null>(ME_QUERY_KEY, loggedIn);
    },
    [queryClient],
  );

  const register = useCallback(
    async (credentials: Credentials) => {
      const created = await registerRequest(credentials);
      removeUserData(queryClient);
      queryClient.setQueryData<User | null>(ME_QUERY_KEY, created);
    },
    [queryClient],
  );

  const logout = useCallback(async () => {
    await logoutRequest();
    removeUserData(queryClient);
    queryClient.setQueryData<User | null>(ME_QUERY_KEY, null);
  }, [queryClient]);

  const value = useMemo<AuthContextValue>(
    () => ({ user, isLoading, login, register, logout }),
    [user, isLoading, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
