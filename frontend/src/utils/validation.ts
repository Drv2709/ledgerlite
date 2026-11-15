const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const PASSWORD_MIN_LENGTH = 10;
export const PASSWORD_MAX_LENGTH = 72;

export function validateEmail(email: string): string | undefined {
  const value = email.trim();
  if (value.length === 0) return 'Email is required.';
  if (value.length > 255) return 'Email is too long.';
  if (!EMAIL_PATTERN.test(value)) return 'Enter a valid email address.';
  return undefined;
}

/** Rules for CHOOSING a password (register). Login only checks that something was typed. */
export function validateNewPassword(password: string): string | undefined {
  if (password.length < PASSWORD_MIN_LENGTH) return `Use at least ${PASSWORD_MIN_LENGTH} characters.`;
  if (password.length > PASSWORD_MAX_LENGTH) return `Use at most ${PASSWORD_MAX_LENGTH} characters.`;
  return undefined;
}

export function validateRequired(value: string, message: string): string | undefined {
  return value.length === 0 ? message : undefined;
}
