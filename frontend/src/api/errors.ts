import axios from 'axios';
import type { ApiErrorBody } from '../types/api';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

export function isApiErrorBody(value: unknown): value is ApiErrorBody {
  return (
    isRecord(value) &&
    typeof value.code === 'string' &&
    typeof value.detail === 'string' &&
    typeof value.status === 'number' &&
    (value.fieldErrors === undefined || Array.isArray(value.fieldErrors))
  );
}

/** The backend's structured error, if this error came from one. */
export function getApiError(error: unknown): ApiErrorBody | null {
  if (axios.isAxiosError(error)) {
    const data: unknown = error.response?.data;
    return isApiErrorBody(data) ? data : null;
  }
  return null;
}

/** A message that is safe to show to the user. */
export function getErrorMessage(error: unknown): string {
  const apiError = getApiError(error);
  if (apiError) {
    return apiError.detail;
  }
  if (axios.isAxiosError(error)) {
    if (!error.response) {
      return 'Cannot reach the server. Check your connection and try again.';
    }
    if (error.response.status >= 500) {
      return 'The server is having trouble right now. Please try again shortly.';
    }
  }
  return 'Something went wrong. Please try again.';
}

/** Per-field messages from a 400 VALIDATION_ERROR, keyed by field name. */
export function getFieldErrors(error: unknown): Record<string, string> {
  const result: Record<string, string> = {};
  for (const fieldError of getApiError(error)?.fieldErrors ?? []) {
    result[fieldError.field] = fieldError.message;
  }
  return result;
}
