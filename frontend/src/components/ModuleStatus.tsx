const MODULES = [
  { name: "Project Foundation", state: "done" as const },
  { name: "Payment Data Engine", state: "done" as const },
  { name: "Incident Simulator", state: "queued" as const },
  { name: "Monitoring & Incident Detection", state: "queued" as const },
  { name: "Revenue-at-Risk Engine", state: "queued" as const },
  { name: "Command Center Dashboard", state: "queued" as const },
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
        <span className="text-sm font-medium text-ink">Module progress</span>
      </div>
      <ul className="divide-y divide-base-border">
        {MODULES.map((module) => (
          <li key={module.name} className="flex items-center justify-between px-4 py-2.5">
            <span className="text-sm text-ink-muted">{module.name}</span>
            <span className={`font-mono text-[11px] ${STATE_COLOR[module.state]}`}>
              {STATE_LABEL[module.state]}
            </span>
          </li>
        ))}
      </ul>
      <div className="border-t border-base-border px-4 py-2.5 text-xs text-ink-faint">
        16 modules planned in total — see docs/development-roadmap.md
      </div>
    </div>
  );
}
