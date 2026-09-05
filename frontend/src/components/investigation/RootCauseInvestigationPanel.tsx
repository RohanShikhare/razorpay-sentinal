"use client";

import { useState, useEffect, useCallback } from "react";
import {
  investigateIncident,
  fetchLatestInvestigation,
  type IncidentInvestigationResponse,
  type RootCauseHypothesis,
} from "@/lib/investigation";
import type { Incident } from "@/lib/monitoring";

interface RootCauseInvestigationPanelProps {
  incidentId: number;
  incident?: Incident;
  onInvestigated?: (investigation: IncidentInvestigationResponse) => void;
}

function verdictBadge(verdict: string) {
  const v = verdict.toUpperCase();
  switch (v) {
    case "STRONGEST":
      return "bg-emerald-500/15 text-emerald-400 border-emerald-500/30";
    case "LIKELY":
      return "bg-blue-500/15 text-blue-400 border-blue-500/30";
    case "UNLIKELY":
      return "bg-slate-500/15 text-slate-400 border-slate-500/30";
    case "REFUTED":
      return "bg-rose-500/15 text-rose-400 border-rose-500/30";
    case "INCONCLUSIVE":
      return "bg-amber-500/15 text-amber-400 border-amber-500/30";
    default:
      return "bg-base-raised text-ink-muted border-base-border";
  }
}

