import type { Transaction, TransactionType } from '../types/api';
import { formatDateTime } from '../utils/dates';
import { formatMoney } from '../utils/money';

interface TransactionTableProps {
  caption: string;
  transactions: readonly Transaction[];
  /** account id -> account name, for the caller's own accounts only */
  accountNames: ReadonlyMap<string, string>;
}

const TYPE_LABELS: Record<TransactionType, string> = {
  TRANSFER: 'Transfer',
  REVERSAL: 'Reversal',
  DEPOSIT: 'Deposit',
};

function describe(transaction: Transaction, names: ReadonlyMap<string, string>): string {
  const from = names.get(transaction.sourceAccountId) ?? 'External account';
  const to = names.get(transaction.destinationAccountId) ?? 'External account';
  return transaction.type === 'DEPOSIT' ? `Deposit to ${to}` : `${from} → ${to}`;
}

/**
 * A normal table on tablets and laptops. On phones the CSS turns each row into a card (see styles/app.css).
 * The explicit roles keep it a table for screen readers even when it is displayed as cards.
 */
export function TransactionTable({ caption, transactions, accountNames }: TransactionTableProps) {
  return (
    <div className="table-wrap">
      <table className="table" role="table">
        <caption className="visually-hidden">{caption}</caption>
        <thead role="rowgroup">
          <tr role="row">
            <th scope="col" role="columnheader">
              Date
            </th>
            <th scope="col" role="columnheader">
              Details
            </th>
            <th scope="col" role="columnheader">
              Note
            </th>
            <th scope="col" role="columnheader">
              Status
            </th>
            <th scope="col" role="columnheader" className="num">
              Amount
            </th>
          </tr>
        </thead>
        <tbody role="rowgroup">
          {transactions.map((transaction) => {
            const incoming = transaction.direction === 'IN';
            return (
              <tr key={transaction.id} role="row">
                <td role="cell" className="cell-date nowrap">
                  {formatDateTime(transaction.createdAt)}
                </td>
                <td role="cell" className="cell-details">
                  <span className="badge">{TYPE_LABELS[transaction.type]}</span> {describe(transaction, accountNames)}
                </td>
                <td role="cell" className="cell-note muted">
                  {transaction.description ?? ''}
                </td>
                <td role="cell" className="cell-status">
                  {transaction.status === 'REVERSED' ? (
                    <span className="badge badge-warn">Reversed</span>
                  ) : (
                    <span className="muted">Completed</span>
                  )}
                </td>
                <td role="cell" className={`cell-amount num amount ${incoming ? 'amount-in' : 'amount-out'}`}>
                  {incoming ? '+' : '-'}
                  {formatMoney(transaction.amount, transaction.currency)}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
