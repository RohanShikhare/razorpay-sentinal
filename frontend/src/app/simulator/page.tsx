"use client";

import { useCallback, useEffect, useState } from "react";
import { ScenarioSelector } from "@/components/simulator/ScenarioSelector";
import { SimulationStatusPanel } from "@/components/simulator/SimulationStatusPanel";
import {
  fetchSimulationStatus,
  resetSimulation,
  runSimulation,
  type SimulationScenario,
  type SimulationStatusResponse,
} from "@/lib/simulations";

export default function SimulatorPage() {
  const [scenario, setScenario] = useState<SimulationScenario>("BANK_X_UPI_DEGRADATION");
  const [status, setStatus] = useState<SimulationStatusResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const loadStatus = useCallback(async () => {
    try {
      const data = await fetchSimulationStatus();
      setStatus(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load simulation status");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadStatus();
  }, [loadStatus]);

  async function handleSimulate() {
    setRunning(true);
    setError(null);
    setNotice(null);
    try {
      const result = await runSimulation(scenario);
      if (result.message) {
        setNotice(result.message);
      } else {
        setNotice(
          `Failure rate moved from ${result.failureRateBefore.toFixed(2)}% to ${result.failureRateAfter.toFixed(2)}% ` +
            `(${result.modifiedTransactions.toLocaleString("en-IN")} transactions modified).`
        );
      }
      await loadStatus();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to run simulation");
    } finally {
      setRunning(false);
    }
  }

  async function handleReset() {
    setResetting(true);
    setError(null);
    setNotice(null);
    try {
      const result = await resetSimulation();
      setNotice(
        result.reset
          ? `Restored ${result.restoredTransactions.toLocaleString("en-IN")} transactions to their pre-simulation state.`
          : result.message ?? "Nothing to reset."
      );
      await loadStatus();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to reset simulation");
    } finally {
      setResetting(false);
    }
  }

  return (
    <main className="mx-auto max-w-6xl px-6 py-10">
      <p className="font-mono text-xs text-signal-wire">Incident Simulator</p>
      <h1 className="mt-2 text-2xl font-semibold tracking-tight text-ink">Incident Simulator</h1>
      <p className="mt-2 max-w-2xl text-sm text-ink-muted">
        Deliberately degrades the synthetic payment dataset in controlled, reproducible ways. This
        only creates conditions in the data — it does not detect or declare an incident. That&apos;s
        the Monitoring &amp; Incident Detection Engine&apos;s job.
      </p>

      <div className="mt-6">
        <h2 className="text-sm font-medium text-ink">Scenario</h2>
        <div className="mt-2">
          <ScenarioSelector value={scenario} onChange={setScenario} disabled={running || resetting} />
        </div>
      </div>

      <div className="mt-4 flex flex-wrap gap-3">
        <button
          type="button"
          onClick={handleSimulate}
          disabled={running || resetting}
          className="border border-signal-critical/50 bg-signal-critical/10 px-4 py-2 text-sm font-medium text-signal-critical hover:bg-signal-critical/20 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {running ? "Simulating..." : "Simulate Incident"}
        </button>
        <button
          type="button"
          onClick={handleReset}
          disabled={running || resetting}
          className="border border-base-border bg-base-panel px-4 py-2 text-sm font-medium text-ink hover:border-signal-wire disabled:cursor-not-allowed disabled:opacity-50"
        >
          {resetting ? "Resetting..." : "Reset Simulation"}
        </button>
      </div>

      {error && (
        <div className="mt-4 border border-signal-critical/40 bg-signal-critical/10 px-4 py-3 text-sm text-signal-critical">
          {error}
        </div>
      )}
      {notice && !error && (
        <div className="mt-4 border border-signal-wire/40 bg-signal-wire/10 px-4 py-3 text-sm text-signal-wire">
          {notice}
        </div>
      )}

      <div className="mt-6">
        {loading || !status ? (
          <div className="border border-base-border bg-base-panel px-4 py-10 text-center text-sm text-ink-muted">
            loading simulation status...
          </div>
        ) : (
          <SimulationStatusPanel status={status} />
        )}
      </div>
    </main>
  );
}
