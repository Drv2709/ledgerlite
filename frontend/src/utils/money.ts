// Money is handled as decimal strings. No parseFloat, no Number(): nothing here can introduce
// binary floating-point error. Comparisons use BigInt scaled to 4 decimal places.

export const AMOUNT_SCALE = 4;

const CURRENCY_SYMBOLS: Record<string, string | undefined> = {
  USD: '$',
  CAD: 'CA$',
  EUR: '€',
  GBP: '£',
  INR: '₹',
};

const DECIMAL_PATTERN = /^(-?)(\d+)(?:\.(\d+))?$/;
const AMOUNT_INPUT_PATTERN = /^\d{1,15}(\.\d{1,4})?$/;

/** "1234.5000" + "USD" -> "$1,234.50". Extra decimals are kept only when they are not zero. */
export function formatMoney(amount: string, currency: string): string {
  const match = DECIMAL_PATTERN.exec(amount);
  if (match === null) {
    return `${amount} ${currency}`;
  }
  const negative = match[1] === '-';
  const whole = (match[2] ?? '0').replace(/^0+(?=\d)/, '');
  const trimmed = (match[3] ?? '').replace(/0+$/, '');
  const fraction = trimmed.length < 2 ? trimmed.padEnd(2, '0') : trimmed;
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',');

  const symbol = CURRENCY_SYMBOLS[currency];
  const prefix = symbol ?? '';
  const suffix = symbol === undefined ? ` ${currency}` : '';
  return `${negative ? '-' : ''}${prefix}${grouped}.${fraction}${suffix}`;
}

/** Digits with an optional fraction of at most 4 places, e.g. "25", "25.5", "0.0001". */
export function isValidAmountInput(value: string): boolean {
  return AMOUNT_INPUT_PATTERN.test(value);
}

/** "12.5" -> 125000n (4 implied decimals). Only call with a value that passed isValidAmountInput. */
export function toScaledUnits(value: string): bigint {
  const [whole = '0', fraction = ''] = value.split('.');
  return BigInt(whole + fraction.padEnd(AMOUNT_SCALE, '0'));
}

export function isPositiveAmount(value: string): boolean {
  return isValidAmountInput(value) && toScaledUnits(value) > 0n;
}

/** Matches the backend limit of 1,000,000 for an initial deposit. */
export const MAX_INITIAL_DEPOSIT_UNITS = 1000000n * 10000n;
