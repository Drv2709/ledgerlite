import { useState, type FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { getErrorMessage, getFieldErrors } from '../api/errors';
import { useAuth } from '../auth/useAuth';
import { AuthCard } from '../components/AuthCard';
import { FormField } from '../components/FormField';
import { validateEmail, validateRequired } from '../utils/validation';

type FieldErrors = Partial<Record<'email' | 'password', string>>;

export default function LoginPage() {
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [clientErrors, setClientErrors] = useState<FieldErrors>({});

  // On success the session query updates and PublicOnlyRoute redirects, so nothing else to do here.
  const mutation = useMutation({ mutationFn: login });

  const serverFieldErrors = getFieldErrors(mutation.error);
  const hasFieldErrors = Object.keys(serverFieldErrors).length > 0;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const errors: FieldErrors = {
      email: validateEmail(email),
      password: validateRequired(password, 'Password is required.'),
    };
    setClientErrors(errors);
    if (errors.email || errors.password) {
      return;
    }
    mutation.mutate({ email: email.trim(), password });
  }

  return (
    <AuthCard title="Sign in" subtitle="Welcome back. Sign in to see your accounts.">
      <form onSubmit={handleSubmit} noValidate>
        <FormField
          id="email"
          label="Email"
          type="email"
          value={email}
          onChange={setEmail}
          autoComplete="email"
          error={clientErrors.email ?? serverFieldErrors.email}
          disabled={mutation.isPending}
        />
        <FormField
          id="password"
          label="Password"
          type="password"
          value={password}
          onChange={setPassword}
          autoComplete="current-password"
          error={clientErrors.password ?? serverFieldErrors.password}
          disabled={mutation.isPending}
        />

        {mutation.isError && !hasFieldErrors && (
          <div className="form-error" role="alert">
            {getErrorMessage(mutation.error)}
          </div>
        )}

        <button type="submit" className="button" disabled={mutation.isPending}>
          {mutation.isPending ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
      <p className="muted center">
        New to LedgerLite? <Link to="/register">Create an account</Link>
      </p>
    </AuthCard>
  );
}
