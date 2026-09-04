"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import {
  SafetyScenarioDto,
  SafetyScenarioResult,
  getSafetyScenarios,
  runSafetyScenario,
  runAllSafetyScenarios,
  SafetyScenarioType,
} from "../../lib/safety";

export default function SafetyLabPage() {
  const [scenarios, setScenarios] = useState<SafetyScenarioDto[]>([]);
  const [results, setResults] = useState<Record<string, SafetyScenarioResult>>({});
  const [loading, setLoading] = useState<boolean>(true);
  const [runningType, setRunningType] = useState<SafetyScenarioType | null>(null);
  const [runningAll, setRunningAll] = useState<boolean>(false);
  const [selectedCategory, setSelectedCategory] = useState<string>("ALL");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadScenarios();
  }, []);

  async function loadScenarios() {
    setLoading(true);
    setError(null);
    try {
      const data = await getSafetyScenarios();
      setScenarios(data);
    } catch (err) {
      console.error("Failed to load safety scenarios:", err);
      setError("Failed to connect to backend safety engine. Please verify the backend is running.");
    } finally {
      setLoading(false);
    }
  }

  async function handleRunScenario(type: SafetyScenarioType) {
    setRunningType(type);
    try {
      const res = await runSafetyScenario(type);
      setResults((prev) => ({ ...prev, [type]: res }));
    } catch (err: any) {
      console.error(`Failed to run scenario ${type}:`, err);
      alert(`Error running scenario ${type}: ` + (err.message || "Unknown error"));
    } finally {
      setRunningType(null);
    }
  }

  async function handleRunAll() {
    setRunningAll(true);
    try {
      const resList = await runAllSafetyScenarios();
      const map: Record<string, SafetyScenarioResult> = {};
      for (const r of resList) {
        map[r.scenario] = r;
      }
      setResults(map);
    } catch (err: any) {
      console.error("Failed to run all scenarios:", err);
      alert("Error executing safety test suite: " + (err.message || "Unknown error"));
    } finally {
      setRunningAll(false);
    }
  }

  const categories = ["ALL", "AI_SAFETY", "POLICY_GATE", "EXECUTION_SAFETY", "AUDIT_INTEGRITY"];

  const filteredScenarios = scenarios.filter((s) => {
    if (selectedCategory === "ALL") return true;
    return s.category === selectedCategory;
  });

  const totalRun = Object.keys(results).length;
  const passedCount = Object.values(results).filter((r) => r.passed).length;
  const allPassed = totalRun === scenarios.length && scenarios.length > 0 && passedCount === scenarios.length;

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 p-6 md:p-10 font-sans">
      <div className="max-w-7xl mx-auto space-y-8">
        {/* Navigation Breadcrumb */}
        <div className="flex items-center space-x-2 text-xs text-slate-400">
          <Link href="/dashboard" className="hover:text-blue-400 transition-colors">
            Command Center
          </Link>
          <span>/</span>
          <span className="text-slate-200">Safety & Failure Lab</span>
        </div>

        {/* Header Banner */}
        <div className="bg-slate-900/90 border border-slate-800 rounded-xl p-6 md:p-8 backdrop-blur-md shadow-2xl relative overflow-hidden">
          <div className="absolute top-0 right-0 w-96 h-96 bg-blue-500/5 rounded-full blur-3xl pointer-events-none" />
          <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6 relative z-10">
            <div>
              <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 mb-3">
                <span className="h-2 w-2 rounded-full bg-emerald-400 animate-pulse" />
                Resilience & Failure Mode Test Harness (Module 12)
              </div>
              <h1 className="text-3xl font-extrabold text-white tracking-tight">
                Sentinel Safety & Failure Lab
              </h1>
              <p className="text-slate-400 text-sm mt-2 max-w-3xl leading-relaxed">
                Controlled, deterministic failure scenarios deliberately exercising AI outages, malformed LLM responses, policy rejections, human escalation gates, circuit breakers, and cryptographic audit tampering.
              </p>
            </div>

            <div className="flex flex-wrap items-center gap-3">
              <button
                onClick={handleRunAll}
                disabled={runningAll || loading}
                className="px-5 py-2.5 rounded-lg bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white font-medium text-sm transition-all shadow-lg shadow-blue-600/20 flex items-center gap-2"
              >
                {runningAll ? (
                  <>
                    <svg className="animate-spin h-4 w-4 text-white" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                    </svg>
                    Running All 12 Scenarios...
                  </>
                ) : (
                  <>
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z" />
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                    Run Full Safety Test Suite
                  </>
                )}
              </button>
            </div>
          </div>
        </div>

        {/* Safety Scoreboard */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-5">
            <div className="text-xs uppercase font-medium text-slate-400">Architectural Invariant</div>
            <div className="text-xl font-bold text-white mt-1">AI Proposes → Policy Decides</div>
            <div className="text-xs text-emerald-400 mt-1 flex items-center gap-1 font-medium">
              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
              Execution Obeys Policy
            </div>
          </div>

          <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-5">
            <div className="text-xs uppercase font-medium text-slate-400">Design Posture</div>
            <div className="text-xl font-bold text-emerald-400 mt-1">Fail Closed</div>
            <div className="text-xs text-slate-400 mt-1">Zero autonomous bypass on error</div>
          </div>

          <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-5">
            <div className="text-xs uppercase font-medium text-slate-400">Test Suite Execution</div>
            <div className="text-xl font-bold text-white mt-1">
              {totalRun} / {scenarios.length} Executed
            </div>
            <div className="text-xs text-slate-400 mt-1">
              {passedCount} passed ({scenarios.length > 0 ? Math.round((passedCount / scenarios.length) * 100) : 0}%)
            </div>
          </div>

          <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-5">
            <div className="text-xs uppercase font-medium text-slate-400">Safety Invariants Tested</div>
            <div className="text-xl font-bold text-blue-400 mt-1">15 Invariants</div>
            <div className="text-xs text-emerald-400 mt-1">100% Enforced in Code</div>
          </div>
        </div>

        {/* Category Filters */}
        <div className="flex flex-wrap items-center gap-2 border-b border-slate-800 pb-4">
          {categories.map((cat) => (
            <button
              key={cat}
              onClick={() => setSelectedCategory(cat)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold tracking-wide transition-all ${
                selectedCategory === cat
                  ? "bg-blue-600/20 text-blue-400 border border-blue-500/40"
                  : "bg-slate-900 text-slate-400 border border-slate-800 hover:text-slate-200"
              }`}
            >
              {cat.replace("_", " ")}
            </button>
          ))}
        </div>

        {/* Error Notification */}
        {error && (
          <div className="bg-rose-500/10 border border-rose-500/30 text-rose-400 p-4 rounded-xl text-sm flex items-center gap-3">
            <svg className="w-5 h-5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
            <div>{error}</div>
          </div>
        )}

        {/* Scenarios Grid */}
        {loading ? (
          <div className="text-center py-20 text-slate-400 flex flex-col items-center gap-3">
            <svg className="animate-spin h-8 w-8 text-blue-500" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
            </svg>
            <span>Loading safety scenario definitions...</span>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {filteredScenarios.map((sc) => {
              const res = results[sc.type];
              const isRunning = runningType === sc.type;

              return (
                <div
                  key={sc.type}
                  className={`bg-slate-900/80 border rounded-xl p-6 transition-all shadow-lg flex flex-col justify-between ${
                    res
                      ? res.passed
                        ? "border-emerald-500/40 bg-slate-900/90"
                        : "border-rose-500/40 bg-slate-900/90"
                      : "border-slate-800 hover:border-slate-700"
                  }`}
                >
                  <div>
                    {/* Scenario Header */}
                    <div className="flex items-start justify-between gap-4 mb-3">
                      <div>
                        <div className="flex items-center gap-2 mb-1">
                          <span className="px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider bg-slate-800 text-slate-300 border border-slate-700">
                            {sc.category.replace("_", " ")}
                          </span>
                          {res && (
                            <span
                              className={`px-2 py-0.5 rounded text-[10px] font-bold tracking-wider ${
                                res.passed
                                  ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/30"
                                  : "bg-rose-500/10 text-rose-400 border border-rose-500/30"
                              }`}
                            >
                              {res.status}
                            </span>
                          )}
                        </div>
                        <h3 className="text-lg font-bold text-white">{sc.name}</h3>
                      </div>

                      <button
                        onClick={() => handleRunScenario(sc.type)}
                        disabled={isRunning || runningAll}
                        className="px-3.5 py-1.5 rounded-lg text-xs font-semibold bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-200 transition-all disabled:opacity-50 flex items-center gap-1.5 flex-shrink-0"
                      >
                        {isRunning ? (
                          <>
                            <svg className="animate-spin h-3.5 w-3.5 text-blue-400" fill="none" viewBox="0 0 24 24">
                              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                            </svg>
                            Testing...
                          </>
                        ) : (
                          <>
                            <svg className="w-3.5 h-3.5 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z" />
                            </svg>
                            Run Scenario
                          </>
                        )}
                      </button>
                    </div>

                    <p className="text-xs text-slate-400 leading-relaxed mb-4">{sc.description}</p>

                    <div className="bg-slate-950/70 border border-slate-800/80 rounded-lg p-3.5 space-y-2 mb-4">
                      <div className="text-[11px] text-blue-400 font-mono font-medium">
                        🛡️ {sc.safetyProperty}
                      </div>
                      <div className="text-[11px] text-slate-300">
                        <span className="text-slate-400 font-semibold">Expected:</span> {sc.expectedBehavior}
                      </div>
                    </div>
                  </div>

                  {/* Result Details if executed */}
                  {res && (
                    <div className="mt-2 pt-4 border-t border-slate-800 space-y-2.5">
                      <div className="text-[11px] text-slate-300">
                        <span className="text-emerald-400 font-semibold">✓ Observed Behavior:</span>{" "}
                        <span className="text-slate-200">{res.observedBehavior}</span>
                      </div>

                      <div className="flex flex-wrap items-center gap-2 text-[11px] font-mono pt-1">
                        {res.policyDecision && (
                          <span className="px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
                            Policy: <strong className="text-white">{res.policyDecision}</strong>
                          </span>
                        )}
                        {res.recoveryStatus && (
                          <span className="px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
                            Execution: <strong className="text-white">{res.recoveryStatus}</strong>
                          </span>
                        )}
                        {res.stoppingReason && (
                          <span className="px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
                            Stop: <strong className="text-white">{res.stoppingReason}</strong>
                          </span>
                        )}
                        {res.auditVerificationValid !== null && (
                          <span className="px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
                            Audit Verification:{" "}
                            <strong className={res.auditVerificationValid ? "text-emerald-400" : "text-amber-400"}>
                              {res.auditVerificationValid ? "VERIFIED" : "TAMPER_DETECTED"}
                            </strong>
                          </span>
                        )}
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
