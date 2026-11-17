const DATE_TIME_FORMAT = new Intl.DateTimeFormat(undefined, {
  year: 'numeric',
  month: 'short',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

export function formatDateTime(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? iso : DATE_TIME_FORMAT.format(date);
}

/** "2026-11-15" (a local calendar day) -> the instant that day starts, as an ISO string. */
export function startOfDayIso(dateInput: string): string | undefined {
  const date = new Date(`${dateInput}T00:00:00`);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}

/** "2026-11-15" -> the last millisecond of that local day, so the filter is inclusive. */
export function endOfDayIso(dateInput: string): string | undefined {
  const date = new Date(`${dateInput}T23:59:59.999`);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}
