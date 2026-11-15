export type Role = 'USER' | 'ADMIN';

export interface User {
  id: string;
  email: string;
  role: Role;
}

export interface Credentials {
  email: string;
  password: string;
}

export interface ApiFieldError {
  field: string;
  message: string;
}

/** Error body returned by every backend error (see docs/API_CONTRACT.md). */
export interface ApiErrorBody {
  type: string;
  title: string;
  status: number;
  code: string;
  detail: string;
  timestamp: string;
  fieldErrors?: ApiFieldError[];
}
