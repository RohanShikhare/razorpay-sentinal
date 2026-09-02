import type { FinancialImpact } from "@/lib/financial";

function formatInr(value: string): string {
  const n = Number(value);
  if (Number.isNaN(n)) return value;
  if (Math.abs(n) >= 100_000) {
    return `₹${(n / 100_000).toFixed(2)}L`;
  }
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

export function FinancialImpactSummary({ impact }: { impact: FinancialImpact }) {
  const recoverablePct = Math.max(0, Math.min(100, impact.recoverablePercentage));

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Metric label="Revenue at risk" value={formatInr(impact.revenueAtRisk)} accent="text-signal-critical" />
        <Metric label="Estimated recoverable" value={formatInr(impact.estimatedRecoverableRevenue)} accent="text-signal-nominal" />
        <Metric label="Non-recoverable" value={formatInr(impact.nonRecoverableRevenue)} accent="text-signal-watch" />
        <Metric label="Affected transactions" value={impact.affectedTransactions.toLocaleString("en-IN")} />
      </div>

      <div className="border border-base-border bg-base-panel p-4">
        <div className="flex items-center justify-between text-xs text-ink-muted">
          <span>
            Revenue at risk: <span className="font-mono text-ink">{formatInr(impact.revenueAtRisk)}</span>
          </span>
          <span>
            Potentially recoverable:{" "}
            <span className="font-mono text-signal-nominal">{formatInr(impact.estimatedRecoverableRevenue)}</span>
          </span>
        </div>
        <div className="mt-2 h-3 w-full bg-base-raised">
          <div
            className="h-3 bg-signal-nominal transition-all"
            style={{ width: `${recoverablePct}%` }}
            aria-label={`${recoverablePct.toFixed(1)}% potentially recoverable`}
          />
        </div>
        <div className="mt-1 text-right font-mono text-xs text-ink-faint">
          {recoverablePct.toFixed(1)}% potentially recoverable
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        <Metric label="Affected transaction value" value={formatInr(impact.affectedTransactionValue)} />
        <Metric label="Average transaction value" value={formatInr(impact.averageTransactionValue)} />
        <Metric label="Largest affected transaction" value={formatInr(impact.largestAffectedTransaction)} />
      </div>
    </div>
  );
}
