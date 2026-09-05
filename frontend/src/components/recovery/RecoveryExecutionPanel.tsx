"use client";

import { useState, useEffect, useCallback } from "react";
import {
  executeRecovery,
  fetchLatestRecoveryExecution,
  type RecoveryExecutionResponse,
  type RecoveryExecutionAttemptDto,
  type ExecutionStatus,
  type RecoveryOutcome,
  type StoppingReason,
} from "@/lib/recoveryExecution";
import { fetchLatestPolicyEvaluation, type PolicyEvaluationResponse } from "@/lib/policy";
import type { Incident } from "@/lib/monitoring";

interface RecoveryExecutionPanelProps {
  incidentId: number;
  incident?: Incident;
  policyEvaluation?: PolicyEvaluationResponse | null;
  onExecutionCompleted?: (execution: RecoveryExecutionResponse) => void;
}

function formatInr(value: number | string | undefined | null): string {
  if (value === undefined || value === null) return "₹0";
  const n = typeof value === "string" ? Number(value) : value;
  if (Number.isNaN(n)) return "₹0";
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(n);
}

function statusBadge(status: ExecutionStatus) {
  switch (status) {
    case "COMPLETED":
      return {
        label: "COMPLETED",
        badge: "bg-emerald-500/15 text-emerald-400 border-emerald-500/30",
        dot: "bg-emerald-400",
      };
    case "PARTIALLY_COMPLETED":
      return {
        label: "PARTIAL RECOVERY",
        badge: "bg-amber-500/15 text-amber-400 border-amber-500/30",
        dot: "bg-amber-400",
      };
    case "STOPPED":
      return {
        label: "STOPPED",
        badge: "bg-orange-500/15 text-orange-400 border-orange-500/30",
        dot: "bg-orange-400",
      };
    case "BLOCKED":
      return {
        label: "BLOCKED BY POLICY",
        badge: "bg-rose-500/15 text-rose-400 border-rose-500/30",
        dot: "bg-rose-400",
      };
    case "RUNNING":
      return {
        label: "EXECUTING SIMULATION...",
        badge: "bg-blue-500/15 text-blue-400 border-blue-500/30 animate-pulse",
        dot: "bg-blue-400",
      };
    case "FAILED":
      return {
        label: "FAILED",
        badge: "bg-rose-500/15 text-rose-400 border-rose-500/30",
        dot: "bg-rose-400",
      };
    default:
      return {
        label: status,
        badge: "bg-base-raised text-ink-muted border-base-border",
        dot: "bg-ink-muted",
      };
  }
}

function outcomeBadge(outcome: RecoveryOutcome) {
  switch (outcome) {
    case "RECOVERED":
      return "bg-emerald-500/15 text-emerald-400 border-emerald-500/30";
    case "FAILED":
      return "bg-rose-500/15 text-rose-400 border-rose-500/30";
    case "SKIPPED":
      return "bg-zinc-500/15 text-zinc-400 border-zinc-500/30";
    case "ALREADY_SUCCESSFUL":
      return "bg-cyan-500/15 text-cyan-400 border-cyan-500/30";
    case "STOPPED_BY_LIMIT":
      return "bg-amber-500/15 text-amber-400 border-amber-500/30";
    case "STOPPED_BY_CIRCUIT_BREAKER":
      return "bg-orange-500/15 text-orange-400 border-orange-500/30";
    default:
      return "bg-base-raised text-ink-muted border-base-border";
  }
}

