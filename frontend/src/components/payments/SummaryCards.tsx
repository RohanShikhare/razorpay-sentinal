import type { PaymentSummary } from "@/lib/payments";

function formatInr(value: string): string {
  const n = Number(value);
  if (Number.isNaN(n)) return value;
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(n);
}

function Metric({ label, value, accent }: { label: string; value: string; accent?: string }) {
  return (
    <div className="border border-base-border bg-base-panel px-4 py-3">
      <div className="text-xs text-ink-muted">{label}</div>
      <div className={`mt-1 font-mono text-lg ${accent ?? "text-ink"}`}>{value}</div>
    </div>
  );
}

export function SummaryCards({ summary }: { summary: PaymentSummary }) {
  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
      <Metric label="Total transactions" value={summary.totalTransactions.toLocaleString("en-IN")} />
      <Metric
        label="Success rate"
        value={`${summary.successRate.toFixed(2)}%`}
        accent="text-signal-nominal"
      />
      <Metric
        label="Failure rate"
        value={`${summary.failureRate.toFixed(2)}%`}
        accent="text-signal-critical"
      />
      <Metric label="Total value" value={formatInr(summary.totalTransactionValue)} />
      <Metric label="Failed value" value={formatInr(summary.failedTransactionValue)} accent="text-signal-critical" />
    </div>
  );
}
