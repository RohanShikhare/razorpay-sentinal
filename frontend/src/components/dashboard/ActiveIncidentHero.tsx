import type { Incident } from "@/lib/monitoring";
import type { FinancialImpact } from "@/lib/financial";

function formatInr(value: string): string {
  const n = Number(value);
  if (Number.isNaN(n)) return value;
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 }).format(n);
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="text-xs text-ink-muted">{label}</div>
      <div className="mt-0.5 font-mono text-sm text-ink">{value}</div>
    </div>
  );
}

function targetLabel(incident: Incident): string {
  if (incident.targetBank && incident.targetPaymentMethod) {
    return `${incident.targetBank} + ${incident.targetPaymentMethod}`;
  }
  if (incident.targetMerchantId) {
    return `Merchant ${incident.targetMerchantId}`;
  }
  return "System-wide";
}

function typeLabel(type: Incident["type"]): string {
  switch (type) {
    case "PAYMENT_DEGRADATION":
      return "Payment Degradation";
    case "MERCHANT_DEGRADATION":
      return "Merchant Degradation";
    case "SYSTEM_DEGRADATION":
      return "System Degradation";
    default:
      return type;
  }
}

export function ActiveIncidentHero({ incident, impact }: { incident: Incident; impact: FinancialImpact | null }) {
  return (
    <div className="border border-signal-critical/40 bg-signal-critical/5">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-signal-critical/30 px-5 py-3">
        <div className="flex items-center gap-2">
          <span className="text-xs font-semibold tracking-wide text-signal-critical">ACTIVE INCIDENT</span>
          <span className="bg-signal-critical/20 px-2 py-0.5 text-xs font-bold text-signal-critical">
            {incident.severity}
          </span>
        </div>
        <a href={`/incidents/${incident.id}`} className="text-xs text-signal-wire underline underline-offset-2">
          Open full incident details -&gt;
        </a>
      </div>

      <div className="px-5 py-4">
        <h3 className="text-lg font-semibold text-ink">
          {targetLabel(incident)} — {typeLabel(incident.type)}
        </h3>

        <div className="mt-4 grid grid-cols-2 gap-4 sm:grid-cols-4">
          <Field label="Incident ID" value={`#${incident.id}`} />
          <Field label="Detected at" value={new Date(incident.detectedAt).toLocaleString()} />
          <Field label="Status" value={incident.status} />
          <Field label="Confidence" value={`${incident.confidence.toFixed(0)}%`} />

          <Field label="Baseline failure rate" value={`${incident.baselineFailureRate.toFixed(2)}%`} />
          <Field label="Current failure rate" value={`${incident.currentFailureRate.toFixed(2)}%`} />
          <Field label="Increase" value={`+${incident.failureRateIncrease.toFixed(2)} pts`} />
          <Field label="Affected transactions" value={incident.affectedTransactions.toLocaleString("en-IN")} />

          <Field label="Affected transaction value" value={formatInr(incident.affectedTransactionValue)} />
          <Field label="Revenue at risk" value={formatInr(incident.revenueAtRisk)} />
          <Field
            label="Estimated recoverable"
            value={impact ? formatInr(impact.estimatedRecoverableRevenue) : "—"}
          />
          <Field label="Non-recoverable" value={impact ? formatInr(impact.nonRecoverableRevenue) : "—"} />
        </div>

        <div className="mt-4 flex flex-wrap items-center justify-between gap-2 border-t border-signal-critical/20 pt-3">
          <div>
            <div className="text-xs text-ink-muted">Root cause (AI Investigation)</div>
            <div className="mt-0.5 text-sm text-ink-faint">
              {incident.rootCause ?? "Not yet investigated with AI"}
            </div>
          </div>
          <div className="flex items-center gap-2">
            <a
              href={`/incidents/${incident.id}`}
              className="inline-flex items-center gap-1.5 border border-signal-wire/40 bg-signal-wire/10 px-3 py-1 text-xs font-semibold text-signal-wire hover:bg-signal-wire/20 transition-colors"
            >
              <svg className="w-3 h-3 text-signal-wire" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
              <span>{incident.rootCause ? "View Diagnosis" : "Investigate with AI"}</span>
            </a>
            <a
              href={`/incidents/${incident.id}`}
              className="inline-flex items-center gap-1.5 border border-blue-500/40 bg-blue-500/10 px-3 py-1 text-xs font-semibold text-blue-400 hover:bg-blue-500/20 transition-colors"
            >
              <svg className="w-3 h-3 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
              </svg>
              <span>Root-Cause Analysis</span>
            </a>
            <a
              href={`/incidents/${incident.id}`}
              className="inline-flex items-center gap-1.5 border border-emerald-500/40 bg-emerald-500/10 px-3 py-1 text-xs font-semibold text-emerald-400 hover:bg-emerald-500/20 transition-colors"
            >
              <svg className="w-3 h-3 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
              </svg>
              <span>Recovery Strategy</span>
            </a>
            <a
              href={`/incidents/${incident.id}`}
              className="inline-flex items-center gap-1.5 border border-indigo-500/40 bg-indigo-500/10 px-3 py-1 text-xs font-semibold text-indigo-400 hover:bg-indigo-500/20 transition-colors"
            >
              <svg className="w-3 h-3 text-indigo-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
              </svg>
              <span>Policy Gate</span>
            </a>
          </div>
        </div>
      </div>
    </div>
  );
}
