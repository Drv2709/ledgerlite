import { useState, type FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { getErrorMessage, getFieldErrors } from '../api/errors';
import { useAuth } from '../auth/useAuth';
import { AuthCard } from '../components/AuthCard';
import { FormField } from '../components/FormField';
import { PASSWORD_MIN_LENGTH, validateEmail, validateNewPassword } from '../utils/validation';

type FieldErrors = Partial<Record<'email' | 'password' | 'confirmPassword', string>>;

export default function RegisterPage() {
  const { register } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [clientErrors, setClientErrors] = useState<FieldErrors>({});

  const mutation = useMutation({ mutationFn: register });

  const serverFieldErrors = getFieldErrors(mutation.error);
  const hasFieldErrors = Object.keys(serverFieldErrors).length > 0;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const errors: FieldErrors = {
      email: validateEmail(email),
      password: validateNewPassword(password),
      confirmPassword: confirmPassword === password ? undefined : 'Passwords do not match.',
    };
    setClientErrors(errors);
    if (errors.email || errors.password || errors.confirmPassword) {
      return;
    }
    mutation.mutate({ email: email.trim(), password });
  }

  return (
    <AuthCard title="Create your account" subtitle="It takes a few seconds.">
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
          autoComplete="new-password"
          hint={`At least ${PASSWORD_MIN_LENGTH} characters.`}
          error={clientErrors.password ?? serverFieldErrors.password}
          disabled={mutation.isPending}
        />
        <FormField
          id="confirmPassword"
          label="Confirm password"
          type="password"
          value={confirmPassword}
          onChange={setConfirmPassword}
          autoComplete="new-password"
          error={clientErrors.confirmPassword}
          disabled={mutation.isPending}
        />

        {mutation.isError && !hasFieldErrors && (
          <div className="form-error" role="alert">
            {getErrorMessage(mutation.error)}
          </div>
        )}

        <button type="submit" className="button" disabled={mutation.isPending}>
          {mutation.isPending ? 'Creating account…' : 'Create account'}
        </button>
      </form>
      <p className="muted center">
        Already have an account? <Link to="/login">Sign in</Link>
      </p>
    </AuthCard>
  );
}
