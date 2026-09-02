import type { Incident } from "@/lib/monitoring";

const SEVERITY_COLOR: Record<string, string> = {
  LOW: "text-signal-watch",
  MEDIUM: "text-signal-watch",
  HIGH: "text-signal-critical",
  CRITICAL: "text-signal-critical",
};

function formatInr(value: string): string {
  const n = Number(value);
  if (Number.isNaN(n)) return value;
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 }).format(n);
}

function targetLabel(incident: Incident): string {
  if (incident.targetBank && incident.targetPaymentMethod) {
    return `${incident.targetBank} + ${incident.targetPaymentMethod}`;
  }
  return incident.targetBank ?? incident.targetMerchantId ?? "system-wide";
}

export function IncidentsTable({
  incidents,
  emptyMessage,
}: {
  incidents: Incident[];
  emptyMessage?: string;
}) {
  if (incidents.length === 0) {
    return (
      <div className="border border-base-border bg-base-panel px-4 py-10 text-center text-sm text-ink-muted">
        {emptyMessage ?? "No active incidents detected."}
      </div>
    );
  }

  return (
    <div className="overflow-x-auto border border-base-border bg-base-panel">
      <table className="w-full min-w-[820px] text-left text-xs">
        <thead>
          <tr className="border-b border-base-border text-ink-muted">
            <th className="px-3 py-2 font-medium">Incident</th>
            <th className="px-3 py-2 font-medium">Type</th>
            <th className="px-3 py-2 font-medium">Severity</th>
            <th className="px-3 py-2 font-medium">Status</th>
            <th className="px-3 py-2 font-medium">Detected At</th>
            <th className="px-3 py-2 text-right font-medium">Affected</th>
            <th className="px-3 py-2 text-right font-medium">Revenue at risk</th>
            <th className="px-3 py-2 text-right font-medium">Confidence</th>
            <th className="px-3 py-2 font-medium">Action</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-base-border font-mono">
          {incidents.map((incident) => (
            <tr key={incident.id}>
              <td className="px-3 py-2 text-ink-faint">#{incident.id}</td>
              <td className="px-3 py-2 text-ink" title={targetLabel(incident)}>
                {incident.type}
              </td>
              <td className={`px-3 py-2 font-semibold ${SEVERITY_COLOR[incident.severity] ?? "text-ink"}`}>
                {incident.severity}
              </td>
              <td className="px-3 py-2 text-ink-muted">{incident.status}</td>
              <td className="px-3 py-2 text-ink-muted">{new Date(incident.detectedAt).toLocaleString()}</td>
              <td className="px-3 py-2 text-right text-ink">
                {incident.affectedTransactions.toLocaleString("en-IN")}
              </td>
              <td className="px-3 py-2 text-right text-signal-critical">{formatInr(incident.revenueAtRisk)}</td>
              <td className="px-3 py-2 text-right text-ink-muted">{incident.confidence.toFixed(0)}%</td>
              <td className="px-3 py-2">
                <a href={`/incidents/${incident.id}`} className="text-signal-wire underline underline-offset-2">
                  View Incident
                </a>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
