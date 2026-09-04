"use client";

import { useState, useEffect, useCallback } from "react";
import {
  evaluatePolicy,
  fetchLatestPolicyEvaluation,
  type PolicyEvaluationResponse,
  type PolicyDecision,
  type PolicyCheck,
  type AppliedLimit,
  type PolicyCheckResult,
} from "@/lib/policy";
import type { Incident } from "@/lib/monitoring";
import type { RecoveryStrategyResponse } from "@/lib/recoveryStrategy";

interface PolicyGatePanelProps {
  incidentId: number;
  incident?: Incident;
  recoveryStrategy?: RecoveryStrategyResponse | null;
  onPolicyEvaluated?: (evaluation: PolicyEvaluationResponse) => void;
}

function decisionBadge(decision: PolicyDecision) {
  switch (decision) {
    case "APPROVED":
      return {
        label: "APPROVED",
        badge: "bg-emerald-500/15 text-emerald-400 border-emerald-500/30",
        dot: "bg-emerald-400",
        border: "border-emerald-500/30",
        bg: "bg-emerald-950/20",
        icon: "✓",
      };
    case "APPROVED_WITH_LIMITS":
      return {
        label: "APPROVED WITH LIMITS",
        badge: "bg-amber-500/15 text-amber-400 border-amber-500/30",
        dot: "bg-amber-400",
        border: "border-amber-500/30",
        bg: "bg-amber-950/20",
        icon: "⚡",
      };
    case "HUMAN_REVIEW":
      return {
        label: "HUMAN REVIEW REQUIRED",
        badge: "bg-blue-500/15 text-blue-400 border-blue-500/30",
        dot: "bg-blue-400",
        border: "border-blue-500/30",
        bg: "bg-blue-950/20",
        icon: "👤",
      };
    case "REJECTED":
      return {
        label: "REJECTED / BLOCKED",
        badge: "bg-rose-500/15 text-rose-400 border-rose-500/30",
        dot: "bg-rose-400",
        border: "border-rose-500/30",
        bg: "bg-rose-950/20",
        icon: "✕",
      };
    default:
      return {
        label: decision,
        badge: "bg-base-raised text-ink-muted border-base-border",
        dot: "bg-ink-muted",
        border: "border-base-border",
        bg: "bg-base-raised",
        icon: "?",
      };
  }
}

function checkResultBadge(result: PolicyCheckResult) {
  switch (result) {
    case "PASS":
      return "bg-emerald-500/15 text-emerald-400 border-emerald-500/30";
    case "WARN":
      return "bg-amber-500/15 text-amber-400 border-amber-500/30";
    case "FAIL":
      return "bg-rose-500/15 text-rose-400 border-rose-500/30";
    default:
      return "bg-base-raised text-ink-muted border-base-border";
  }
}

function severityBadge(severity: string) {
  switch (severity?.toUpperCase()) {
    case "CRITICAL":
      return "text-rose-400 font-semibold";
    case "MAJOR":
      return "text-amber-400";
    case "MINOR":
      return "text-blue-400";
    default:
      return "text-ink-muted";
  }
}