export function RootCauseInvestigationPanel({
  incidentId,
  incident,
  onInvestigated,
}: RootCauseInvestigationPanelProps) {
  const [investigation, setInvestigation] = useState<IncidentInvestigationResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Load existing investigation on mount without triggering a new POST
  const loadExisting = useCallback(async () => {
    setInitialLoading(true);
    try {
      const existing = await fetchLatestInvestigation(incidentId);
      if (existing) {
        setInvestigation(existing);
      }
    } catch {
      // Silent on 404 or backend unavailable
    } finally {
      setInitialLoading(false);
    }
  }, [incidentId]);

  useEffect(() => {
    loadExisting();
  }, [loadExisting]);

  const handleInvestigate = async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await investigateIncident(incidentId);
      setInvestigation(result);
      if (onInvestigated) {
        onInvestigated(result);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to run root-cause investigation");
    } finally {
      setLoading(false);
    }
  };

  const confidencePct = investigation ? Math.round(investigation.overallConfidence * 100) : 0;

  return (
    <div className="border border-base-border bg-base-panel overflow-hidden">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-base-border bg-base-subtle/50 px-5 py-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="font-mono text-xs text-signal-wire font-medium">
              Deep Root-Cause Investigation
            </span>
            {investigation && (
              <span className="border border-blue-500/30 bg-blue-500/10 px-2 py-0.5 font-mono text-[11px] text-blue-400">
                INVESTIGATION COMPLETE
              </span>
            )}
          </div>
          <h2 className="text-base font-semibold text-ink mt-0.5">Hypothesis Evaluation &amp; Root Cause</h2>
        </div>

        <button
          type="button"
          onClick={handleInvestigate}
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
              <span>Evaluating Hypotheses...</span>
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
                  d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"
                />
              </svg>
              <span>{investigation ? "Re-Investigate Root Cause" : "Investigate Root Cause"}</span>
            </>
          )}
        </button>
      </div>

      {/* Safety & Architectural Boundary Banner */}
      <div className="border-b border-blue-500/20 bg-blue-500/5 px-5 py-2.5 text-xs text-blue-300 flex items-start gap-2.5">
        <svg
          className="w-4 h-4 text-blue-400 shrink-0 mt-0.5"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
          />
        </svg>
        <div>
          <span className="font-semibold text-blue-200">Investigation Boundary:</span> Deep investigation evaluates competing root-cause theories against deterministic telemetry. It is strictly analytical and advisory, without executing retries, routing modifications, or financial changes.
        </div>
      </div>

      {/* Error state */}
      {error && (
        <div className="p-5">
          <div className="border border-signal-critical/40 bg-signal-critical/10 p-3 text-xs text-signal-critical">
            <strong>Investigation Failed:</strong> {error}
          </div>
        </div>
      )}

      {/* Loading state */}
      {loading && (
        <div className="p-8 text-center space-y-3">
          <div className="inline-block animate-pulse text-signal-wire font-mono text-sm">
            [ FORMULATING &amp; RANKING COMPETING HYPOTHESES FOR INCIDENT #{incidentId} ]
          </div>
          <p className="text-xs text-ink-muted max-w-md mx-auto">
            Comparing bank switch failure modes, merchant integration discrepancies, protocol-level signals, and stochastic baseline fluctuations.
          </p>
        </div>
      )}

      {/* Initial state */}
      {!investigation && !loading && !initialLoading && !error && (
        <div className="p-8 text-center space-y-3">
          <div className="text-sm font-medium text-ink">No Deep Investigation on Record</div>
          <p className="text-xs text-ink-muted max-w-lg mx-auto">
            Sentinel performs deeper root-cause hypothesis evaluation on demand. Click &quot;Investigate Root Cause&quot; to systematically compare competing explanations against observed telemetry.
          </p>
        </div>
      )}

      {/* Investigation Results */}
      {investigation && !loading && (
        <div className="p-5 space-y-6">
          {/* Top Headline Section */}
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            {/* Primary Root Cause */}
            <div className="md:col-span-3 border border-base-border bg-base-subtle p-4">
              <div className="flex items-center justify-between">
                <span className="text-xs text-ink-muted">Strongest Identified Root Cause</span>
                <span className="font-mono text-[11px] text-ink-faint">
                  {new Date(investigation.createdAt).toLocaleTimeString()}
                </span>
              </div>
              <div className="mt-1 text-base font-semibold text-ink leading-snug">
                {investigation.strongestRootCause}
              </div>
              <div className="mt-2 flex flex-wrap items-center gap-2">
                <span className="text-[11px] text-ink-muted">Segment:</span>
                <span className="font-mono text-[11px] bg-base-raised border border-base-border px-2 py-0.5 text-ink font-medium">
                  {investigation.affectedSegment}
                </span>
              </div>
              <p className="mt-3 text-xs text-ink-muted leading-relaxed border-t border-base-border pt-2">
                {investigation.summary}
              </p>
            </div>

            {/* Confidence & Review Gate */}
            <div className="border border-base-border bg-base-subtle p-4 flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between">
                  <span className="text-xs text-ink-muted">Overall Confidence</span>
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

              <div className="mt-4 pt-3 border-t border-base-border">
                <div className="text-[11px] text-ink-muted mb-1">Human Review</div>
                {investigation.requiresHumanReview ? (
                  <span className="inline-flex items-center gap-1.5 px-2 py-0.5 text-xs font-semibold border border-rose-500/30 bg-rose-500/10 text-rose-400">
                    <span className="w-1.5 h-1.5 rounded-full bg-rose-500 animate-pulse" />
                    Recommended
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1.5 px-2 py-0.5 text-xs font-semibold border border-emerald-500/30 bg-emerald-500/10 text-emerald-400">
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                    High Signal / Clear
                  </span>
                )}
              </div>
            </div>
          </div>

          {/* Competing Hypotheses Comparison */}
          <div>
            <div className="text-xs font-semibold text-ink uppercase tracking-wide mb-3 flex items-center gap-2">
              <svg className="w-3.5 h-3.5 text-signal-wire" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M3 6l3 18h12l3-18H3z" />
              </svg>
              <span>Competing Hypotheses Evaluation</span>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {investigation.hypotheses.map((h: RootCauseHypothesis, idx: number) => {
                const hConf = Math.round(h.confidence * 100);
                return (
                  <div
                    key={idx}
                    className={`border p-4 transition-colors ${
                      h.verdict === "STRONGEST"
                        ? "border-emerald-500/40 bg-emerald-500/[0.03]"
                        : "border-base-border bg-base-subtle"
                    }`}
                  >
                    <div className="flex flex-wrap items-center justify-between gap-2 border-b border-base-border pb-2">
                      <span className="text-xs font-semibold text-ink flex items-center gap-1.5">
                        <span className="font-mono text-ink-faint">#{idx + 1}</span>
                        <span>{h.hypothesis}</span>
                      </span>
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-xs font-semibold text-ink">{hConf}%</span>
                        <span className={`px-2 py-0.5 font-mono text-[10px] font-bold border ${verdictBadge(h.verdict)}`}>
                          {h.verdict}
                        </span>
                      </div>
                    </div>

                    <div className="mt-3 space-y-2">
                      {/* Supporting Evidence */}
                      {h.supportingEvidence && h.supportingEvidence.length > 0 && (
                        <div>
                          <div className="text-[11px] font-semibold text-emerald-400 flex items-center gap-1">
                            <span>✓</span> Supporting Evidence:
                          </div>
                          <ul className="mt-1 space-y-1 pl-3">
                            {h.supportingEvidence.map((ev: string, evIdx: number) => (
                              <li key={evIdx} className="text-xs text-ink-muted list-disc">
                                {ev}
                              </li>
                            ))}
                          </ul>
                        </div>
                      )}

                      {/* Contradicting Evidence */}
                      {h.contradictingEvidence && h.contradictingEvidence.length > 0 && (
                        <div className="pt-1">
                          <div className="text-[11px] font-semibold text-rose-400 flex items-center gap-1">
                            <span>✕</span> Contradicting Evidence:
                          </div>
                          <ul className="mt-1 space-y-1 pl-3">
                            {h.contradictingEvidence.map((ev: string, evIdx: number) => (
                              <li key={evIdx} className="text-xs text-ink-muted list-disc">
                                {ev}
                              </li>
                            ))}
                          </ul>
                        </div>
                      )}
                    </div>

                    <div className="mt-3 pt-2 border-t border-base-border/50 flex items-center justify-between text-[11px] text-ink-faint">
                      <span>Target Scope:</span>
                      <span className="font-mono text-ink-muted">{h.affectedSegment}</span>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Detailed Reasoning & Telemetry Evidence Comparison */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* Supporting & Contradicting Telemetry */}
            <div className="border border-base-border bg-base-subtle p-4 space-y-4">
              <div>
                <div className="text-xs font-semibold text-ink mb-2 flex items-center gap-1.5">
                  <span className="text-emerald-400 font-bold">✓</span>
                  Observed Telemetry Corroboration
                </div>
                <ul className="space-y-1.5">
                  {investigation.supportingEvidence && investigation.supportingEvidence.length > 0 ? (
                    investigation.supportingEvidence.map((item: string, idx: number) => (
                      <li key={idx} className="text-xs text-ink-muted flex items-start gap-2">
                        <span className="text-emerald-400 text-[11px] shrink-0">•</span>
                        <span>{item}</span>
                      </li>
                    ))
                  ) : (
                    <li className="text-xs text-ink-faint">No specific corroborating telemetry points.</li>
                  )}
                </ul>
              </div>

              {investigation.contradictingEvidence && investigation.contradictingEvidence.length > 0 && (
                <div className="border-t border-base-border pt-3">
                  <div className="text-xs font-semibold text-ink mb-2 flex items-center gap-1.5">
                    <span className="text-rose-400 font-bold">✕</span>
                    Constraining / Counter-Indicative Telemetry
                  </div>
                  <ul className="space-y-1.5">
                    {investigation.contradictingEvidence.map((item: string, idx: number) => (
                      <li key={idx} className="text-xs text-ink-muted flex items-start gap-2">
                        <span className="text-rose-400 text-[11px] shrink-0">•</span>
                        <span>{item}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>

            {/* Investigation Reasoning & Uncertainty */}
            <div className="border border-base-border bg-base-subtle p-4 flex flex-col justify-between space-y-4">
              <div>
                <div className="text-xs font-semibold text-ink mb-2 flex items-center gap-1.5">
                  <svg className="w-3.5 h-3.5 text-signal-wire" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  Diagnostic Deduction &amp; Comparison
                </div>
                <p className="text-xs text-ink-muted leading-relaxed whitespace-pre-line">
                  {investigation.reasoning}
                </p>
              </div>

              {/* Acknowledged Uncertainty */}
              <div className="border-t border-base-border pt-3">
                <div className="text-[11px] font-semibold text-amber-400 mb-1 flex items-center gap-1">
                  <span>⚠</span> Acknowledged Uncertainty &amp; Visibility Limits:
                </div>
                <p className="text-xs text-ink-faint leading-snug">
                  {investigation.uncertainty}
                </p>
              </div>
            </div>
          </div>

          {/* Footer Metadata */}
          <div className="pt-2 border-t border-base-border flex flex-wrap items-center justify-between text-[11px] font-mono text-ink-faint">
            <div>
              Provider: <span className="text-ink-muted">{investigation.provider}</span> ({investigation.model})
            </div>
            <div>
              Investigation ID: <span className="text-ink-muted">#{investigation.investigationId}</span> · Incident #{investigation.incidentId}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
