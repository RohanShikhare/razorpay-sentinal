"use client";

import { useState, useEffect, useCallback } from "react";
import {
  diagnoseIncident,
  fetchLatestDiagnosis,
  type AiDiagnosisResponse,
  type ControlledAction,
  type RiskLevel,
} from "@/lib/ai";
import type { Incident } from "@/lib/monitoring";

interface AiInvestigationPanelProps {
  incidentId: number;
  incident?: Incident;
  onDiagnosed?: (diagnosis: AiDiagnosisResponse) => void;
}

function formatAction(action: ControlledAction): { label: string; description: string; badgeColor: string } {
  switch (action) {
    case "IMMEDIATE_RETRY":
      return {
        label: "Immediate Retry",
        description: "Transient network failure detected. Safe to retry immediately for eligible payments.",
        badgeColor: "bg-blue-500/10 text-blue-400 border-blue-500/30",
      };
    case "DELAYED_RETRY":
      return {
        label: "Delayed Retry (Backoff)",
        description: "Bank or gateway rate limiting/throttling detected. Schedule retry with exponential backoff.",
        badgeColor: "bg-amber-500/10 text-amber-400 border-amber-500/30",
      };
    case "ALTERNATE_PAYMENT_METHOD":
      return {
        label: "Reroute to Alternate Method/Gateway",
        description: "Downstream rails severely degraded. Prompt customer or reroute to healthy secondary rail/method.",
        badgeColor: "bg-purple-500/10 text-purple-400 border-purple-500/30",
      };
    case "MONITOR":
      return {
        label: "Passive Monitoring",
        description: "Localized or low-velocity anomaly. Continue telemetry monitoring before active intervention.",
        badgeColor: "bg-emerald-500/10 text-emerald-400 border-emerald-500/30",
      };
    case "HUMAN_REVIEW":
      return {
        label: "Manual Human Review",
        description: "Ambiguous signals, merchant configuration dispute, or high financial exposure requiring engineer approval.",
        badgeColor: "bg-rose-500/10 text-rose-400 border-rose-500/30",
      };
    case "NO_ACTION":
      return {
        label: "No Action (Normal Fluctuation)",
        description: "Observed variations are within expected baseline statistical noise.",
        badgeColor: "bg-slate-500/10 text-slate-400 border-slate-500/30",
      };
    default:
      return {
        label: action,
        description: "Advisory recommendation",
        badgeColor: "bg-slate-500/10 text-slate-400 border-slate-500/30",
      };
  }
}

function riskBadge(risk: RiskLevel) {
  switch (risk) {
    case "CRITICAL":
      return "bg-signal-critical/15 text-signal-critical border-signal-critical/30";
    case "HIGH":
      return "bg-rose-500/15 text-rose-400 border-rose-500/30";
    case "MEDIUM":
      return "bg-amber-500/15 text-amber-400 border-amber-500/30";
    case "LOW":
      return "bg-signal-nominal/15 text-signal-nominal border-signal-nominal/30";
    default:
      return "bg-base-raised text-ink-muted border-base-border";
  }
}

