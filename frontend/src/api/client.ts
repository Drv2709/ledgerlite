import axios from 'axios';

/**
 * The auth token lives in an HttpOnly cookie, so JavaScript never sees it and cannot leak it.
 * The browser attaches it (withCredentials). For CSRF, axios copies the readable XSRF-TOKEN cookie
 * into the X-XSRF-TOKEN header on every same-origin request, which the backend requires on POST.
 */
export const api = axios.create({
  baseURL: '',
  withCredentials: true,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
});

type UnauthorizedHandler = () => void;
let unauthorizedHandler: UnauthorizedHandler | null = null;

/** Registered by AuthProvider: called when a request fails with 401 (expired or invalid session). */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler;
}

// A 401 from these is an expected answer ("not logged in" / "wrong password"), not an expired session.
const AUTH_ENDPOINTS = ['/api/auth/login', '/api/auth/register', '/api/auth/me'];

api.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const url = error.config?.url ?? '';
      if (!AUTH_ENDPOINTS.some((endpoint) => url.startsWith(endpoint))) {
        unauthorizedHandler?.();
      }
    }
    return Promise.reject(error);
  },
);
