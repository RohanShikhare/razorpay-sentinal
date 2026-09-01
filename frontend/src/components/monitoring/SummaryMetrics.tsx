import type { MonitoringSummary } from "@/lib/monitoring";

function formatInr(value: string): string {
  const n = Number(value);
  if (Number.isNaN(n)) return value;
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 }).format(n);
}

function Metric({ label, value, accent }: { label: string; value: string; accent?: string }) {
  return (
    <div className="border border-base-border bg-base-panel px-4 py-3">
      <div className="text-xs text-ink-muted">{label}</div>
      <div className={`mt-1 font-mono text-lg ${accent ?? "text-ink"}`}>{value}</div>
    </div>
  );
}

export function SummaryMetrics({ summary }: { summary: MonitoringSummary }) {
  const changeAccent = summary.failureRateChange > 5 ? "text-signal-critical" : "text-ink";

  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
      <Metric label="Success rate" value={`${summary.successRate.toFixed(2)}%`} accent="text-signal-nominal" />
      <Metric label="Failure rate" value={`${summary.failureRate.toFixed(2)}%`} accent="text-signal-critical" />
      <Metric label="Baseline failure rate" value={`${summary.baselineFailureRate.toFixed(2)}%`} />
      <Metric
        label="Failure rate change"
        value={`${summary.failureRateChange >= 0 ? "+" : ""}${summary.failureRateChange.toFixed(2)} pts`}
        accent={changeAccent}
      />
      <Metric label="Revenue at risk" value={formatInr(summary.revenueAtRisk)} accent="text-signal-critical" />
      <Metric label="Active incidents" value={summary.activeIncidents.toLocaleString("en-IN")} />
      <Metric label="Affected transactions" value={summary.affectedTransactions.toLocaleString("en-IN")} />
    </div>
  );
}