export function AiInvestigationPanel({ incidentId, incident, onDiagnosed }: AiInvestigationPanelProps) {
  const [diagnosis, setDiagnosis] = useState<AiDiagnosisResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Load latest existing diagnosis on mount without triggering a new POST
  const loadExisting = useCallback(async () => {
    setInitialLoading(true);
    try {
      const existing = await fetchLatestDiagnosis(incidentId);
      if (existing) {
        setDiagnosis(existing);
      }
    } catch {
      // Non-blocking: If 404 or backend unavailable, silently leave empty
    } finally {
      setInitialLoading(false);
    }
  }, [incidentId]);

  useEffect(() => {
    loadExisting();
  }, [loadExisting]);

  const handleDiagnose = async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await diagnoseIncident(incidentId);
      setDiagnosis(result);
      if (onDiagnosed) {
        onDiagnosed(result);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to run AI diagnosis");
    } finally {
      setLoading(false);
    }
  };

  const actionMeta = diagnosis ? formatAction(diagnosis.recommendedAction) : null;
  const confidencePct = diagnosis ? Math.round(diagnosis.confidence * 100) : 0;

  return (
    <div className="border border-base-border bg-base-panel overflow-hidden">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-base-border bg-base-subtle/50 px-5 py-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="font-mono text-xs text-signal-wire font-medium">Module 6 · Advisory Intelligence</span>
            {diagnosis && (
              <span className="border border-emerald-500/30 bg-emerald-500/10 px-2 py-0.5 font-mono text-[11px] text-emerald-400">
                DIAGNOSIS ACTIVE
              </span>
            )}
          </div>
          <h2 className="text-base font-semibold text-ink mt-0.5">AI Diagnostic Agent</h2>
        </div>

        <button
          type="button"
          onClick={handleDiagnose}
          disabled={loading}
          className="inline-flex items-center gap-2 border border-signal-wire/40 bg-signal-wire/10 px-4 py-2 text-xs font-semibold text-signal-wire hover:bg-signal-wire/20 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {loading ? (
            <>
              <svg
                className="animate-spin h-3.5 w-3.5 text-signal-wire"
                xmlns="http://www.w3.org/2000/svg"
                fill="none"
                viewBox="0 0 24 24"
              >
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path
                  className="opacity-75"
                  fill="currentColor"
                  d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                />
              </svg>
              <span>Analyzing Evidence...</span>
            </>
          ) : (
            <>
              <svg
                className="w-3.5 h-3.5 text-signal-wire"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M13 10V3L4 14h7v7l9-11h-7z"
                />
              </svg>
              <span>{diagnosis ? "Re-diagnose with AI" : "Investigate with AI"}</span>
            </>
          )}
        </button>
      </div>

      {/* Mandatory Safety & Architectural Boundary Banner */}
      <div className="border-b border-amber-500/20 bg-amber-500/5 px-5 py-2.5 text-xs text-amber-300 flex items-start gap-2.5">
        <svg
          className="w-4 h-4 text-amber-400 shrink-0 mt-0.5"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
          />
        </svg>
        <div>
          <span className="font-semibold text-amber-300">Safety Guardrail:</span> AI recommendations are strictly advisory. AI cannot execute payments, modify transactions, or bypass deterministic financial and policy engines.
        </div>
      </div>

      {/* Error state */}
      {error && (
        <div className="p-5">
          <div className="border border-signal-critical/40 bg-signal-critical/10 p-3 text-xs text-signal-critical">
            <strong>Diagnosis Failed:</strong> {error}
          </div>
        </div>
      )}

      {/* Loading state */}
      {loading && (
        <div className="p-8 text-center space-y-3">
          <div className="inline-block animate-pulse text-signal-wire font-mono text-sm">
            [ AI DIAGNOSTIC ENGINE ANALYZING INCIDENT #{incidentId} ]
          </div>
          <p className="text-xs text-ink-muted max-w-md mx-auto">
            Extracting deterministic telemetry, analyzing segment concentrations, evaluating baseline shifts, and calculating bounded recovery recommendations.
          </p>
        </div>
      )}

      {/* Initial state (no diagnosis and not loading) */}
      {!diagnosis && !loading && !initialLoading && !error && (
        <div className="p-8 text-center space-y-3">
          <div className="text-sm font-medium text-ink">No AI Investigation on Record</div>
          <p className="text-xs text-ink-muted max-w-lg mx-auto">
            Sentinel preserves system resources by only triggering AI diagnosis on demand. Click &quot;Investigate with AI&quot; above to synthesize telemetry evidence and formulate a root-cause hypothesis.
          </p>
        </div>
      )}

      {/* Active Diagnosis Result */}
      {diagnosis && !loading && (
        <div className="p-5 space-y-6">
          {/* Top Key Metrics Row */}
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            {/* Root Cause Card */}
            <div className="md:col-span-2 border border-base-border bg-base-subtle p-4">
              <div className="flex items-center justify-between">
                <span className="text-xs text-ink-muted">Diagnosed Root Cause</span>
                <span className="font-mono text-[11px] text-ink-faint">
                  {new Date(diagnosis.timestamp).toLocaleTimeString()}
                </span>
              </div>
              <div className="mt-1 text-sm font-semibold text-ink leading-snug">
                {diagnosis.rootCause}
              </div>
              <div className="mt-2 flex flex-wrap items-center gap-1.5">
                <span className="text-[11px] text-ink-muted">Segment:</span>
                <span className="font-mono text-[11px] bg-base-raised border border-base-border px-1.5 py-0.5 text-ink">
                  {diagnosis.affectedSegment}
                </span>
              </div>
            </div>

            {/* Confidence & Risk Level */}
            <div className="border border-base-border bg-base-subtle p-4 flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between">
                  <span className="text-xs text-ink-muted">AI Confidence</span>
                  <span className="font-mono text-xs font-semibold text-ink">{confidencePct}%</span>
                </div>
                <div className="mt-2 w-full bg-base-raised h-1.5 rounded-full overflow-hidden">
                  <div
                    className={`h-full ${
                      confidencePct >= 80
                        ? "bg-signal-nominal"
                        : confidencePct >= 60
                        ? "bg-amber-400"
                        : "bg-signal-critical"
                    }`}
                    style={{ width: `${confidencePct}%` }}
                  />
                </div>
              </div>

              <div className="mt-3 pt-2 border-t border-base-border flex items-center justify-between">
                <span className="text-xs text-ink-muted">Risk Level</span>
                <span className={`px-2 py-0.5 text-[11px] font-semibold border ${riskBadge(diagnosis.riskLevel)}`}>
                  {diagnosis.riskLevel}
                </span>
              </div>
            </div>

            {/* Recovery & Human Gate */}
            <div className="border border-base-border bg-base-subtle p-4 flex flex-col justify-between">
              <div>
                <span className="text-xs text-ink-muted">Advisory Recovery Est.</span>
                <div className="mt-1 font-mono text-xl font-semibold text-signal-nominal">
                  {diagnosis.estimatedRecoveryPercentage}%
                </div>
                <div className="text-[10px] text-ink-faint">AI advisory potential</div>
              </div>

              <div className="mt-2 pt-2 border-t border-base-border">
                {diagnosis.requiresHumanApproval ? (
                  <div className="flex items-center gap-1.5 text-rose-400 text-xs font-semibold">
                    <span className="inline-block w-2 h-2 rounded-full bg-rose-500 animate-pulse" />
                    Human Approval Required
                  </div>
                ) : (
                  <div className="flex items-center gap-1.5 text-emerald-400 text-xs font-semibold">
                    <span className="inline-block w-2 h-2 rounded-full bg-emerald-500" />
                    Automated Gate Eligible
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* Recommended Action Card */}
          {actionMeta && (
            <div className="border border-base-border bg-base-subtle p-4">
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-base-border pb-3">
                <div className="text-xs text-ink-muted">Recommended Bounded Action (Advisory)</div>
                <span className={`px-2.5 py-1 text-xs font-mono font-bold border ${actionMeta.badgeColor}`}>
                  {diagnosis.recommendedAction}
                </span>
              </div>
              <div className="mt-3">
                <div className="text-sm font-semibold text-ink">{actionMeta.label}</div>
                <p className="mt-0.5 text-xs text-ink-muted">{actionMeta.description}</p>
              </div>
            </div>
          )}

          {/* Reasoning & Evidence Split */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* Observed Evidence */}
            <div className="border border-base-border bg-base-subtle p-4">
              <div className="text-xs font-semibold text-ink mb-2 flex items-center gap-1.5">
                <svg className="w-3.5 h-3.5 text-signal-wire" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                </svg>
                Observed Telemetry Evidence
              </div>
              <ul className="space-y-1.5">
                {diagnosis.evidence && diagnosis.evidence.length > 0 ? (
                  diagnosis.evidence.map((item, idx) => (
                    <li key={idx} className="text-xs text-ink-muted flex items-start gap-2">
                      <span className="font-mono text-signal-wire text-[10px] shrink-0 mt-0.5">•</span>
                      <span>{item}</span>
                    </li>
                  ))
                ) : (
                  <li className="text-xs text-ink-faint">No specific evidence points recorded.</li>
                )}
              </ul>
            </div>

            {/* Diagnostic Reasoning */}
            <div className="border border-base-border bg-base-subtle p-4">
              <div className="text-xs font-semibold text-ink mb-2 flex items-center gap-1.5">
                <svg className="w-3.5 h-3.5 text-signal-wire" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                Inference &amp; Reasoning
              </div>
              <p className="text-xs text-ink-muted leading-relaxed whitespace-pre-line">
                {diagnosis.reasoning}
              </p>
            </div>
          </div>

          {/* Provider Meta Footer */}
          <div className="pt-2 border-t border-base-border flex flex-wrap items-center justify-between text-[11px] font-mono text-ink-faint">
            <div>
              Provider: <span className="text-ink-muted">{diagnosis.provider}</span> ({diagnosis.model})
            </div>
            <div>
              Diagnosis ID: <span className="text-ink-muted">#{diagnosis.diagnosisId}</span> · Incident #{diagnosis.incidentId}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
