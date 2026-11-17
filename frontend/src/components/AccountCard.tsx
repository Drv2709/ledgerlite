import { Link } from 'react-router-dom';
import type { Account } from '../types/api';
import { formatMoney } from '../utils/money';

export function AccountCard({ account }: { account: Account }) {
  return (
    <article className="card account-card">
      <h3>{account.name}</h3>
      <p className="muted small">
        {account.accountNumber} · {account.currency}
      </p>
      <p className="balance">{formatMoney(account.balance, account.currency)}</p>
      <Link to={`/transfer?from=${encodeURIComponent(account.id)}`} className="link-button">
        Send money
      </Link>
    </article>
  );
}
