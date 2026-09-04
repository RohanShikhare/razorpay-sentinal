const STAGES = [
  { name: "Detect", note: "Anomaly detection" },
  { name: "Quantify", note: "Revenue at risk" },
  { name: "Diagnose", note: "Advisory AI diagnosis" },
  { name: "Investigate", note: "Evidence hypothesis proof" },
  { name: "Strategize", note: "Bounded recovery strategy" },
  { name: "Gate", note: "12-rule policy gate" },
  { name: "Recover", note: "Simulated execution" },
  { name: "Audit", note: "SHA-256 audit proof" },
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
