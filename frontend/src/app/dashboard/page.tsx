"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { DashboardStatusHeader } from "@/components/dashboard/DashboardStatusHeader";
import { KpiCards } from "@/components/dashboard/KpiCards";
import { ActiveIncidentHero } from "@/components/dashboard/ActiveIncidentHero";
import { QuickOperationsPanel } from "@/components/dashboard/QuickOperationsPanel";
import { SegmentBreakdownTable } from "@/components/dashboard/SegmentBreakdownTable";
import { ErrorCodeBreakdownTable } from "@/components/dashboard/ErrorCodeBreakdownTable";
import { IncidentsTable } from "@/components/monitoring/IncidentsTable";
import { FailureRateComparisonChart } from "@/components/monitoring/FailureRateComparisonChart";
import { FinancialImpactSummary } from "@/components/financial/FinancialImpactSummary";
import { fetchIncidents, fetchMonitoringSummary, type Incident, type MonitoringSummary } from "@/lib/monitoring";
import { fetchFinancialImpact, type FinancialImpact } from "@/lib/financial";
import { fetchPaymentSummary, type PaymentSummary } from "@/lib/payments";

const SEVERITY_RANK: Record<Incident["severity"], number> = { LOW: 0, MEDIUM: 1, HIGH: 2, CRITICAL: 3 };
const AUTO_REFRESH_INTERVAL_MS = 20_000;

function pickFeaturedIncident(incidents: Incident[]): Incident | null {
  if (incidents.length === 0) return null;
  return [...incidents].sort((a, b) => {
    const severityDiff = SEVERITY_RANK[b.severity] - SEVERITY_RANK[a.severity];
    if (severityDiff !== 0) return severityDiff;
    return new Date(b.detectedAt).getTime() - new Date(a.detectedAt).getTime();
  })[0];
}

export default function DashboardPage() {
  const [summary, setSummary] = useState<MonitoringSummary | null>(null);
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [featuredImpact, setFeaturedImpact] = useState<FinancialImpact | null>(null);
  const [paymentSummary, setPaymentSummary] = useState<PaymentSummary | null>(null);

  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const inFlight = useRef(false);

  const load = useCallback(async () => {
    if (inFlight.current) return;
    inFlight.current = true;
    setRefreshing(true);
    setError(null);
    try {
      const [summaryData, incidentsPage, paymentSummaryData] = await Promise.all([
        fetchMonitoringSummary(),
        fetchIncidents(0, 10),
        fetchPaymentSummary(),
      ]);
      setSummary(summaryData);
      setIncidents(incidentsPage.content);
      setPaymentSummary(paymentSummaryData);

      const featured = pickFeaturedIncident(incidentsPage.content);
      if (featured) {
        try {
          setFeaturedImpact(await fetchFinancialImpact(featured.id));
        } catch {
          setFeaturedImpact(null);
        }
      } else {
        setFeaturedImpact(null);
      }

      setLastUpdated(new Date());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to load monitoring data");
    } finally {
      setLoading(false);
      setRefreshing(false);
      inFlight.current = false;
    }
  }, []);

  useEffect(() => {
    load();
    const interval = setInterval(load, AUTO_REFRESH_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [load]);

  const featured = pickFeaturedIncident(incidents);

  return (
    <main className="mx-auto max-w-7xl px-6 py-8">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <div>
          <p className="font-mono text-xs text-signal-wire">Module 5 - Command Center</p>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight text-ink">Razorpay Sentinel</h1>
          <p className="text-sm text-ink-muted">AI Payment Incident Commander</p>
        </div>
      </div>

      <div className="mt-4">
        <DashboardStatusHeader
          summary={summary}
          lastUpdated={lastUpdated}
          refreshing={refreshing}
          onRefresh={load}
        />
      </div>

      {error && (
        <div className="mt-4 flex items-center justify-between border border-signal-critical/40 bg-signal-critical/10 px-4 py-3 text-sm text-signal-critical">
          <span>Unable to load monitoring data - {error}</span>
          <button type="button" onClick={load} className="underline underline-offset-2">
            Retry
          </button>
        </div>
      )}

      <div className="mt-5">
        {loading || !summary ? (
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-5">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="h-20 animate-pulse border border-base-border bg-base-panel" />
            ))}
          </div>
        ) : (
          <KpiCards summary={summary} />
        )}
      </div>

      <div className="mt-6">
        {loading ? null : featured ? (
          <ActiveIncidentHero incident={featured} impact={featuredImpact} />
        ) : (
          <div className="border border-base-border bg-base-panel px-5 py-6">
            <div className="flex items-center gap-2">
              <span className="h-2 w-2 rounded-full bg-signal-nominal" aria-hidden />
              <span className="text-sm font-semibold tracking-wide text-signal-nominal">SYSTEM HEALTHY</span>
            </div>
            <p className="mt-2 max-w-xl text-sm text-ink-muted">
              No active critical incidents. Payment success rate is{" "}
              {summary ? `${summary.successRate.toFixed(1)}%` : "-"}, current failure rate is{" "}
              {summary ? `${summary.failureRate.toFixed(1)}%` : "-"}, and revenue at risk is{" "}
              {summary ? `Rs ${Number(summary.revenueAtRisk).toLocaleString("en-IN")}` : "-"}.
            </p>
          </div>
        )}
      </div>

      <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-[2fr_1fr]">
        <div className="space-y-6">
          {summary && (
            <FailureRateComparisonChart
              baselineFailureRate={summary.baselineFailureRate}
              currentFailureRate={summary.failureRate}
            />
          )}

          <div>
            <h2 className="text-sm font-medium text-ink">Financial exposure</h2>
            <p className="mt-1 text-xs text-ink-faint">
              Revenue-at-risk is calculated from the actual affected transaction population - nothing here is
              hardcoded.
            </p>
            <div className="mt-3">
              {featured && featuredImpact ? (
                <FinancialImpactSummary impact={featuredImpact} />
              ) : (
                <div className="border border-base-border bg-base-panel px-4 py-6 text-center text-sm text-ink-muted">
                  No financial exposure - no active incidents detected.
                </div>
              )}
            </div>
          </div>
        </div>

        <QuickOperationsPanel onDataChanged={load} />
      </div>

      <div className="mt-8 space-y-6">
        <h2 className="text-sm font-medium text-ink">Segment breakdown</h2>
        {paymentSummary ? (
          <>
            <SegmentBreakdownTable title="By bank" labelHeader="Bank" segments={paymentSummary.byBank} />
            <SegmentBreakdownTable
              title="By payment method"
              labelHeader="Method"
              segments={paymentSummary.byPaymentMethod}
            />
            <ErrorCodeBreakdownTable segments={paymentSummary.byErrorCode} />
          </>
        ) : (
          <div className="border border-base-border bg-base-panel px-4 py-10 text-center text-sm text-ink-muted">
            loading segment data...
          </div>
        )}
      </div>

      <div className="mt-8">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-medium text-ink">Recent incidents</h2>
          <a href="/incidents" className="text-xs text-signal-wire underline underline-offset-2">
            View all incidents
          </a>
        </div>
        <div className="mt-3">
          {loading ? (
            <div className="border border-base-border bg-base-panel px-4 py-10 text-center text-sm text-ink-muted">
              loading incidents...
            </div>
          ) : (
            <IncidentsTable incidents={incidents} />
          )}
        </div>
      </div>
    </main>
  );
}
