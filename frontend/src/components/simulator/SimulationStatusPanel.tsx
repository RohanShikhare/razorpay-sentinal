import { SCENARIOS, type SimulationStatusResponse } from "@/lib/simulations";

function scenarioLabel(scenario: string | null): string {
  return SCENARIOS.find((s) => s.value === scenario)?.label ?? scenario ?? "—";
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="text-xs text-ink-muted">{label}</div>
      <div className="mt-0.5 font-mono text-sm text-ink">{value}</div>
    </div>
  );
}

export function SimulationStatusPanel({ status }: { status: SimulationStatusResponse }) {
  return (
    <div className="border border-base-border bg-base-panel">
      <div className="flex items-center justify-between border-b border-base-border px-4 py-3">
        <span className="text-sm font-medium text-ink">Simulation status</span>
        <span
          className={`px-2 py-0.5 text-xs font-medium tracking-wide ${
            status.active
              ? "bg-signal-critical/15 text-signal-critical"
              : "bg-signal-nominal/15 text-signal-nominal"
          }`}
        >
          {status.active ? "SIMULATION ACTIVE" : "HEALTHY"}
        </span>
      </div>

      {status.active ? (
        <div className="grid grid-cols-2 gap-4 px-4 py-4 sm:grid-cols-4">
          <Metric label="Scenario" value={scenarioLabel(status.scenario)} />
          <Metric
            label="Started"
            value={status.startedAt ? new Date(status.startedAt).toLocaleTimeString() : "—"}
          />
          <Metric label="Affected transactions" value={status.affectedTransactions.toLocaleString("en-IN")} />
          <Metric label="Modified transactions" value={status.modifiedTransactions.toLocaleString("en-IN")} />
          <Metric label="Failure rate before" value={`${status.failureRateBefore.toFixed(2)}%`} />
          <Metric label="Failure rate after" value={`${status.failureRateAfter.toFixed(2)}%`} />
          {status.targetBank && <Metric label="Target bank" value={status.targetBank} />}
          {status.targetPaymentMethod && <Metric label="Target method" value={status.targetPaymentMethod} />}
          {status.targetMerchantId && <Metric label="Target merchant" value={status.targetMerchantId} />}
        </div>
      ) : (
        <div className="px-4 py-6 text-sm text-ink-muted">
          No simulation is active. The dataset reflects normal, healthy traffic.
        </div>
      )}
    </div>
  );
}