function stoppingReasonLabel(reason: StoppingReason | null): { label: string; desc: string; color: string } {
  switch (reason) {
    case "COMPLETED_ALL_ELIGIBLE":
      return {
        label: "Simulation Completed",
        desc: "All eligible transactions were attempted or processed within policy limits.",
        color: "text-emerald-400 border-emerald-500/30 bg-emerald-950/20",
      };
    case "MAX_ATTEMPTS_REACHED":
      return {
        label: "Max Retry Limit Reached",
        desc: "Execution halted after reaching the policy-enforced maximum retry count per transaction.",
        color: "text-amber-400 border-amber-500/30 bg-amber-950/20",
      };
    case "RETRY_WINDOW_EXCEEDED":
      return {
        label: "Retry Window Exceeded",
        desc: "Elapsed time exceeded the policy-enforced recovery time window.",
        color: "text-amber-400 border-amber-500/30 bg-amber-950/20",
      };
    case "CIRCUIT_BREAKER_FAILURE_RATE":
      return {
        label: "Circuit Breaker Tripped (Failure Rate > 50%)",
        desc: "Recovery halted automatically to prevent cascading degradation.",
        color: "text-rose-400 border-rose-500/30 bg-rose-950/20",
      };
    case "INCIDENT_RESOLVED":
      return {
        label: "Incident Resolved",
        desc: "Execution stopped because the incident has been marked resolved.",
        color: "text-blue-400 border-blue-500/30 bg-blue-950/20",
      };
    case "HUMAN_APPROVAL_REQUIRED":
      return {
        label: "Human Approval Required",
        desc: "Execution blocked — requires explicit human review and authorization.",
        color: "text-blue-400 border-blue-500/30 bg-blue-950/20",
      };
    case "POLICY_BLOCKED":
      return {
        label: "Blocked by Guardrail Policy",
        desc: "Policy Gate evaluated this strategy as unsafe or rejected.",
        color: "text-rose-400 border-rose-500/30 bg-rose-950/20",
      };
    case "NO_ACTION_REQUIRED":
      return {
        label: "No Action Required",
        desc: "Monitoring only strategy executed with 0 payment interventions.",
        color: "text-blue-400 border-blue-500/30 bg-blue-950/20",
      };
    default:
      return {
        label: reason ?? "Standard Execution",
        desc: "Bounded recovery simulation completed.",
        color: "text-ink-muted border-base-border bg-base-raised",
      };
  }
}

