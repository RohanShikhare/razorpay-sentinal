import type { MonitoringSummary } from "@/lib/monitoring";

function formatInr(value: string): string {
  const n = Number(value);
  if (Number.isNaN(n)) return value;
  if (Math.abs(n) >= 100_000) {
    return `₹${(n / 100_000).toFixed(2)}L`;
  }
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 }).format(n);
}

function Card({
  label,
  children,
  accent,
}: {
  label: string;
  children: React.ReactNode;
  accent?: string;
}) {
  return (
    <div className="border border-base-border bg-base-panel px-4 py-3.5">
      <div className="text-xs text-ink-muted">{label}</div>
      <div className={`mt-1.5 font-mono text-2xl leading-none ${accent ?? "text-ink"}`}>{children}</div>
    </div>
  );
}

export function KpiCards({ summary }: { summary: MonitoringSummary }) {
  const baselineSuccessRate = 100 - summary.baselineFailureRate;
  const successDelta = summary.successRate - baselineSuccessRate;
  const degraded = successDelta < -0.5;

  return (
    <div className="grid grid-cols-2 gap-3 lg:grid-cols-5">
      <Card label="Payment success rate" accent={degraded ? "text-signal-critical" : "text-signal-nominal"}>
        {summary.successRate.toFixed(1)}%
        <div className="mt-1 font-sans text-[11px] font-normal text-ink-faint">
          Baseline {baselineSuccessRate.toFixed(1)}% ·{" "}
          <span className={degraded ? "text-signal-critical" : "text-signal-nominal"}>
            {successDelta >= 0 ? "up" : "down"} {Math.abs(successDelta).toFixed(1)} pp
          </span>
        </div>
      </Card>

      <Card label="Revenue at risk" accent="text-signal-critical">
        {formatInr(summary.revenueAtRisk)}
      </Card>

      <Card label="Estimated recoverable" accent="text-signal-nominal">
        {formatInr(summary.estimatedRecoverableRevenue)}
      </Card>

      <Card label="Active incidents" accent={summary.activeIncidents > 0 ? "text-signal-critical" : "text-ink"}>
        {summary.activeIncidents.toLocaleString("en-IN")}
      </Card>

      <Card label="Affected payments">{summary.affectedTransactions.toLocaleString("en-IN")}</Card>
    </div>
  );
}
