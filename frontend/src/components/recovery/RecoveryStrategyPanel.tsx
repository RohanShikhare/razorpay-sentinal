"use client";

import { useState, useEffect, useCallback } from "react";
import {
  generateRecoveryStrategy,
  fetchLatestRecoveryStrategy,
  type RecoveryStrategyResponse,
  type AlternativeStrategy,
  type RecoveryStrategyType,
} from "@/lib/recoveryStrategy";
import type { Incident } from "@/lib/monitoring";

interface RecoveryStrategyPanelProps {
  incidentId: number;
  incident?: Incident;
  onStrategyGenerated?: (strategy: RecoveryStrategyResponse) => void;
}

function strategyBadge(strategy: RecoveryStrategyType) {
  switch (strategy) {
    case "DELAYED_RETRY":
      return "bg-emerald-500/15 text-emerald-400 border-emerald-500/30";
    case "IMMEDIATE_RETRY":
      return "bg-cyan-500/15 text-cyan-400 border-cyan-500/30";
    case "RETRY_WITH_BACKOFF":
      return "bg-teal-500/15 text-teal-400 border-teal-500/30";
    case "ALTERNATE_PAYMENT_METHOD":
      return "bg-indigo-500/15 text-indigo-400 border-indigo-500/30";
    case "HUMAN_REVIEW":
      return "bg-amber-500/15 text-amber-400 border-amber-500/30";
    case "MONITOR_ONLY":
      return "bg-blue-500/15 text-blue-400 border-blue-500/30";
    case "NO_ACTION":
      return "bg-slate-500/15 text-slate-400 border-slate-500/30";
    default:
      return "bg-base-raised text-ink-muted border-base-border";
  }
}

function riskBadge(risk: string) {
  switch (risk?.toUpperCase()) {
    case "LOW":
      return "bg-emerald-500/15 text-emerald-400 border-emerald-500/30";
    case "MEDIUM":
      return "bg-amber-500/15 text-amber-400 border-amber-500/30";
    case "HIGH":
      return "bg-rose-500/15 text-rose-400 border-rose-500/30";
    case "CRITICAL":
      return "bg-rose-600/25 text-rose-300 border-rose-500/50";
    default:
      return "bg-base-raised text-ink-muted border-base-border";
  }
}

function formatINR(val?: number): string {
  if (val === undefined || val === null) return "₹0";
  return `₹${Number(val).toLocaleString("en-IN", { maximumFractionDigits: 0 })}`;
}

