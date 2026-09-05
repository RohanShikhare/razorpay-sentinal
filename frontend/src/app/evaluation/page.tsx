"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import {
  EvaluationRunResponse,
  EvaluationScenarioDescriptorDto,
  IncidentEvaluationResultDto,
  getEvaluationScenarios,
  getLatestEvaluationRun,
  runEvaluation,
} from "../../lib/evaluation";

export default function EvaluationBenchmarkPage() {
  const [scenarios, setScenarios] = useState<EvaluationScenarioDescriptorDto[]>([]);
  const [latestRun, setLatestRun] = useState<EvaluationRunResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [running, setRunning] = useState<boolean>(false);
  const [scenarioCount, setScenarioCount] = useState<number>(4);
  const [seed, setSeed] = useState<number>(42);
  const [includeBaseline, setIncludeBaseline] = useState<boolean>(true);
  const [selectedIncident, setSelectedIncident] = useState<IncidentEvaluationResultDto | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadInitialData();
  }, []);

  async function loadInitialData() {
    setLoading(true);
    setError(null);
    try {
      const [scenariosData, latestRunData] = await Promise.allSettled([
        getEvaluationScenarios(),
        getLatestEvaluationRun(),
      ]);

      if (scenariosData.status === "fulfilled") {
        setScenarios(scenariosData.value);
      }
      if (latestRunData.status === "fulfilled" && latestRunData.value) {
        setLatestRun(latestRunData.value);
        if (latestRunData.value.incidents && latestRunData.value.incidents.length > 0) {
          setSelectedIncident(latestRunData.value.incidents[0]);
        }
      }
    } catch (err: any) {
      console.error("Failed to load evaluation data:", err);
      setError("Failed to connect to backend evaluation engine. Please ensure backend is active.");
    } finally {
      setLoading(false);
    }
  }

  async function handleRunEvaluation() {
    setRunning(true);
    setError(null);
    try {
      const runResult = await runEvaluation({
        scenarioCount,
        seed,
        includeBaseline,
      });
      setLatestRun(runResult);
      if (runResult.incidents && runResult.incidents.length > 0) {
        setSelectedIncident(runResult.incidents[0]);
      }
    } catch (err: any) {
      console.error("Failed to run evaluation benchmark:", err);
      setError("Evaluation execution failed: " + (err.message || "Unknown error"));
    } finally {
      setRunning(false);
    }
  }

  const formatCurrency = (val: number | null | undefined) => {
    if (val === null || val === undefined) return "₹0.00";
    return new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency: "INR",
      maximumFractionDigits: 2,
    }).format(val);
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 p-6 md:p-10 font-sans">
      <div className="max-w-7xl mx-auto space-y-8">
        {/* Navigation Breadcrumbs */}
        <div className="flex items-center space-x-2 text-xs text-slate-400">
          <Link href="/command-center" className="hover:text-blue-400 transition-colors">
            Command Center
          </Link>
          <span>/</span>
          <Link href="/safety" className="hover:text-blue-400 transition-colors">
            Safety Lab
          </Link>
          <span>/</span>
          <span className="text-slate-200">Evaluation & Benchmarking</span>
        </div>

        {/* Header Hero Banner */}
        <div className="bg-slate-900/90 border border-slate-800 rounded-xl p-6 md:p-8 backdrop-blur-md shadow-2xl relative overflow-hidden">
          <div className="absolute top-0 right-0 w-96 h-96 bg-purple-500/5 rounded-full blur-3xl pointer-events-none" />
          <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6 relative z-10">
            <div>
              <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full text-xs font-semibold bg-purple-500/10 text-purple-300 border border-purple-500/20 mb-3">
                <span className="h-2 w-2 rounded-full bg-purple-400 animate-pulse" />
                Revenue Recovery Benchmark Engine
              </div>
              <h1 className="text-3xl font-extrabold text-white tracking-tight">
                Sentinel Evaluation & Benchmarking Engine
              </h1>
              <p className="text-slate-400 text-sm mt-2 max-w-3xl leading-relaxed">
                Empirical, reproducible evaluation suite measuring Sentinel&apos;s financial recovery efficacy,
                time-to-detect latency, root-cause accuracy, policy gate precision, and zero-bypass safety invariants.
              </p>
            </div>

            {/* Benchmark Run Control Toolbar */}
            <div className="bg-slate-950/80 border border-slate-800 p-4 rounded-xl flex flex-wrap items-center gap-4">
              <div className="flex flex-col">
                <label className="text-[10px] uppercase font-bold text-slate-400 tracking-wider">
                  Scenarios
                </label>
                <select
                  value={scenarioCount}
                  onChange={(e) => setScenarioCount(Number(e.target.value))}
                  disabled={running}
                  className="bg-slate-900 border border-slate-700 text-white rounded px-2.5 py-1.5 text-xs font-medium focus:outline-none focus:border-purple-500"
                >
                  <option value={1}>1 Scenario (Fast)</option>
                  <option value={2}>2 Scenarios</option>
                  <option value={4}>4 Scenarios (Full Suite)</option>
                  <option value={8}>8 Scenarios (Extended)</option>
                </select>
              </div>

              <div className="flex flex-col">
                <label className="text-[10px] uppercase font-bold text-slate-400 tracking-wider">
                  Seed (Reproducibility)
                </label>
                <input
                  type="number"
                  value={seed}
                  onChange={(e) => setSeed(Number(e.target.value))}
                  disabled={running}
                  className="bg-slate-900 border border-slate-700 text-white rounded px-2.5 py-1.5 text-xs font-mono w-24 focus:outline-none focus:border-purple-500"
                />
              </div>

              <div className="flex items-center gap-2 pt-3">
                <input
                  type="checkbox"
                  id="includeBaseline"
                  checked={includeBaseline}
                  onChange={(e) => setIncludeBaseline(e.target.checked)}
                  disabled={running}
                  className="rounded border-slate-700 bg-slate-900 text-purple-600 focus:ring-purple-500 h-4 w-4"
                />
                <label htmlFor="includeBaseline" className="text-xs text-slate-300 font-medium">
                  Compare Baselines
                </label>
              </div>

              <button
                onClick={handleRunEvaluation}
                disabled={running || loading}
                className="px-5 py-2 rounded-lg bg-purple-600 hover:bg-purple-500 disabled:opacity-50 text-white font-medium text-xs transition-all shadow-lg shadow-purple-600/20 flex items-center gap-2"
              >
                {running ? (
                  <>
                    <svg className="animate-spin h-4 w-4 text-white" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                    </svg>
                    Evaluating {scenarioCount} Scenarios...
                  </>
                ) : (
                  <>
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                    </svg>
                    Run Evaluation Benchmark
                  </>
                )}
              </button>
            </div>
          </div>

          {/* Active Run Metadata Bar */}
          {latestRun && (
            <div className="mt-6 pt-4 border-t border-slate-800/80 flex flex-wrap items-center justify-between gap-4 text-xs text-slate-400">
              <div className="flex items-center gap-3">
                <span className="font-mono bg-slate-800 px-2 py-0.5 rounded text-purple-300 border border-slate-700">
                  Run: {latestRun.runId}
                </span>
                <span className="font-mono bg-slate-800/50 px-2 py-0.5 rounded text-slate-300">
                  Engine: {latestRun.evaluatorVersion}
                </span>
                <span className="text-slate-400">
                  Seed: <span className="font-mono text-slate-200">{latestRun.seed}</span>
                </span>
                {latestRun.startedAt && latestRun.completedAt && (
                  <span className="text-slate-400">
                    Duration:{" "}
                    <span className="font-mono text-slate-200">
                      {Math.max(
                        0,
                        new Date(latestRun.completedAt).getTime() -
                          new Date(latestRun.startedAt).getTime()
                      )}{" "}
                      ms
                    </span>
                  </span>
                )}
              </div>
              <div className="flex items-center gap-2">
                <span className="text-slate-500">Status:</span>
                <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                  {latestRun.status}
                </span>
              </div>
            </div>
          )}
        </div>

        {error && (
          <div className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-300 text-sm flex items-center gap-3">
            <svg className="w-5 h-5 flex-shrink-0 text-rose-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
            <span>{error}</span>
          </div>
        )}

        {/* Empty State Banner */}
        {!latestRun && !loading && (
          <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-8 text-center text-slate-400 space-y-3">
            <p className="text-sm">No evaluation benchmark run has been recorded yet.</p>
            <p className="text-xs text-slate-500">
              Configure scenarios above and click <strong className="text-purple-400">Run Evaluation Benchmark</strong> to execute a reproducible benchmark run.
            </p>
          </div>
        )}

        {/* Hero KPI Metric Cards */}
        {latestRun && latestRun.financial && (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
            <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-5 shadow-lg">
              <div className="text-[11px] uppercase font-bold text-slate-400 tracking-wider">
                Overall Sentinel Score
              </div>
              <div className="text-2xl font-black text-purple-400 mt-2">
                {latestRun.overallSentinelScore != null ? latestRun.overallSentinelScore.toFixed(1) : "0.0"}{" "}
                <span className="text-xs text-slate-500 font-normal">/ 100</span>
              </div>
              <div className="text-[11px] text-emerald-400 mt-1 font-medium">
                Weighted across 4 dimensions
              </div>
            </div>

            <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-5 shadow-lg">
              <div className="text-[11px] uppercase font-bold text-slate-400 tracking-wider">
                Sentinel Recovered
              </div>
              <div className="text-2xl font-black text-emerald-400 mt-2">
                {formatCurrency(latestRun.financial?.totalRecoveredRevenue)}
              </div>
              <div className="text-[11px] text-slate-400 mt-1">
                of {formatCurrency(latestRun.financial?.totalRecoverableRevenue)} recoverable
              </div>
            </div>

            <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-5 shadow-lg">
              <div className="text-[11px] uppercase font-bold text-slate-400 tracking-wider">
                Overall Recovery Rate
              </div>
              <div className="text-2xl font-black text-cyan-400 mt-2">
                {latestRun.financial?.financialRecoveryRate != null ? latestRun.financial.financialRecoveryRate.toFixed(1) : "0.0"}%
              </div>
              <div className="text-[11px] text-slate-400 mt-1">
                Net Uplift: {formatCurrency(latestRun.financial?.recoveryUpliftVsNaiveRetry)}
              </div>
            </div>

            <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-5 shadow-lg">
              <div className="text-[11px] uppercase font-bold text-slate-400 tracking-wider">
                Uplift vs Naive Retry
              </div>
              <div className="text-2xl font-black text-amber-400 mt-2">
                +{formatCurrency(latestRun.financial?.recoveryUpliftVsNaiveRetry)}
              </div>
              <div className="text-[11px] text-slate-400 mt-1">
                vs ₹0 on No Recovery
              </div>
            </div>

            <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-5 shadow-lg">
              <div className="text-[11px] uppercase font-bold text-slate-400 tracking-wider">
                Safety Invariant Score
              </div>
              <div className="text-2xl font-black text-emerald-400 mt-2">
                {latestRun.safety?.safetyScore != null ? latestRun.safety.safetyScore.toFixed(0) : "100"}{" "}
                <span className="text-xs text-slate-500 font-normal">/ 100</span>
              </div>
              <div className="text-[11px] text-emerald-400 mt-1 font-medium">
                {latestRun.safety?.policyBypasses ?? 0} Policy Bypasses Verified
              </div>
            </div>
          </div>
        )}

        {/* Baseline Comparison Visual Section */}
        {latestRun && latestRun.financial && (
          <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-6 shadow-xl">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-6">
              <div>
                <h2 className="text-lg font-bold text-white flex items-center gap-2">
                  <svg className="w-5 h-5 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                  </svg>
                  Comparative Revenue Recovery vs Baselines
                </h2>
                <p className="text-xs text-slate-400 mt-1">
                  Benchmarked against Baseline A (Zero Recovery Action) and Baseline B (Naive Immediate Retry).
                </p>
              </div>
              <div className="text-xs font-mono px-3 py-1.5 rounded-lg bg-slate-800 border border-slate-700 text-slate-300">
                Benefit / Cost Efficiency:{" "}
                <span className="text-emerald-400 font-bold">
                  {latestRun.financial?.recoveryEfficiency != null ? `${formatCurrency(latestRun.financial.recoveryEfficiency)} / attempt` : "N/A"}
                </span>
              </div>
            </div>

            {/* Comparison Visual Bars */}
            <div className="space-y-4">
              {/* Baseline A: No Recovery */}
              <div>
                <div className="flex justify-between text-xs mb-1">
                  <span className="text-slate-400 font-medium">Baseline A: No Recovery (Status Quo)</span>
                  <span className="font-mono text-slate-400">
                    {formatCurrency(latestRun.financial?.baselineNoRecoveryAmount)} (0.0%)
                  </span>
                </div>
                <div className="w-full h-4 bg-slate-800 rounded-full overflow-hidden">
                  <div className="h-full bg-slate-600 rounded-full" style={{ width: "0%" }} />
                </div>
              </div>

              {/* Baseline B: Naive Retry */}
              <div>
                <div className="flex justify-between text-xs mb-1">
                  <span className="text-amber-300 font-medium">Baseline B: Naive Retry (Blind 2x Execution)</span>
                  <span className="font-mono text-amber-300">
                    {formatCurrency(latestRun.financial?.baselineNaiveRetryAmount)} (
                    {(
                      ((latestRun.financial?.baselineNaiveRetryAmount || 0) /
                        Math.max(1, latestRun.financial?.totalRecoverableRevenue || 1)) *
                      100
                    ).toFixed(1)}
                    %)
                  </span>
                </div>
                <div className="w-full h-4 bg-slate-800 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-amber-500 rounded-full transition-all duration-500"
                    style={{
                      width: `${Math.min(
                        100,
                        ((latestRun.financial?.baselineNaiveRetryAmount || 0) /
                          Math.max(1, latestRun.financial?.totalRecoverableRevenue || 1)) *
                          100
                      )}%`,
                    }}
                  />
                </div>
              </div>

              {/* Sentinel Recovery */}
              <div>
                <div className="flex justify-between text-xs mb-1">
                  <span className="text-purple-300 font-bold flex items-center gap-1.5">
                    <span className="h-2 w-2 rounded-full bg-purple-400" />
                    Razorpay Sentinel: Policy-Governed AI Recovery
                  </span>
                  <span className="font-mono text-emerald-400 font-bold">
                    {formatCurrency(latestRun.financial?.totalRecoveredRevenue)} (
                    {latestRun.financial?.financialRecoveryRate != null ? latestRun.financial.financialRecoveryRate.toFixed(1) : "0.0"}%)
                  </span>
                </div>
                <div className="w-full h-4 bg-slate-800 rounded-full overflow-hidden border border-purple-500/30">
                  <div
                    className="h-full bg-gradient-to-r from-purple-500 to-emerald-400 rounded-full transition-all duration-500"
                    style={{
                      width: `${Math.min(100, latestRun.financial?.financialRecoveryRate || 0)}%`,
                    }}
                  />
                </div>
              </div>
            </div>
          </div>
        )}

        {/* 4-Quadrant Benchmark Dimension Cards */}
        {latestRun && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {/* Quadrant 1: Financial Effectiveness */}
            <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-6 shadow-xl">
              <div className="flex items-center gap-2 pb-4 border-b border-slate-800 text-sm font-bold text-white">
                <div className="p-1.5 rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                </div>
                1. Financial Recovery Effectiveness
              </div>
              <div className="grid grid-cols-2 gap-4 mt-4 text-xs">
                <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800/80">
                  <span className="text-slate-400">Total Revenue at Risk</span>
                  <div className="text-sm font-mono font-bold text-slate-200 mt-1">
                    {formatCurrency(latestRun.financial?.totalRevenueAtRisk)}
                  </div>
                </div>
                <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800/80">
                  <span className="text-slate-400">Recoverable Revenue</span>
                  <div className="text-sm font-mono font-bold text-slate-200 mt-1">
                    {formatCurrency(latestRun.financial?.totalRecoverableRevenue)}
                  </div>
                </div>
                <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800/80">
                  <span className="text-slate-400">Total Recovered Revenue</span>
                  <div className="text-sm font-mono font-bold text-emerald-400 mt-1">
                    {formatCurrency(latestRun.financial?.totalRecoveredRevenue)}
                  </div>
                </div>
                <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800/80">
                  <span className="text-slate-400">Recovery Efficiency</span>
                  <div className="text-sm font-mono font-bold text-cyan-400 mt-1">
                    {latestRun.financial?.recoveryEfficiency != null ? `${formatCurrency(latestRun.financial.recoveryEfficiency)} / att` : "N/A"}
                  </div>
                </div>
              </div>
            </div>

            {/* Quadrant 2: Detection & Diagnosis Accuracy */}
            <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-6 shadow-xl">
              <div className="flex items-center gap-2 pb-4 border-b border-slate-800 text-sm font-bold text-white">
                <div className="p-1.5 rounded-lg bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                </div>
                2. Detection & Diagnosis Accuracy
              </div>
              <div className="grid grid-cols-2 gap-4 mt-4 text-xs">
                <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800/80">
                  <span className="text-slate-400">Detection F1 / Precision</span>
                  <div className="text-sm font-mono font-bold text-cyan-400 mt-1">
                    {latestRun.detection?.f1Score != null ? latestRun.detection.f1Score.toFixed(1) : "0.0"}% (P: {latestRun.detection?.precision != null ? latestRun.detection.precision.toFixed(0) : "0"}%)
                  </div>
                </div>
                <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800/80">
                  <span className="text-slate-400">Mean Time to Detect (TTD)</span>
                  <div className="text-sm font-mono font-bold text-slate-200 mt-1">
                    {latestRun.detection?.averageDetectionLatencyMs != null ? `${latestRun.detection.averageDetectionLatencyMs} ms` : "N/A"}
                  </div>
                </div>
                <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800/80">
                  <span className="text-slate-400">Root-Cause Accuracy</span>
                  <div className="text-sm font-mono font-bold text-emerald-400 mt-1">
                    {latestRun.diagnosis?.diagnosisAccuracy != null ? latestRun.diagnosis.diagnosisAccuracy.toFixed(1) : "0.0"}%
                  </div>
                </div>
                <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800/80">
                  <span className="text-slate-400">Mean AI Confidence</span>
                  <div className="text-sm font-mono font-bold text-purple-400 mt-1">
                    {((latestRun.diagnosis?.averageConfidence || 0) * 100).toFixed(1)}%
                  </div>
                </div>
              </div>
            </div>

            {/* Quadrant 3: Strategy Precision & Policy Safety */}
            <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-6 shadow-xl">
              <div className="flex items-center gap-2 pb-4 border-b border-slate-800 text-sm font-bold text-white">
                <div className="p-1.5 rounded-lg bg-purple-500/10 text-purple-400 border border-purple-500/20">
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                  </svg>
                </div>
                3. Strategy Precision & Policy Gate
              </div>
              <div className="grid grid-cols-2 gap-4 mt-4 text-xs">
                <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800/80">
                  <span className="text-slate-400">Strategy Safety Precision</span>
                  <div className="text-sm font-mono font-bold text-emerald-400 mt-1">
                    {latestRun.strategy?.strategyQualityScore != null ? latestRun.strategy.strategyQualityScore.toFixed(1) : "0.0"}%
                  </div>
                </div>
                <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800/80">
                  <span className="text-slate-400">Policy Compliance Rate</span>
                  <div className="text-sm font-mono font-bold text-purple-400 mt-1">
                    {latestRun.policy?.policyComplianceRate != null ? latestRun.policy.policyComplianceRate.toFixed(1) : "0.0"}%
                  </div>
                </div>
                <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800/80">
                  <span className="text-slate-400">Policy-Approved Recoveries</span>
                  <div className="text-sm font-mono font-bold text-emerald-400 mt-1">
                    {latestRun.policy?.approvedCount ?? 0} APPROVED
                  </div>
                </div>
                <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800/80">
                  <span className="text-slate-400">Human Escalations</span>
                  <div className="text-sm font-mono font-bold text-amber-400 mt-1">
                    {latestRun.policy?.humanReviewCount ?? 0} HUMAN_REVIEW
                  </div>
                </div>
              </div>
            </div>

            {/* Quadrant 4: Recovery Execution & Latency */}
            <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-6 shadow-xl">
              <div className="flex items-center gap-2 pb-4 border-b border-slate-800 text-sm font-bold text-white">
                <div className="p-1.5 rounded-lg bg-amber-500/10 text-amber-400 border border-amber-500/20">
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                  </svg>
                </div>
                4. Recovery Execution & Latency
              </div>
              <div className="grid grid-cols-2 gap-4 mt-4 text-xs">
                <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800/80">
                  <span className="text-slate-400">Execution Success Rate</span>
                  <div className="text-sm font-mono font-bold text-emerald-400 mt-1">
                    {latestRun.recovery?.transactionRecoveryRate != null ? latestRun.recovery.transactionRecoveryRate.toFixed(1) : "0.0"}%
                  </div>
                </div>
                <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800/80">
                  <span className="text-slate-400">Mean Attempts per Recovery</span>
                  <div className="text-sm font-mono font-bold text-slate-200 mt-1">
                    {latestRun.recovery?.averageAttemptsPerRecovery != null ? latestRun.recovery.averageAttemptsPerRecovery.toFixed(2) : "1.00"}
                  </div>
                </div>
                <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800/80">
                  <span className="text-slate-400">Execution Attempts</span>
                  <div className="text-sm font-mono font-bold text-slate-200 mt-1">
                    {latestRun.recovery?.attemptedTransactions ?? 0} Total Attempts
                  </div>
                </div>
                <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800/80">
                  <span className="text-slate-400">Successful Transactions</span>
                  <div className="text-sm font-mono font-bold text-emerald-400 mt-1">
                    {latestRun.recovery?.successfulRecoveries ?? 0} Recovered
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Safety & Invariant Verification Checklist */}
        {latestRun && latestRun.safety && (
          <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-6 shadow-xl">
            <div className="flex items-center justify-between border-b border-slate-800 pb-4 mb-4">
              <div>
                <h3 className="text-sm font-bold text-white flex items-center gap-2">
                  <svg className="w-4 h-4 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                  </svg>
                  System Safety Invariant Proof & Compliance
                </h3>
                <p className="text-xs text-slate-400 mt-0.5">
                  Automated verification guaranteeing that AI decisions never bypass deterministic policy gates.
                </p>
              </div>
              <span className="px-2.5 py-1 rounded text-xs font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                SAFETY: {latestRun.safety?.safetyScore != null ? latestRun.safety.safetyScore.toFixed(0) : "100"} / 100
              </span>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-4 gap-4 text-xs">
              <div className="flex items-start gap-2.5 p-3 rounded-lg bg-slate-950/40 border border-slate-800">
                <span className="text-emerald-400 mt-0.5 font-bold">✓</span>
                <div>
                  <div className="font-semibold text-slate-200">Zero Policy Bypasses</div>
                  <div className="text-slate-400 text-[11px] mt-0.5">
                    {latestRun.safety.policyBypasses === 0 ? "100% Policy Gated (0 Bypasses)" : `${latestRun.safety.policyBypasses} Bypasses Detected`}
                  </div>
                </div>
              </div>

              <div className="flex items-start gap-2.5 p-3 rounded-lg bg-slate-950/40 border border-slate-800">
                <span className="text-emerald-400 mt-0.5 font-bold">✓</span>
                <div>
                  <div className="font-semibold text-slate-200">Zero Bound Violations</div>
                  <div className="text-slate-400 text-[11px] mt-0.5">
                    {latestRun.safety.financialBoundViolations === 0 ? "Within Safety Envelope" : `${latestRun.safety.financialBoundViolations} Out of Bounds`}
                  </div>
                </div>
              </div>

              <div className="flex items-start gap-2.5 p-3 rounded-lg bg-slate-950/40 border border-slate-800">
                <span className="text-emerald-400 mt-0.5 font-bold">✓</span>
                <div>
                  <div className="font-semibold text-slate-200">Fail-Closed Posture</div>
                  <div className="text-slate-400 text-[11px] mt-0.5">
                    {latestRun.safety.unauthorizedExecutions === 0 ? "100% High-Risk / Uncertain blocked" : `${latestRun.safety.unauthorizedExecutions} Unauthorized Executed`}
                  </div>
                </div>
              </div>

              <div className="flex items-start gap-2.5 p-3 rounded-lg bg-slate-950/40 border border-slate-800">
                <span className="text-emerald-400 mt-0.5 font-bold">✓</span>
                <div>
                  <div className="font-semibold text-slate-200">SHA-256 Audit Integrity</div>
                  <div className="text-slate-400 text-[11px] mt-0.5">
                    {latestRun.safety.auditIntegrityFailures === 0 ? "100% Cryptographically Intact" : `${latestRun.safety.auditIntegrityFailures} Integrity Failures`}
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Per-Incident Breakdown Table */}
        {latestRun && latestRun.incidents && latestRun.incidents.length > 0 && (
          <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-6 shadow-2xl">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-4">
              <div>
                <h3 className="text-base font-bold text-white flex items-center gap-2">
                  <svg className="w-4 h-4 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 10h16M4 14h16M4 18h16" />
                  </svg>
                  Per-Incident Evaluation Results ({latestRun.incidents.length} Scenarios)
                </h3>
                <p className="text-xs text-slate-400 mt-0.5">
                  Click on any incident row to inspect detailed diagnosis, strategy, and execution traces.
                </p>
              </div>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-slate-800 text-slate-400 uppercase tracking-wider text-[10px]">
                    <th className="pb-3 px-3">Incident</th>
                    <th className="pb-3 px-3">Scenario</th>
                    <th className="pb-3 px-3">TTD</th>
                    <th className="pb-3 px-3">Root Cause Accuracy</th>
                    <th className="pb-3 px-3">Strategy</th>
                    <th className="pb-3 px-3">Policy Gate</th>
                    <th className="pb-3 px-3">Recovered (Sentinel vs Naive)</th>
                    <th className="pb-3 px-3">Audit Chain</th>
                    <th className="pb-3 px-3 text-right">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 font-sans">
                  {latestRun.incidents.map((inc) => {
                    const isSelected = selectedIncident?.evaluationIncidentId === inc.evaluationIncidentId;
                    return (
                      <tr
                        key={inc.evaluationIncidentId}
                        onClick={() => setSelectedIncident(inc)}
                        className={`hover:bg-slate-800/50 cursor-pointer transition-colors ${
                          isSelected ? "bg-purple-950/30 border-l-2 border-purple-500" : ""
                        }`}
                      >
                        <td className="py-3 px-3 font-mono font-bold text-purple-300">
                          #{inc.incidentId != null ? inc.incidentId : inc.evaluationIncidentId.substring(0, 8)}
                        </td>
                        <td className="py-3 px-3">
                          <div className="font-medium text-slate-200">
                            {inc.scenarioType.replace(/_/g, " ")}
                          </div>
                          <div className="text-[10px] text-slate-500 font-mono">{inc.scenarioType}</div>
                        </td>
                        <td className="py-3 px-3 font-mono text-slate-300">
                          {inc.detectionLatencyMs != null ? `${inc.detectionLatencyMs} ms` : "N/A"}
                        </td>
                        <td className="py-3 px-3">
                          {inc.rootCauseCorrect ? (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                              MATCH ({inc.diagnosisConfidence != null ? (inc.diagnosisConfidence * 100).toFixed(0) : "0"}%)
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-rose-500/10 text-rose-400 border border-rose-500/20">
                              MISMATCH
                            </span>
                          )}
                        </td>
                        <td className="py-3 px-3 font-mono text-slate-300">
                          {inc.strategyProposed || "NO_ACTION"}
                        </td>
                        <td className="py-3 px-3">
                          <span
                            className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                              inc.policyDecision === "APPROVED" || inc.policyDecision === "APPROVED_WITH_LIMITS"
                                ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20"
                                : inc.policyDecision === "HUMAN_REVIEW"
                                ? "bg-amber-500/10 text-amber-400 border border-amber-500/20"
                                : "bg-rose-500/10 text-rose-400 border border-rose-500/20"
                            }`}
                          >
                            {inc.policyDecision || "N/A"}
                          </span>
                        </td>
                        <td className="py-3 px-3 font-mono">
                          <span className="text-emerald-400 font-bold">
                            {formatCurrency(inc.recoveredRevenue)}
                          </span>{" "}
                          <span className="text-slate-500 text-[10px]">
                            vs {formatCurrency(inc.naiveRetryRecoveredRevenue)}
                          </span>
                        </td>
                        <td className="py-3 px-3">
                          {inc.auditVerified ? (
                            <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-500/10 text-emerald-400">
                              VERIFIED
                            </span>
                          ) : (
                            <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-rose-500/10 text-rose-400">
                              FAILED
                            </span>
                          )}
                        </td>
                        <td className="py-3 px-3 text-right font-mono font-bold text-purple-300">
                          {inc.status}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Selected Incident Deep Inspector Drawer */}
        {selectedIncident && (
          <div className="bg-slate-900/90 border border-purple-500/30 rounded-xl p-6 shadow-2xl space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-slate-800 pb-3">
              <div>
                <h4 className="text-sm font-bold text-white flex items-center gap-2">
                  <span className="px-2 py-0.5 rounded text-xs font-mono bg-purple-600 text-white">
                    Incident #{selectedIncident.incidentId != null ? selectedIncident.incidentId : selectedIncident.evaluationIncidentId.substring(0, 8)}
                  </span>
                  <span>{selectedIncident.scenarioType.replace(/_/g, " ")}</span>
                </h4>
                <div className="text-xs text-slate-400 mt-1">{selectedIncident.notes}</div>
              </div>
              <div className="text-xs font-mono text-purple-300">
                Status: <span className="text-base font-bold text-emerald-400">{selectedIncident.status}</span>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
              <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800">
                <span className="text-[10px] uppercase font-bold text-slate-400">Ground Truth Pattern</span>
                <p className="text-slate-200 mt-1 font-medium">
                  {selectedIncident.groundTruth?.scenarioSummary ||
                    selectedIncident.groundTruth?.expectedRootCauseKeywords?.join(", ") ||
                    selectedIncident.scenarioType}
                </p>
              </div>

              <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800">
                <span className="text-[10px] uppercase font-bold text-slate-400">AI Diagnosed Root Cause</span>
                <p className="text-slate-200 mt-1 font-medium">
                  {selectedIncident.rootCauseObserved || "No root cause declared"}
                </p>
              </div>

              <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800">
                <span className="text-[10px] uppercase font-bold text-slate-400">Recovery Execution Status</span>
                <p className="text-slate-200 mt-1 font-mono">{selectedIncident.executionStatus || "NO_EXECUTION"}</p>
                <div className="mt-1 text-slate-400 text-[11px]">
                  Recovered:{" "}
                  <span className="text-emerald-400 font-bold">
                    {formatCurrency(selectedIncident.recoveredRevenue)}
                  </span>{" "}
                  of {formatCurrency(selectedIncident.recoverableRevenue)}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Benchmark Scenario Catalog */}
        <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-6 shadow-xl">
          <h3 className="text-sm font-bold text-white mb-3 flex items-center gap-2">
            <svg className="w-4 h-4 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
            </svg>
            Supported Evaluation Benchmark Scenarios
          </h3>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 text-xs">
            {scenarios.map((sc) => (
              <div key={sc.scenario} className="p-4 bg-slate-950/60 border border-slate-800 rounded-lg flex flex-col justify-between">
                <div>
                  <div className="font-semibold text-slate-200">{sc.name}</div>
                  <div className="text-[10px] font-mono text-purple-400 mt-0.5">{sc.scenario}</div>
                  <p className="text-slate-400 text-[11px] mt-2 leading-relaxed">{sc.description}</p>
                </div>
                <div className="mt-3 pt-3 border-t border-slate-850 text-[10px] space-y-1 text-slate-400">
                  <div>
                    Expected Policy: <span className="font-mono text-slate-200">{sc.expectedPolicyBehavior}</span>
                  </div>
                  <div>
                    Safe Strategies: <span className="font-mono text-emerald-400">{sc.expectedSafeStrategies?.join(", ")}</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
