const SUBSYSTEMS = [
  { name: "Payment Data Engine", state: "done" as const },
  { name: "Incident Simulator", state: "done" as const },
  { name: "Monitoring & Anomaly Detection", state: "done" as const },
  { name: "Revenue-at-Risk & Financial Impact", state: "done" as const },
  { name: "AI Diagnostic Agent", state: "done" as const },
  { name: "Root-Cause Investigation Engine", state: "done" as const },
  { name: "Recovery Strategy Engine", state: "done" as const },
  { name: "Deterministic Policy Gate", state: "done" as const },
  { name: "Recovery Execution Simulator", state: "done" as const },
  { name: "Tamper-Evident SHA-256 Audit Trail", state: "done" as const },
  { name: "Safety & Resilience Lab", state: "done" as const },
  { name: "Evaluation & Benchmarking Engine", state: "done" as const },
  { name: "Incident Command Center", state: "done" as const },
];


const STATE_LABEL: Record<"done" | "queued", string> = {
  done: "built",
  queued: "queued",
};

const STATE_COLOR: Record<"done" | "queued", string> = {
  done: "text-signal-nominal",
  queued: "text-ink-faint",
};

export function ModuleStatus() {
  return (
    <div className="border border-base-border bg-base-panel">
      <div className="border-b border-base-border px-4 py-3">
        <span className="text-sm font-medium text-ink">Subsystem Status</span>
      </div>
      <ul className="divide-y divide-base-border">
        {SUBSYSTEMS.map((subsystem) => (
          <li key={subsystem.name} className="flex items-center justify-between px-4 py-2.5">
            <span className="text-sm text-ink-muted">{subsystem.name}</span>
            <span className={`font-mono text-[11px] ${STATE_COLOR[subsystem.state]}`}>
              {STATE_LABEL[subsystem.state]}
            </span>
          </li>
        ))}
      </ul>
      <div className="border-t border-base-border px-4 py-2.5 text-xs text-ink-faint">
        All core subsystems operational — see docs/architecture.md
      </div>
    </div>
  );
}