export function RecoveryStrategyPanel({
  incidentId,
  incident,
  onStrategyGenerated,
}: RecoveryStrategyPanelProps) {
  const [strategy, setStrategy] = useState<RecoveryStrategyResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadExisting = useCallback(async () => {
    setInitialLoading(true);
    try {
      const existing = await fetchLatestRecoveryStrategy(incidentId);
      if (existing) {
        setStrategy(existing);
      }
    } catch {
      // Silent on 404
    } finally {
      setInitialLoading(false);
    }
  }, [incidentId]);

  useEffect(() => {
    loadExisting();
  }, [loadExisting]);

  const handleGenerate = async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await generateRecoveryStrategy(incidentId);
      setStrategy(result);
      if (onStrategyGenerated) {
        onStrategyGenerated(result);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to generate recovery strategy");
    } finally {
      setLoading(false);
    }
  };

  const confidencePct = strategy ? Math.round(strategy.confidence * 100) : 0;

  return (
    <div className="border border-base-border bg-base-panel overflow-hidden">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-base-border bg-base-subtle/50 px-5 py-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="font-mono text-xs text-signal-wire font-medium">
              Module 8 · Recovery Strategy Engine
            </span>
            {strategy && (
              <span className="border border-emerald-500/30 bg-emerald-500/10 px-2 py-0.5 font-mono text-[11px] text-emerald-400">
                PROPOSAL PRODUCED
              </span>
            )}
          </div>
          <h2 className="text-base font-semibold text-ink mt-0.5">
            Bounded Recovery Proposal &amp; Stopping Conditions
          </h2>
        </div>

        <button
          onClick={handleGenerate}
          disabled={loading}
          className="flex items-center gap-2 border border-emerald-500/40 bg-emerald-500/10 px-4 py-2 font-mono text-xs font-semibold text-emerald-400 transition hover:bg-emerald-500/20 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {loading ? (
            <>
              <span className="inline-block h-3.5 w-3.5 animate-spin rounded-full border-2 border-emerald-400 border-t-transparent" />
              <span>Evaluating Strategy...</span>
            </>
          ) : strategy ? (
            <>
              <span>↺</span>
              <span>Re-evaluate Strategy</span>
            </>
          ) : (
            <>
              <span>⚡</span>
              <span>Formulate Recovery Strategy</span>
            </>
          )}
        </button>
      </div>

      {/* Error banner */}
      {error && (
        <div className="border-b border-rose-500/30 bg-rose-500/10 px-5 py-3 text-xs text-rose-400 font-mono flex items-center justify-between">
          <span>⚠ {error}</span>
          <button onClick={() => setError(null)} className="text-rose-400 hover:text-rose-300">
            ✕
          </button>
        </div>
      )}

      {/* Initial loading */}
      {initialLoading && (
        <div className="p-8 text-center text-xs font-mono text-ink-muted">
          Loading existing recovery strategy records...
        </div>
      )}

      {/* Empty State */}
      {!initialLoading && !strategy && !loading && (
        <div className="p-8 text-center">
          <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full border border-base-border bg-base-raised text-xl">
            🛡
          </div>
          <h3 className="text-sm font-semibold text-ink">No Recovery Strategy Formulated Yet</h3>
          <p className="mx-auto mt-1 max-w-md text-xs text-ink-muted">
            The Recovery Strategy Engine evaluates incident telemetry, Module 4 financial impact,
            and Module 7 root causes to synthesize a bounded recovery strategy with strict stopping conditions.
          </p>
          <button
            onClick={handleGenerate}
            className="mt-4 border border-emerald-500/40 bg-emerald-500/10 px-4 py-2 font-mono text-xs font-semibold text-emerald-400 transition hover:bg-emerald-500/20"
          >
            ⚡ Formulate Recovery Strategy Proposal
          </button>
        </div>
      )}

      {/* Active Strategy View */}
      {strategy && !initialLoading && (
        <div className="p-5 space-y-6">
          {/* Top 4 KPI Cards */}
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {/* Recommended Strategy */}
            <div className="border border-base-border bg-base-subtle p-4">
              <div className="font-mono text-[11px] text-ink-muted uppercase tracking-wider">
                Recommended Strategy
              </div>
              <div className="mt-2 flex items-center gap-2">
                <span
                  className={`inline-block border px-2.5 py-1 font-mono text-xs font-bold ${strategyBadge(
                    strategy.selectedStrategy
                  )}`}
                >
                  {strategy.selectedStrategy.replace(/_/g, " ")}
                </span>
                <span className="font-mono text-[11px] text-ink-muted">Rank #{strategy.rank}</span>
              </div>
              <div className="mt-2 text-xs text-ink-muted truncate">
                Segment: <span className="text-ink font-medium">{strategy.affectedSegment}</span>
              </div>
            </div>

            {/* Confidence & Risk */}
            <div className="border border-base-border bg-base-subtle p-4">
              <div className="font-mono text-[11px] text-ink-muted uppercase tracking-wider">
                Confidence &amp; Risk
              </div>
              <div className="mt-2 flex items-baseline gap-2">
                <span className="font-mono text-2xl font-bold text-ink">{confidencePct}%</span>
                <span
                  className={`border px-2 py-0.5 font-mono text-[10px] font-semibold ${riskBadge(
                    strategy.riskLevel
                  )}`}
                >
                  {strategy.riskLevel} RISK
                </span>
              </div>
              <div className="mt-2 text-xs text-ink-muted">
                Source: <span className="font-mono text-signal-wire">{strategy.source}</span>
              </div>
            </div>

            {/* Expected Recovery % */}
            <div className="border border-base-border bg-base-subtle p-4">
              <div className="font-mono text-[11px] text-ink-muted uppercase tracking-wider">
                Expected Recovery
              </div>
              <div className="mt-2 flex items-baseline gap-2">
                <span className="font-mono text-2xl font-bold text-emerald-400">
                  {strategy.expectedRecoveryPercentage}%
                </span>
                <span className="text-xs text-ink-muted">of recoverable</span>
              </div>
              <div className="mt-2 h-1.5 w-full bg-base-raised overflow-hidden rounded">
                <div
                  className="h-full bg-emerald-500 transition-all duration-500"
                  style={{ width: `${strategy.expectedRecoveryPercentage}%` }}
                />
              </div>
            </div>

            {/* Estimated Recoverable Amount */}
            <div className="border border-base-border bg-base-subtle p-4">
              <div className="font-mono text-[11px] text-ink-muted uppercase tracking-wider">
                Strategy Est. Recovery
              </div>
              <div className="mt-2 font-mono text-2xl font-bold text-emerald-400">
                {formatINR(strategy.estimatedRecoveryAmount)}
              </div>
              <div className="mt-2 text-xs text-ink-muted truncate">
                Authoritative cap: {formatINR(strategy.authoritativeRecoverableAmount)}
              </div>
            </div>
          </div>

          {/* Strategy Rationale */}
          <div className="border border-base-border bg-base-subtle p-4">
            <div className="flex items-center justify-between gap-2 border-b border-base-border/50 pb-2 mb-3">
              <h3 className="font-mono text-xs font-semibold text-ink uppercase tracking-wider">
                Strategy Rationale &amp; Technical Logic
              </h3>
              <span className="font-mono text-[11px] text-ink-muted">
                Affected population: {strategy.estimatedAffectedTransactionCount} txns
              </span>
            </div>
            <p className="text-sm text-ink leading-relaxed font-sans">{strategy.rationale}</p>
          </div>

          {/* Authoritative Financial Baseline vs Strategy Estimate */}
          <div className="border border-base-border bg-base-subtle p-4">
            <h3 className="font-mono text-xs font-semibold text-ink uppercase tracking-wider mb-3">
              Authoritative Financial Anchor (Module 4) vs Strategy Proposal
            </h3>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
              <div className="border border-base-border/70 bg-base-panel p-3">
                <div className="font-mono text-[11px] text-ink-muted">Authoritative Revenue at Risk</div>
                <div className="mt-1 font-mono text-base font-bold text-ink">
                  {formatINR(strategy.authoritativeRevenueAtRisk)}
                </div>
                <div className="text-[10px] text-ink-muted mt-1">Module 4 historical snapshot</div>
              </div>
              <div className="border border-base-border/70 bg-base-panel p-3">
                <div className="font-mono text-[11px] text-ink-muted">Authoritative Recoverable</div>
                <div className="mt-1 font-mono text-base font-bold text-ink">
                  {formatINR(strategy.authoritativeRecoverableAmount)}
                </div>
                <div className="text-[10px] text-ink-muted mt-1">Weighted error-code ceiling</div>
              </div>
              <div className="border border-emerald-500/30 bg-emerald-500/5 p-3">
                <div className="font-mono text-[11px] text-emerald-400">Strategy Bounded Estimate</div>
                <div className="mt-1 font-mono text-base font-bold text-emerald-400">
                  {formatINR(strategy.estimatedRecoveryAmount)} ({strategy.expectedRecoveryPercentage}%)
                </div>
                <div className="text-[10px] text-emerald-500/80 mt-1">Advisory recovery expectation</div>
              </div>
            </div>
          </div>

          {/* Bounded Stopping Conditions */}
          <div className="border border-base-border bg-base-subtle p-4">
            <div className="flex items-center gap-2 border-b border-base-border/50 pb-2 mb-3">
              <span className="text-emerald-400">🛡</span>
              <h3 className="font-mono text-xs font-semibold text-ink uppercase tracking-wider">
                Bounded Stopping Conditions &amp; Safeguards
              </h3>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-4">
              <div className="border border-base-border/70 bg-base-panel p-2.5">
                <div className="text-[10px] font-mono text-ink-muted">Max Retry Attempts</div>
                <div className="font-mono text-sm font-bold text-ink mt-0.5">
                  {strategy.stoppingConditions.maxRetryAttempts} attempts / payment
                </div>
              </div>
              <div className="border border-base-border/70 bg-base-panel p-2.5">
                <div className="text-[10px] font-mono text-ink-muted">Execution Window</div>
                <div className="font-mono text-sm font-bold text-ink mt-0.5">
                  {strategy.stoppingConditions.retryWindowMinutes} minutes max
                </div>
              </div>
              <div className="border border-base-border/70 bg-base-panel p-2.5">
                <div className="text-[10px] font-mono text-ink-muted">Backoff Interval</div>
                <div className="font-mono text-sm font-bold text-ink mt-0.5">
                  {strategy.stoppingConditions.backoffBaseSeconds > 0
                    ? `${strategy.stoppingConditions.backoffBaseSeconds}s base exponential`
                    : "No backoff delay"}
                </div>
              </div>
            </div>

            <ul className="space-y-2">
              {strategy.stoppingConditions.conditions?.map((cond, idx) => (
                <li key={idx} className="flex items-start gap-2 text-xs text-ink font-mono">
                  <span className="text-emerald-400 mt-0.5">✓</span>
                  <span>{cond}</span>
                </li>
              ))}
            </ul>
          </div>

          {/* Human Approval Callout */}
          <div
            className={`border p-4 flex items-start gap-3 ${
              strategy.requiresHumanApproval
                ? "border-amber-500/40 bg-amber-500/10 text-amber-300"
                : "border-blue-500/30 bg-blue-500/10 text-blue-300"
            }`}
          >
            <span className="text-lg mt-0.5">
              {strategy.requiresHumanApproval ? "✋" : "ℹ"}
            </span>
            <div>
              <div className="font-mono text-xs font-bold uppercase tracking-wide">
                {strategy.requiresHumanApproval
                  ? "Human Approval Required by Sentinel Safety Policy"
                  : "Automated Policy Gate Eligibility"}
              </div>
              <p className="text-xs mt-1 leading-relaxed opacity-90 font-sans">
                {strategy.requiresHumanApproval
                  ? "Because of risk level, merchant boundary, or confidence constraints, Sentinel recommends that a human incident commander explicitly inspect and authorize this recovery plan before execution."
                  : "This strategy proposal is deterministic and within automated safety thresholds. It remains subject to Module 9 Policy Gate validation prior to any simulation."}
              </p>
            </div>
          </div>

          {/* Alternative Strategies Considered */}
          {strategy.alternatives && strategy.alternatives.length > 0 && (
            <div className="border border-base-border bg-base-subtle p-4">
              <h3 className="font-mono text-xs font-semibold text-ink uppercase tracking-wider mb-3">
                Alternative Strategies Evaluated
              </h3>
              <div className="space-y-3">
                {strategy.alternatives.map((alt: AlternativeStrategy, idx: number) => (
                  <div
                    key={idx}
                    className="border border-base-border/70 bg-base-panel p-3 flex flex-wrap items-center justify-between gap-3"
                  >
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-xs font-bold text-ink-muted">#{alt.rank}</span>
                        <span
                          className={`border px-2 py-0.5 font-mono text-xs font-bold ${strategyBadge(
                            alt.strategy
                          )}`}
                        >
                          {alt.strategy.replace(/_/g, " ")}
                        </span>
                        <span
                          className={`border px-1.5 py-0.2 font-mono text-[10px] ${riskBadge(
                            alt.riskLevel
                          )}`}
                        >
                          {alt.riskLevel}
                        </span>
                      </div>
                      <p className="text-xs text-ink font-sans">{alt.reason}</p>
                    </div>
                    <div className="text-right font-mono">
                      <div className="text-xs font-bold text-ink">{alt.expectedRecoveryPercentage}%</div>
                      <div className="text-[10px] text-ink-muted">expected recovery</div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Safety & Policy Gate Disclaimer */}
          <div className="border border-signal-wire/30 bg-signal-wire/5 p-4 text-xs font-mono text-signal-wire flex items-start gap-2.5">
            <span className="text-sm">⚠</span>
            <div>
              <div className="font-bold">POLICY GATE &amp; EXECUTION BOUNDARY</div>
              <div className="mt-1 opacity-90 font-sans">
                {strategy.advisoryDisclaimer ||
                  "Recovery strategy is advisory and has not been policy-approved or executed. Recovery execution is disabled until Policy Gate validation."}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
