const STAGES = [
  { name: "Detect", note: "Deterministic incident detection" },
  { name: "Diagnose", note: "AI root-cause analysis" },
  { name: "Quantify", note: "Revenue at risk" },
  { name: "Decide", note: "AI recovery recommendation" },
  { name: "Gate", note: "Policy & guardrail validation" },
  { name: "Recover", note: "Simulated recovery execution" },
  { name: "Audit", note: "Complete audit trail" },
];

export function WorkflowChain() {
  return (
    <ol className="flex flex-col divide-y divide-base-border border border-base-border sm:flex-row sm:divide-x sm:divide-y-0">
      {STAGES.map((stage, index) => (
        <li key={stage.name} className="flex-1 px-4 py-3">
          <div className="flex items-baseline gap-2">
            <span className="font-mono text-xs text-ink-faint">
              {String(index + 1).padStart(2, "0")}
            </span>
            <span className="text-sm font-medium text-ink">{stage.name}</span>
          </div>
          <p className="mt-1 text-xs leading-snug text-ink-muted">{stage.note}</p>
        </li>
      ))}
    </ol>
  );
}