export function RecoveryExecutionPanel({
  incidentId,
  incident,
  policyEvaluation: propPolicy,
  onExecutionCompleted,
}: RecoveryExecutionPanelProps) {
  const [execution, setExecution] = useState<RecoveryExecutionResponse | null>(null);
  const [policy, setPolicy] = useState<PolicyEvaluationResponse | null>(propPolicy ?? null);
  const [loading, setLoading] = useState(false);
  const [executing, setExecuting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [attemptFilter, setAttemptFilter] = useState<"ALL" | "RECOVERED" | "FAILED" | "SKIPPED">("ALL");

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [execData, policyData] = await Promise.all([
        fetchLatestRecoveryExecution(incidentId).catch(() => null),
        propPolicy ? Promise.resolve(propPolicy) : fetchLatestPolicyEvaluation(incidentId).catch(() => null),
      ]);
      if (execData) setExecution(execData);
      if (policyData) setPolicy(policyData);
    } catch {
      // no existing execution
    } finally {
      setLoading(false);
    }
  }, [incidentId, propPolicy]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleExecute = async () => {
    setExecuting(true);
    setError(null);
    try {
      const result = await executeRecovery(incidentId);
      setExecution(result);
      if (onExecutionCompleted) {
        onExecutionCompleted(result);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Recovery execution failed");
    } finally {
      setExecuting(false);
    }
  };

  const isExecutionAllowed = policy?.executionAllowed === true;
  const isPolicyApproved =
    policy?.decision === "APPROVED" || policy?.decision === "APPROVED_WITH_LIMITS";
  const canExecute = isExecutionAllowed && isPolicyApproved && !executing;

  const filteredAttempts: RecoveryExecutionAttemptDto[] =
    execution?.attempts.filter((a) => {
      if (attemptFilter === "ALL") return true;
      if (attemptFilter === "RECOVERED") return a.outcome === "RECOVERED";
      if (attemptFilter === "FAILED") return a.outcome === "FAILED";
      if (attemptFilter === "SKIPPED")
        return (
          a.outcome === "SKIPPED" ||
          a.outcome === "STOPPED_BY_LIMIT" ||
          a.outcome === "STOPPED_BY_CIRCUIT_BREAKER"
        );
      return true;
    }) ?? [];

  return (
    <div className="border border-base-border bg-base-panel">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-base-border px-5 py-4">
        <div>
          <span className="font-mono text-xs uppercase tracking-wider text-emerald-400">
            Module 10 · Recovery Execution Simulator
          </span>
          <h2 className="text-base font-semibold text-ink">Bounded Recovery Execution</h2>
          <p className="mt-0.5 text-xs text-ink-muted">
            Bounded execution engine acting strictly within Module 9 policy guardrails to recover verified revenue.
          </p>
        </div>

        <div className="flex items-center gap-2">
          {execution && (
            <span
              className={`inline-flex items-center gap-1.5 border px-2.5 py-1 text-xs font-mono font-medium ${
                statusBadge(execution.status).badge
              }`}
            >
              <span className={`h-1.5 w-1.5 rounded-full ${statusBadge(execution.status).dot}`} />
              {statusBadge(execution.status).label}
            </span>
          )}

          <button
            onClick={handleExecute}
            disabled={!canExecute}
            className={`inline-flex items-center gap-2 border px-4 py-1.5 text-xs font-semibold transition-all ${
              canExecute
                ? "border-emerald-500/50 bg-emerald-500/15 text-emerald-300 hover:bg-emerald-500/25 cursor-pointer shadow-sm shadow-emerald-950"
                : "border-base-border bg-base-raised text-ink-faint cursor-not-allowed opacity-60"
            }`}
          >
            {executing ? (
              <>
                <svg className="h-3.5 w-3.5 animate-spin text-emerald-400" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                </svg>
                <span>Simulating Recovery...</span>
              </>
            ) : (
              <>
                <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z" />
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <span>{execution ? "Re-run Execution" : "Execute Recovery"}</span>
              </>
            )}
          </button>
        </div>
      </div>

      {/* Policy Gate Pre-Execution Verification Bar */}
      <div className="border-b border-base-border bg-base-raised/60 px-5 py-3">
        <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
          <div className="flex items-center gap-3">
            <span className="font-mono uppercase text-ink-muted">Policy Gate Authority:</span>
            {policy ? (
              <span
                className={`border px-2 py-0.5 font-mono text-[11px] font-semibold ${
                  policy.decision === "APPROVED"
                    ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-400"
                    : policy.decision === "APPROVED_WITH_LIMITS"
                    ? "border-amber-500/30 bg-amber-500/10 text-amber-400"
                    : policy.decision === "HUMAN_REVIEW"
                    ? "border-blue-500/30 bg-blue-500/10 text-blue-400"
                    : "border-rose-500/30 bg-rose-500/10 text-rose-400"
                }`}
              >
                {policy.decision}
              </span>
            ) : (
              <span className="text-ink-faint">Evaluating policy...</span>
            )}
            <span className="text-ink-faint">|</span>
            <span className="font-mono text-ink-muted">
              Execution Status:{" "}
              <strong
                className={
                  isExecutionAllowed ? "text-emerald-400" : "text-rose-400"
                }
              >
                {isExecutionAllowed ? "ALLOWED BY POLICY" : "EXECUTION BLOCKED"}
              </strong>
            </span>
          </div>

          {!isExecutionAllowed && policy && (
            <span className="font-mono text-[11px] text-rose-400">
              {policy.decision === "HUMAN_REVIEW"
                ? "⚠️ Human sign-off mandatory before recovery simulation"
                : "🚫 Policy engine has restricted execution for this incident"}
            </span>
          )}
        </div>
      </div>

      {error && (
        <div className="border-b border-signal-critical/30 bg-signal-critical/10 px-5 py-3 text-xs text-signal-critical">
          {error}
        </div>
      )}

      {/* Main Content Area */}
      <div className="p-5 space-y-6">
        {/* 4-Way Comparison Metric Grid */}
        <div>
          <div className="mb-2.5 flex items-center justify-between">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-ink-muted">
              Authoritative vs. Estimated vs. Measured Revenue
            </h3>
            <span className="text-[11px] font-mono text-ink-faint">
              Module 4 Baseline → Module 8 Proposal → Module 10 Actual Outcome
            </span>
          </div>

          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            {/* Revenue at Risk (Module 4) */}
            <div className="border border-base-border bg-base-surface p-3.5">
              <div className="text-[11px] font-mono uppercase text-ink-muted">1. Revenue at Risk</div>
              <div className="mt-1 text-lg font-bold font-mono text-ink">
                {formatInr(execution?.authoritativeRevenueAtRisk ?? incident?.revenueAtRisk)}
              </div>
              <div className="mt-1 text-[10px] text-ink-faint">Module 4 Detected Exposure</div>
            </div>

            {/* Authoritative Recoverable (Module 4) */}
            <div className="border border-base-border bg-base-surface p-3.5">
              <div className="text-[11px] font-mono uppercase text-ink-muted">2. Recoverable Bound</div>
              <div className="mt-1 text-lg font-bold font-mono text-blue-400">
                {formatInr(execution?.authoritativeRecoverableAmount ?? 0)}
              </div>
              <div className="mt-1 text-[10px] text-ink-faint">Module 4 Maximum Theoretical Cap</div>
            </div>

            {/* AI Estimated Recovery (Module 8) */}
            <div className="border border-base-border bg-base-surface p-3.5">
              <div className="flex items-center justify-between">
                <span className="text-[11px] font-mono uppercase text-ink-muted">3. AI Estimate</span>
                <span className="bg-purple-500/20 text-purple-300 px-1 py-0.2 text-[9px] font-mono font-bold rounded">
                  ESTIMATE
                </span>
              </div>
              <div className="mt-1 text-lg font-bold font-mono text-purple-400">
                {execution?.expectedRecoveryPercentage !== null && execution?.expectedRecoveryPercentage !== undefined
                  ? formatInr((execution.authoritativeRecoverableAmount * execution.expectedRecoveryPercentage) / 100)
                  : "—"}
              </div>
              <div className="mt-1 text-[10px] text-ink-faint">
                {execution?.expectedRecoveryPercentage
                  ? `~${execution.expectedRecoveryPercentage.toFixed(0)}% projected by Module 8`
                  : "AI Model Hypothesis"}
              </div>
            </div>

            {/* Actual Simulated Recovery (Module 10) */}
            <div className="border border-emerald-500/40 bg-emerald-950/20 p-3.5 shadow-sm">
              <div className="flex items-center justify-between">
                <span className="text-[11px] font-mono uppercase text-emerald-400 font-semibold">
                  4. Actual Simulated
                </span>
                <span className="bg-emerald-500/20 text-emerald-300 px-1.5 py-0.2 text-[9px] font-mono font-bold rounded border border-emerald-500/40">
                  MEASURED
                </span>
              </div>
              <div className="mt-1 text-xl font-black font-mono text-emerald-300">
                {formatInr(execution?.recoveredAmount ?? 0)}
              </div>
              <div className="mt-1 text-[10px] text-emerald-400/80 font-mono">
                {execution
                  ? `${execution.successfulRecoveries} payments recovered (${(
                      execution.metrics?.financialRecoveryRate ?? 0
                    ).toFixed(1)}%)`
                  : "Awaiting execution"}
              </div>
            </div>
          </div>
        </div>

        {/* Execution Details & Metrics */}
        {execution && (
          <div className="space-y-4">
            {/* Stopping Condition Banner */}
            {execution.stoppingReason && (
              <div
                className={`border p-3 flex items-start justify-between gap-4 ${
                  stoppingReasonLabel(execution.stoppingReason).color
                }`}
              >
                <div>
                  <div className="text-xs font-semibold font-mono">
                    STOPPING CONDITION: {stoppingReasonLabel(execution.stoppingReason).label}
                  </div>
                  <div className="mt-0.5 text-[11px] opacity-90">
                    {stoppingReasonLabel(execution.stoppingReason).desc}
                  </div>
                </div>
                <div className="text-right font-mono text-[11px] opacity-75 shrink-0">
                  Duration: {execution.metrics?.executionDurationSeconds ?? 0}s
                </div>
              </div>
            )}

            {/* Secondary KPI Bar */}
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-6 border border-base-border bg-base-surface p-3 text-center">
              <div>
                <div className="text-[10px] font-mono text-ink-muted">ELIGIBLE</div>
                <div className="mt-0.5 font-mono text-sm font-semibold text-ink">
                  {execution.eligibleTransactions}
                </div>
              </div>
              <div>
                <div className="text-[10px] font-mono text-ink-muted">ATTEMPTED</div>
                <div className="mt-0.5 font-mono text-sm font-semibold text-ink">
                  {execution.attemptedTransactions}
                </div>
              </div>
              <div>
                <div className="text-[10px] font-mono text-emerald-400">SUCCESSFUL</div>
                <div className="mt-0.5 font-mono text-sm font-bold text-emerald-400">
                  {execution.successfulRecoveries}
                </div>
              </div>
              <div>
                <div className="text-[10px] font-mono text-rose-400">FAILED</div>
                <div className="mt-0.5 font-mono text-sm font-bold text-rose-400">
                  {execution.failedRecoveries}
                </div>
              </div>
              <div>
                <div className="text-[10px] font-mono text-ink-muted">SKIPPED</div>
                <div className="mt-0.5 font-mono text-sm font-semibold text-ink-muted">
                  {execution.skippedTransactions}
                </div>
              </div>
              <div>
                <div className="text-[10px] font-mono text-emerald-400">RECOVERY RATE</div>
                <div className="mt-0.5 font-mono text-sm font-bold text-emerald-400">
                  {(execution.metrics?.recoveryRate ?? 0).toFixed(1)}%
                </div>
              </div>
            </div>

            {/* Enforced Policy Guardrail Limits */}
            <div className="border border-base-border bg-base-raised/40 p-3">
              <div className="text-[11px] font-semibold text-ink uppercase tracking-wider mb-2">
                Enforced Module 9 Policy Parameters
              </div>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs font-mono">
                <div className="bg-base-surface border border-base-border p-2">
                  <span className="text-ink-muted text-[10px] block">APPLIED RETRY LIMIT</span>
                  <span className="text-ink font-semibold">{execution.appliedRetryLimit ?? 3} attempts max</span>
                </div>
                <div className="bg-base-surface border border-base-border p-2">
                  <span className="text-ink-muted text-[10px] block">RETRY WINDOW</span>
                  <span className="text-ink font-semibold">{execution.appliedRetryWindow ?? 30} minutes</span>
                </div>
                <div className="bg-base-surface border border-base-border p-2">
                  <span className="text-ink-muted text-[10px] block">BACKOFF STRATEGY</span>
                  <span className="text-ink font-semibold">{execution.appliedBackoff ?? 30}s linear</span>
                </div>
                <div className="bg-base-surface border border-base-border p-2">
                  <span className="text-ink-muted text-[10px] block">SIMULATION ENGINE</span>
                  <span className="text-ink font-semibold">{execution.simulationVersion}</span>
                </div>
              </div>
            </div>

            {/* Transaction-Level Retry Attempt Timeline Table */}
            <div>
              <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
                <div className="flex items-center gap-2">
                  <h4 className="text-xs font-semibold uppercase tracking-wider text-ink">
                    Synthetic Attempt Timeline
                  </h4>
                  <span className="font-mono text-[11px] text-ink-muted">
                    ({filteredAttempts.length} of {execution.attempts.length} attempts)
                  </span>
                </div>

                {/* Filter Tabs */}
                <div className="flex items-center gap-1 text-[11px] font-mono">
                  {(["ALL", "RECOVERED", "FAILED", "SKIPPED"] as const).map((tab) => (
                    <button
                      key={tab}
                      onClick={() => setAttemptFilter(tab)}
                      className={`px-2 py-0.5 border text-[10px] transition-colors ${
                        attemptFilter === tab
                          ? "bg-base-raised text-ink border-ink-muted font-bold"
                          : "border-base-border text-ink-faint hover:text-ink"
                      }`}
                    >
                      {tab}
                    </button>
                  ))}
                </div>
              </div>

              {/* Table */}
              <div className="overflow-x-auto border border-base-border">
                <table className="w-full text-left font-mono text-xs">
                  <thead className="bg-base-raised text-[10px] uppercase text-ink-muted border-b border-base-border">
                    <tr>
                      <th className="px-3 py-2">Payment ID</th>
                      <th className="px-3 py-2">Attempt</th>
                      <th className="px-3 py-2">Strategy</th>
                      <th className="px-3 py-2">Amount</th>
                      <th className="px-3 py-2">Outcome</th>
                      <th className="px-3 py-2">Reason / Details</th>
                      <th className="px-3 py-2 text-right">Timestamp</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-base-border bg-base-surface text-ink">
                    {filteredAttempts.length === 0 ? (
                      <tr>
                        <td colSpan={7} className="px-3 py-6 text-center text-ink-faint">
                          No attempts match the selected filter.
                        </td>
                      </tr>
                    ) : (
                      filteredAttempts.map((attempt) => (
                        <tr
                          key={attempt.attemptId ?? `${attempt.paymentId}-${attempt.attemptNumber}`}
                          className="hover:bg-base-raised/50 transition-colors"
                        >
                          <td className="px-3 py-2 font-bold text-ink">
                            #{attempt.paymentId}
                          </td>
                          <td className="px-3 py-2">
                            <span className="bg-base-raised px-1.5 py-0.5 rounded text-[10px]">
                              Attempt {attempt.attemptNumber}
                            </span>
                          </td>
                          <td className="px-3 py-2 text-ink-muted text-[11px]">
                            {attempt.strategyType}
                          </td>
                          <td className="px-3 py-2 font-semibold">
                            {formatInr(attempt.amount)}
                          </td>
                          <td className="px-3 py-2">
                            <span
                              className={`border px-1.5 py-0.5 text-[10px] font-semibold ${outcomeBadge(
                                attempt.outcome
                              )}`}
                            >
                              {attempt.outcome}
                            </span>
                          </td>
                          <td className="px-3 py-2 text-ink-muted text-[11px] max-w-xs truncate">
                            {attempt.failureReason ?? "—"}
                          </td>
                          <td className="px-3 py-2 text-right text-ink-faint text-[10px]">
                            {new Date(attempt.attemptedAt).toLocaleTimeString()}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* Empty State when no execution has happened yet */}
        {!execution && (
          <div className="border border-dashed border-base-border bg-base-surface p-8 text-center">
            <div className="mx-auto flex h-10 w-10 items-center justify-center rounded-full bg-emerald-500/10 text-emerald-400">
              <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
            </div>
            <h3 className="mt-3 text-sm font-semibold text-ink">Ready for Policy-Governed Execution</h3>
            <p className="mx-auto mt-1 max-w-md text-xs text-ink-muted">
              Click &quot;Execute Recovery&quot; above to simulate bounded payment retries according to the policy
              limits established by Module 9.
            </p>
          </div>
        )}
      </div>

      {/* Safety Boundary Disclaimer Footer */}
      <div className="border-t border-base-border bg-base-raised/30 px-5 py-2.5 text-[11px] text-ink-faint flex flex-wrap items-center justify-between gap-2 font-mono">
        <span>
          🔒 SIMULATION ONLY · No real payment processing, Razorpay API calls, or monetary movement occur.
        </span>
        <span>Bounded by Module 4 Authoritative Financial Engine</span>
      </div>
    </div>
  );
}
