"use client";

import { useCallback, useEffect, useState } from "react";
import { HealthBanner } from "@/components/monitoring/HealthBanner";
import { SummaryMetrics } from "@/components/monitoring/SummaryMetrics";
import { FailureRateComparisonChart } from "@/components/monitoring/FailureRateComparisonChart";
import { SegmentChart } from "@/components/monitoring/SegmentChart";
import {
  fetchMonitoringSummary,
  runDetection,
  type DetectionResult,
  type MonitoringSummary,
} from "@/lib/monitoring";

export default function MonitoringPage() {
  const [summary, setSummary] = useState<MonitoringSummary | null>(null);
  const [detection, setDetection] = useState<DetectionResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [detecting, setDetecting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadSummary = useCallback(async () => {
    try {
      const data = await fetchMonitoringSummary();
      setSummary(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load monitoring summary");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadSummary();
  }, [loadSummary]);

  async function handleRunDetection() {
    setDetecting(true);
    setError(null);
    try {
      const result = await runDetection();
      setDetection(result);
      await loadSummary();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to run detection");
    } finally {
      setDetecting(false);
    }
  }

  const byBank = detection?.topAffectedSegments.filter((s) => s.dimension === "BANK") ?? [];
  const byMethod = detection?.topAffectedSegments.filter((s) => s.dimension === "PAYMENT_METHOD") ?? [];
  const byBankMethod = detection?.topAffectedSegments.filter((s) => s.dimension === "BANK_PAYMENT_METHOD") ?? [];

  return (
    <main className="mx-auto max-w-6xl px-6 py-10">
      <p className="font-mono text-xs text-signal-wire">Monitoring &amp; Incident Detection</p>
      <div className="mt-2 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight text-ink">Command Center</h1>
        <button
          type="button"
          onClick={handleRunDetection}
          disabled={detecting}
          className="border border-signal-wire/50 bg-signal-wire/10 px-4 py-2 text-sm font-medium text-signal-wire hover:bg-signal-wire/20 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {detecting ? "Running detection..." : "Run Detection"}
        </button>
      </div>
      <p className="mt-2 max-w-2xl text-sm text-ink-muted">
        This page shows the deterministic Monitoring &amp; Incident Detection Engine&apos;s own
        conclusions — it never infers an incident client-side. The backend is the source of truth.
      </p>

      {error && (
        <div className="mt-4 border border-signal-critical/40 bg-signal-critical/10 px-4 py-3 text-sm text-signal-critical">
          {error}
        </div>
      )}

      <div className="mt-6">
        {loading || !summary ? (
          <div className="border border-base-border bg-base-panel px-4 py-10 text-center text-sm text-ink-muted">
            loading monitoring status...
          </div>
        ) : (
          <HealthBanner summary={summary} />
        )}
      </div>

      {summary && (
        <div className="mt-4">
          <SummaryMetrics summary={summary} />
        </div>
      )}

      {detection && (
        <div className="mt-8 space-y-6">
          <div className="border border-base-border bg-base-panel p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="text-sm font-medium text-ink">Latest detection result</span>
              <span className="font-mono text-xs text-ink-faint">
                window: {detection.monitoringWindowMinutes} min
              </span>
            </div>
            {detection.incidentDetected ? (
              <p className="mt-2 text-sm text-signal-critical">
                🔴 {detection.severity} incident detected ({detection.incidentType}) — failure rate moved
                from {detection.baselineFailureRate.toFixed(2)}% to {detection.currentFailureRate.toFixed(2)}%.
              </p>
            ) : (
              <p className="mt-2 text-sm text-signal-nominal">
                No incident detected. {detection.message}
              </p>
            )}
          </div>

          <FailureRateComparisonChart
            baselineFailureRate={detection.baselineFailureRate}
            currentFailureRate={detection.currentFailureRate}
          />

          {byBankMethod.length > 0 && <SegmentChart segments={byBankMethod} title="Bank + payment method concentration" />}
          {byBank.length > 0 && <SegmentChart segments={byBank} title="Failure rate by bank" />}
          {byMethod.length > 0 && <SegmentChart segments={byMethod} title="Failure rate by payment method" />}
        </div>
      )}
    </main>
  );
}
