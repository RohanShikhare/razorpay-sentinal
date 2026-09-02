"use client";

import { useState } from "react";
import { ScenarioSelector } from "@/components/simulator/ScenarioSelector";
import { resetSimulation, runSimulation, type SimulationScenario } from "@/lib/simulations";
import { runDetection } from "@/lib/monitoring";

export function QuickOperationsPanel({ onDataChanged }: { onDataChanged: () => Promise<void> }) {
  const [scenario, setScenario] = useState<SimulationScenario>("BANK_X_UPI_DEGRADATION");
  const [busy, setBusy] = useState<"simulate" | "detect" | "reset" | null>(null);
  const [notice, setNotice] = useState<{ tone: "ok" | "error"; message: string } | null>(null);

  async function run(action: "simulate" | "detect" | "reset") {
    setBusy(action);
    setNotice(null);
    try {
      if (action === "simulate") {
        const result = await runSimulation(scenario);
        setNotice({
          tone: "ok",
          message: result.message ?? `Simulation applied: ${result.modifiedTransactions.toLocaleString("en-IN")} transactions modified.`,
        });
      } else if (action === "detect") {
        const result = await runDetection();
        setNotice({
          tone: "ok",
          message: result.incidentDetected
            ? `Incident detected: ${result.severity} - failure rate ${result.currentFailureRate.toFixed(2)}%.`
            : `No incident detected. ${result.message ?? ""}`,
        });
      } else {
        const result = await resetSimulation();
        setNotice({
          tone: "ok",
          message: result.reset
            ? `Restored ${result.restoredTransactions.toLocaleString("en-IN")} transactions.`
            : result.message ?? "Nothing to reset.",
        });
      }
      await onDataChanged();
    } catch (err) {
      setNotice({ tone: "error", message: err instanceof Error ? err.message : `Failed to ${action}` });
    } finally {
      setBusy(null);
    }
  }

  const anyBusy = busy !== null;

  return (
    <div className="border border-base-border bg-base-panel p-4">
      <div className="text-sm font-medium text-ink">Quick operations</div>
      <p className="mt-1 text-xs text-ink-muted">
        Simulate a scenario, run detection, or reset - the dashboard refreshes automatically.
      </p>

      <div className="mt-3">
        <ScenarioSelector value={scenario} onChange={setScenario} disabled={anyBusy} />
      </div>

      <div className="mt-3 flex flex-wrap gap-2">
        <button
          type="button"
          onClick={() => run("simulate")}
          disabled={anyBusy}
          className="border border-signal-critical/50 bg-signal-critical/10 px-3 py-2 text-xs font-medium text-signal-critical hover:bg-signal-critical/20 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {busy === "simulate" ? "Simulating..." : "Simulate Incident"}
        </button>
        <button
          type="button"
          onClick={() => run("detect")}
          disabled={anyBusy}
          className="border border-signal-wire/50 bg-signal-wire/10 px-3 py-2 text-xs font-medium text-signal-wire hover:bg-signal-wire/20 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {busy === "detect" ? "Running detection..." : "Run Detection"}
        </button>
        <button
          type="button"
          onClick={() => run("reset")}
          disabled={anyBusy}
          className="border border-base-border bg-base-raised px-3 py-2 text-xs font-medium text-ink hover:border-signal-wire disabled:cursor-not-allowed disabled:opacity-50"
        >
          {busy === "reset" ? "Resetting..." : "Reset Simulation"}
        </button>
      </div>

      {notice && (
        <div
          className={`mt-3 border px-3 py-2 text-xs ${
            notice.tone === "ok"
              ? "border-signal-nominal/40 bg-signal-nominal/10 text-signal-nominal"
              : "border-signal-critical/40 bg-signal-critical/10 text-signal-critical"
          }`}
        >
          {notice.message}
        </div>
      )}
    </div>
  );
}
