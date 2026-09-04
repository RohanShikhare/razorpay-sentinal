"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { FailureRateComparisonChart } from "@/components/monitoring/FailureRateComparisonChart";
import { SegmentChart } from "@/components/monitoring/SegmentChart";
import { FinancialImpactSummary } from "@/components/financial/FinancialImpactSummary";
import { FinancialBreakdownChart } from "@/components/financial/FinancialBreakdownChart";
import { AiInvestigationPanel } from "@/components/ai/AiInvestigationPanel";
import { RootCauseInvestigationPanel } from "@/components/investigation/RootCauseInvestigationPanel";
import { RecoveryStrategyPanel } from "@/components/recovery/RecoveryStrategyPanel";
import { PolicyGatePanel } from "@/components/policy/PolicyGatePanel";
import { fetchIncidentDetail, type IncidentDetail } from "@/lib/monitoring";
import { fetchFinancialBreakdown, fetchFinancialImpact, type FinancialBreakdown, type FinancialImpact } from "@/lib/financial";

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

export default function IncidentDetailPage() {
  const params = useParams<{ id: string }>();
  const [detail, setDetail] = useState<IncidentDetail | null>(null);
  const [impact, setImpact] = useState<FinancialImpact | null>(null);
  const [breakdown, setBreakdown] = useState<FinancialBreakdown | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const id = Number(params.id);
      const [detailData, impactData, breakdownData] = await Promise.all([
        fetchIncidentDetail(id),
        fetchFinancialImpact(id),
        fetchFinancialBreakdown(id),
      ]);
      setDetail(detailData);
      setImpact(impactData);
      setBreakdown(breakdownData);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load incident");
    } finally {
      setLoading(false);
    }
  }, [params.id]);

  useEffect(() => {
    load();
  }, [load]);

  if (loading) {
    return (
      <main className="mx-auto max-w-6xl px-6 py-10">
        <div className="border border-base-border bg-base-panel px-4 py-10 text-center text-sm text-ink-muted">
          loading incident...
        </div>
      </main>
    );
  }

  if (error || !detail) {
    return (
      <main className="mx-auto max-w-6xl px-6 py-10">
        <div className="border border-signal-critical/40 bg-signal-critical/10 px-4 py-3 text-sm text-signal-critical">
          {error ?? "Incident not found."}
        </div>
      </main>
    );
  }

  const { incident, topAffectedSegments } = detail;
  const byBank = topAffectedSegments.filter((s) => s.dimension === "BANK");
  const byMethod = topAffectedSegments.filter((s) => s.dimension === "PAYMENT_METHOD");
  const byBankMethod = topAffectedSegments.filter((s) => s.dimension === "BANK_PAYMENT_METHOD");

  return (
    <main className="mx-auto max-w-6xl px-6 py-10">
      <p className="font-mono text-xs text-signal-wire">Module 3-4, 6-8 · Monitoring, Financial Impact, AI Diagnosis, Investigation &amp; Recovery Strategy</p>
      <div className="mt-2 flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-semibold tracking-tight text-ink">Incident #{incident.id}</h1>
        <span className="bg-signal-critical/15 px-2 py-0.5 text-xs font-semibold text-signal-critical">
          {incident.severity}
        </span>
        <span className="bg-base-raised px-2 py-0.5 text-xs text-ink-muted">{incident.status}</span>
      </div>

      <div className="mt-6 grid grid-cols-2 gap-4 border border-base-border bg-base-panel p-4 sm:grid-cols-4">
        <Field label="Type" value={incident.type} />
        <Field label="Detected at" value={new Date(incident.detectedAt).toLocaleString()} />
        <Field label="Baseline failure rate" value={`${incident.baselineFailureRate.toFixed(2)}%`} />
        <Field label="Current failure rate" value={`${incident.currentFailureRate.toFixed(2)}%`} />
        <Field label="Failure rate increase" value={`+${incident.failureRateIncrease.toFixed(2)} pts`} />
        <Field label="Affected transactions" value={incident.affectedTransactions.toLocaleString("en-IN")} />
        <Field label="Affected value" value={formatInr(incident.affectedTransactionValue)} />
        <Field label="Revenue at risk" value={formatInr(incident.revenueAtRisk)} />
      </div>

      <div className="mt-8 space-y-6">
        <AiInvestigationPanel
          incidentId={incident.id}
          incident={incident}
          onDiagnosed={(diag) => {
            setDetail((prev) =>
              prev
                ? {
                    ...prev,
                    incident: {
                      ...prev.incident,
                      rootCause: diag.rootCause,
                    },
                  }
                : prev
            );
          }}
        />

        <RootCauseInvestigationPanel
          incidentId={incident.id}
          incident={incident}
          onInvestigated={(inv) => {
            setDetail((prev) =>
              prev
                ? {
                    ...prev,
                    incident: {
                      ...prev.incident,
                      rootCause: inv.strongestRootCause,
                    },
                  }
                : prev
            );
          }}
        />

        <RecoveryStrategyPanel
          incidentId={incident.id}
          incident={incident}
        />

        <PolicyGatePanel
          incidentId={incident.id}
          incident={incident}
        />
      </div>

      {impact && (
        <div className="mt-8">
          <h2 className="text-sm font-medium text-ink">Financial impact</h2>
          <p className="mt-1 text-xs text-ink-faint">
            Revenue-at-risk is calculated from the actual affected transaction population. No
            financial values are hardcoded.
          </p>
          <div className="mt-3">
            <FinancialImpactSummary impact={impact} />
          </div>
        </div>
      )}

      <div className="mt-8 space-y-6">
        <FailureRateComparisonChart
          baselineFailureRate={incident.baselineFailureRate}
          currentFailureRate={incident.currentFailureRate}
        />
        {breakdown && breakdown.byBank.length > 0 && (
          <FinancialBreakdownChart segments={breakdown.byBank} title="Revenue at risk by bank" />
        )}
        {breakdown && breakdown.byPaymentMethod.length > 0 && (
          <FinancialBreakdownChart segments={breakdown.byPaymentMethod} title="Revenue at risk by payment method" />
        )}
        {breakdown && breakdown.byErrorCode.length > 0 && (
          <FinancialBreakdownChart segments={breakdown.byErrorCode} title="Revenue at risk by error code" />
        )}
        {byBankMethod.length > 0 && <SegmentChart segments={byBankMethod} title="Top affected bank + payment method segments" />}
        {byBank.length > 0 && <SegmentChart segments={byBank} title="Top affected banks (failure rate)" />}
        {byMethod.length > 0 && <SegmentChart segments={byMethod} title="Top affected payment methods (failure rate)" />}
      </div>
    </main>
  );
}