export function PolicyGatePanel({
  incidentId,
  incident,
  recoveryStrategy,
  onPolicyEvaluated,
}: PolicyGatePanelProps) {
  const [evaluation, setEvaluation] = useState<PolicyEvaluationResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadExisting = useCallback(async () => {
    setInitialLoading(true);
    try {
      const existing = await fetchLatestPolicyEvaluation(incidentId);
      if (existing) {
        setEvaluation(existing);
      }
    } catch {
      // Silently handle 404
    } finally {
      setInitialLoading(false);
    }
  }, [incidentId]);

  useEffect(() => {
    loadExisting();
  }, [loadExisting]);

  const handleEvaluate = async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await evaluatePolicy(incidentId);
      setEvaluation(result);
      if (onPolicyEvaluated) {
        onPolicyEvaluated(result);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to execute policy gate evaluation");
    } finally {
      setLoading(false);
    }
  };

  const decisionMeta = evaluation ? decisionBadge(evaluation.decision) : null;

  return (
    <div className="rounded-xl border border-base-border bg-base-card shadow-lg p-6 space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-base-border/50 pb-5">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-indigo-500/10 border border-indigo-500/20 text-indigo-400 font-bold text-lg">
            🛡️
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-xl font-bold text-ink-primary tracking-tight">Policy Gate & Guardrail Engine</h2>
              <span className="text-[11px] font-semibold px-2 py-0.5 rounded-full bg-indigo-500/15 text-indigo-400 border border-indigo-500/30">
                MODULE 9
              </span>
              {evaluation && (
                <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-base-raised text-ink-muted border border-base-border">
                  {evaluation.evaluatorVersion}
                </span>
              )}
            </div>
            <p className="text-xs text-ink-muted mt-0.5">
              Deterministic, non-AI gatekeeper enforcing hard financial and operational safety limits
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={handleEvaluate}
            disabled={loading}
            className="flex items-center gap-2 px-4 py-2 text-xs font-semibold rounded-lg bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white transition-all shadow-sm active:scale-95"
          >
            {loading ? (
              <>
                <svg className="animate-spin h-3.5 w-3.5 text-white" viewBox="0 0 24 24" fill="none">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                </svg>
                Evaluating Policies...
              </>
            ) : evaluation ? (
              <>
                <span>🔄</span> Re-evaluate Policy Gate
              </>
            ) : (
              <>
                <span>🛡️</span> Run Policy Gate Check
              </>
            )}
          </button>
        </div>
      </div>

      {/* Error state */}
      {error && (
        <div className="rounded-lg border border-rose-500/30 bg-rose-500/10 p-4 text-xs text-rose-300 flex items-start gap-2.5">
          <span className="text-rose-400 font-bold">✕</span>
          <div className="space-y-1">
            <div className="font-semibold text-rose-200">Policy Evaluation Error</div>
            <div>{error}</div>
          </div>
        </div>
      )}

      {/* Initial Loading */}
      {initialLoading && (
        <div className="py-12 text-center text-xs text-ink-muted flex flex-col items-center justify-center gap-2">
          <div className="h-5 w-5 border-2 border-indigo-400 border-t-transparent rounded-full animate-spin" />
          <span>Loading policy gate records...</span>
        </div>
      )}

      {/* Empty state: No evaluation performed yet */}
      {!initialLoading && !evaluation && !error && (
        <div className="rounded-xl border border-dashed border-base-border p-8 text-center space-y-3 bg-base-raised/30">
          <div className="text-3xl">⚖️</div>
          <h3 className="text-sm font-semibold text-ink-primary">No Policy Evaluation Performed Yet</h3>
          <p className="text-xs text-ink-muted max-w-md mx-auto">
            Run the deterministic Policy Gate to test the proposed Module 8 recovery strategy against hard financial bounds, retry limits, merchant protections, and safety invariants.
          </p>
          <button
            onClick={handleEvaluate}
            disabled={loading}
            className="inline-flex items-center gap-2 px-4 py-2 text-xs font-semibold rounded-lg bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white transition-all"
          >
            <span>🛡️</span> Run Policy Gate Check Now
          </button>
        </div>
      )}

      {/* Main Evaluated Content */}
      {evaluation && decisionMeta && (
        <div className="space-y-6">
          {/* Decision Hero Banner */}
          <div className={`rounded-xl border ${decisionMeta.border} ${decisionMeta.bg} p-5 space-y-4`}>
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div className="flex items-center gap-3">
                <span className={`inline-flex items-center justify-center h-8 w-8 rounded-full font-bold text-sm border ${decisionMeta.badge}`}>
                  {decisionMeta.icon}
                </span>
                <div>
                  <div className="text-[11px] font-semibold text-ink-muted uppercase tracking-wider">
                    Policy Gate Outcome
                  </div>
                  <div className="text-lg font-extrabold tracking-tight text-ink-primary flex items-center gap-2.5">
                    <span className={`px-2.5 py-0.5 rounded-full text-xs font-bold border ${decisionMeta.badge}`}>
                      {decisionMeta.label}
                    </span>
                    <span className="text-xs font-normal text-ink-muted">
                      Evaluated {new Date(evaluation.evaluatedAt).toLocaleTimeString()}
                    </span>
                  </div>
                </div>
              </div>

              {/* Execution status badge */}
              <div className="flex items-center gap-2 self-start sm:self-auto">
                <div
                  className={`px-3 py-1.5 rounded-lg border text-xs font-bold flex items-center gap-2 ${
                    evaluation.executionAllowed
                      ? "bg-emerald-500/15 text-emerald-400 border-emerald-500/30"
                      : "bg-rose-500/15 text-rose-400 border-rose-500/30"
                  }`}
                >
                  <span className={`h-2 w-2 rounded-full ${evaluation.executionAllowed ? "bg-emerald-400 animate-pulse" : "bg-rose-400"}`} />
                  {evaluation.executionAllowed ? "RECOVERY EXECUTION PERMITTED" : "EXECUTION BLOCKED BY POLICY"}
                </div>
              </div>
            </div>

            {/* Summary & Next Step */}
            <div className="text-xs text-ink-secondary space-y-2 border-t border-white/5 pt-3">
              <p className="font-medium text-ink-primary">{evaluation.summary}</p>
              {evaluation.executionBlockedReason && (
                <div className="flex items-start gap-2 text-rose-300 bg-rose-950/40 p-2.5 rounded-lg border border-rose-500/20">
                  <span className="font-bold">⛔ Reason:</span>
                  <span>{evaluation.executionBlockedReason}</span>
                </div>
              )}
              <div className="flex items-center gap-2 text-ink-muted">
                <span className="font-semibold text-indigo-300">Next Safe Step:</span>
                <span>{evaluation.nextStep}</span>
              </div>
            </div>
          </div>

          {/* Metric Cards Grid */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <div className="rounded-lg border border-base-border bg-base-raised/50 p-3 space-y-1">
              <div className="text-[11px] font-medium text-ink-muted">Execution Authorization</div>
              <div className={`text-sm font-bold ${evaluation.executionAllowed ? "text-emerald-400" : "text-rose-400"}`}>
                {evaluation.executionAllowed ? "Allowed" : "Blocked"}
              </div>
              <div className="text-[10px] text-ink-muted">
                {evaluation.requiredHumanApproval ? "Human sign-off required" : "Autonomous bounded"}
              </div>
            </div>

            <div className="rounded-lg border border-base-border bg-base-raised/50 p-3 space-y-1">
              <div className="text-[11px] font-medium text-ink-muted">Human Commander</div>
              <div className={`text-sm font-bold ${evaluation.requiredHumanApproval ? "text-amber-400" : "text-emerald-400"}`}>
                {evaluation.requiredHumanApproval ? "MANDATORY" : "NOT REQUIRED"}
              </div>
              <div className="text-[10px] text-ink-muted">
                {evaluation.requiredHumanApproval ? "Approval required" : "Pre-authorized"}
              </div>
            </div>

            <div className="rounded-lg border border-base-border bg-base-raised/50 p-3 space-y-1">
              <div className="text-[11px] font-medium text-ink-muted">Incident Risk Level</div>
              <div className="text-sm font-bold text-ink-primary">
                {evaluation.riskLevel}
              </div>
              <div className="text-[10px] text-ink-muted">Strategy risk profile</div>
            </div>

            <div className="rounded-lg border border-base-border bg-base-raised/50 p-3 space-y-1">
              <div className="text-[11px] font-medium text-ink-muted">Policy Compliance Score</div>
              <div className="text-sm font-bold text-indigo-400">
                {evaluation.policyScore.toFixed(0)} / 100
              </div>
              <div className="text-[10px] text-ink-muted">Deterministic scoring</div>
            </div>
          </div>

          {/* Applied Limits (Clamped parameters) */}
          {evaluation.appliedLimits && evaluation.appliedLimits.length > 0 && (
            <div className="rounded-lg border border-amber-500/30 bg-amber-950/20 p-4 space-y-3">
              <div className="flex items-center gap-2 text-xs font-bold text-amber-300">
                <span>⚡</span> Enforced Policy Clamping Limits ({evaluation.appliedLimits.length})
              </div>
              <p className="text-xs text-ink-muted">
                Proposed parameters exceeded safety guardrails and were deterministically clamped to safe boundaries:
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                {evaluation.appliedLimits.map((limit, idx) => (
                  <div key={idx} className="rounded-lg border border-amber-500/20 bg-base-card/60 p-3 space-y-1 text-xs">
                    <div className="flex items-center justify-between">
                      <span className="font-mono text-ink-primary font-semibold">{limit.parameter}</span>
                      <span className="px-1.5 py-0.5 rounded bg-amber-500/15 text-amber-300 text-[10px] font-mono border border-amber-500/30">
                        {limit.proposedValue} → {limit.enforcedValue}
                      </span>
                    </div>
                    <div className="text-ink-muted text-[11px]">{limit.reason}</div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Violations & Warnings */}
          {(evaluation.violations.length > 0 || evaluation.warnings.length > 0) && (
            <div className="space-y-3">
              {evaluation.violations.length > 0 && (
                <div className="rounded-lg border border-rose-500/30 bg-rose-950/20 p-4 space-y-2">
                  <div className="text-xs font-bold text-rose-300 flex items-center gap-2">
                    <span>⛔</span> Mandatory Policy Violations ({evaluation.violations.length})
                  </div>
                  <ul className="space-y-1 text-xs text-rose-200 list-disc list-inside">
                    {evaluation.violations.map((v, i) => (
                      <li key={i}>{v}</li>
                    ))}
                  </ul>
                </div>
              )}

              {evaluation.warnings.length > 0 && (
                <div className="rounded-lg border border-amber-500/30 bg-amber-950/20 p-4 space-y-2">
                  <div className="text-xs font-bold text-amber-300 flex items-center gap-2">
                    <span>⚠️</span> Policy Warnings & Constraints ({evaluation.warnings.length})
                  </div>
                  <ul className="space-y-1 text-xs text-amber-200 list-disc list-inside">
                    {evaluation.warnings.map((w, i) => (
                      <li key={i}>{w}</li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}

          {/* Policy Checks Detail Table */}
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-xs font-bold uppercase tracking-wider text-ink-muted flex items-center gap-2">
                <span>📋</span> Deterministic Policy Checks Breakdown ({evaluation.policyChecks.length})
              </h3>
            </div>

            <div className="overflow-x-auto rounded-lg border border-base-border">
              <table className="w-full text-left text-xs border-collapse">
                <thead>
                  <tr className="bg-base-raised/70 border-b border-base-border text-ink-muted font-semibold">
                    <th className="p-3">Policy ID</th>
                    <th className="p-3">Policy Name</th>
                    <th className="p-3">Result</th>
                    <th className="p-3">Severity</th>
                    <th className="p-3">Observed vs Allowed</th>
                    <th className="p-3">Reason / Details</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-base-border/50">
                  {evaluation.policyChecks.map((check, idx) => (
                    <tr key={idx} className="hover:bg-base-raised/30 transition-colors">
                      <td className="p-3 font-mono font-semibold text-indigo-300 whitespace-nowrap">
                        {check.policyId}
                      </td>
                      <td className="p-3 font-medium text-ink-primary whitespace-nowrap">
                        {check.policyName}
                      </td>
                      <td className="p-3 whitespace-nowrap">
                        <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold border ${checkResultBadge(check.result)}`}>
                          {check.result}
                        </span>
                      </td>
                      <td className="p-3 whitespace-nowrap">
                        <span className={`text-[11px] ${severityBadge(check.severity)}`}>
                          {check.severity}
                        </span>
                      </td>
                      <td className="p-3 text-ink-muted text-[11px]">
                        <div><span className="text-ink-secondary font-mono">Obs:</span> {check.observedValue}</div>
                        {check.allowedValue && (
                          <div><span className="text-indigo-400 font-mono">Req:</span> {check.allowedValue}</div>
                        )}
                      </td>
                      <td className="p-3 text-ink-secondary text-[11px]">
                        {check.reason}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Mandatory Policy Disclaimer */}
          <div className="rounded-lg border border-indigo-500/20 bg-indigo-950/15 p-4 text-xs text-indigo-300 space-y-1.5">
            <div className="font-bold flex items-center gap-1.5 text-indigo-200">
              <span>⚖️</span> Policy Gate Authority Disclaimer
            </div>
            <p className="text-ink-muted leading-relaxed">
              {evaluation.advisoryDisclaimer}
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
