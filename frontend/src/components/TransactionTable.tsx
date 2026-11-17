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

export function TransactionTable({ caption, transactions, accountNames }: TransactionTableProps) {
  return (
    <div className="table-wrap">
      <table className="table">
        <caption className="visually-hidden">{caption}</caption>
        <thead>
          <tr>
            <th scope="col">Date</th>
            <th scope="col">Details</th>
            <th scope="col">Note</th>
            <th scope="col">Status</th>
            <th scope="col" className="num">
              Amount
            </th>
          </tr>
        </thead>
        <tbody>
          {transactions.map((transaction) => {
            const incoming = transaction.direction === 'IN';
            return (
              <tr key={transaction.id}>
                <td className="nowrap">{formatDateTime(transaction.createdAt)}</td>
                <td>
                  <span className="badge">{TYPE_LABELS[transaction.type]}</span> {describe(transaction, accountNames)}
                </td>
                <td className="muted">{transaction.description ?? ''}</td>
                <td>
                  {transaction.status === 'REVERSED' ? (
                    <span className="badge badge-warn">Reversed</span>
                  ) : (
                    <span className="muted">Completed</span>
                  )}
                </td>
                <td className={incoming ? 'num amount amount-in' : 'num amount amount-out'}>
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
