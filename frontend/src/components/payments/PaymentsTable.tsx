import type { Payment, PaymentStatus } from "@/lib/payments";

const STATUS_COLOR: Record<PaymentStatus, string> = {
  SUCCESS: "text-signal-nominal",
  FAILED: "text-signal-critical",
  PENDING: "text-signal-watch",
};

function formatAmount(amount: string, currency: string): string {
  const n = Number(amount);
  if (Number.isNaN(n)) return `${amount} ${currency}`;
  return new Intl.NumberFormat("en-IN", { style: "currency", currency, maximumFractionDigits: 2 }).format(n);
}

export function PaymentsTable({ payments }: { payments: Payment[] }) {
  if (payments.length === 0) {
    return (
      <div className="border border-base-border bg-base-panel px-4 py-10 text-center text-sm text-ink-muted">
        No payments match these filters.
      </div>
    );
  }

  return (
    <div className="overflow-x-auto border border-base-border bg-base-panel">
      <table className="w-full min-w-[880px] text-left text-xs">
        <thead>
          <tr className="border-b border-base-border text-ink-muted">
            <th className="px-3 py-2 font-medium">ID</th>
            <th className="px-3 py-2 font-medium">Timestamp</th>
            <th className="px-3 py-2 font-medium">Merchant</th>
            <th className="px-3 py-2 font-medium">Customer</th>
            <th className="px-3 py-2 font-medium">Method</th>
            <th className="px-3 py-2 font-medium">Bank</th>
            <th className="px-3 py-2 font-medium">Status</th>
            <th className="px-3 py-2 font-medium">Error</th>
            <th className="px-3 py-2 text-right font-medium">Amount</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-base-border font-mono">
          {payments.map((payment) => (
            <tr key={payment.id}>
              <td className="px-3 py-2 text-ink-faint">{payment.id}</td>
              <td className="px-3 py-2 text-ink-muted">{new Date(payment.timestamp).toLocaleString()}</td>
              <td className="px-3 py-2 text-ink">{payment.merchantId}</td>
              <td className="px-3 py-2 text-ink-muted">{payment.customerId}</td>
              <td className="px-3 py-2 text-ink">{payment.paymentMethod}</td>
              <td className="px-3 py-2 text-ink">{payment.bank}</td>
              <td className={`px-3 py-2 ${STATUS_COLOR[payment.status]}`}>{payment.status}</td>
              <td className="px-3 py-2 text-ink-faint">
                {payment.errorCode === "NONE" ? "—" : payment.errorCode}
              </td>
              <td className="px-3 py-2 text-right text-ink">
                {formatAmount(payment.amount, payment.currency)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
