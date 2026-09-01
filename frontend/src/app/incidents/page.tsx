"use client";

import { useCallback, useEffect, useState } from "react";
import { fetchIncidents, type Incident } from "@/lib/monitoring";

const SEVERITY_COLOR: Record<string, string> = {
  LOW: "text-signal-watch",
  MEDIUM: "text-signal-watch",
  HIGH: "text-signal-critical",
  CRITICAL: "text-signal-critical",
};

export default function IncidentsPage() {
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const page = await fetchIncidents(0, 50);
      setIncidents(page.content);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load incidents");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <main className="mx-auto max-w-6xl px-6 py-10">
      <p className="font-mono text-xs text-signal-wire">Module 3 · Monitoring &amp; Incident Detection</p>
      <h1 className="mt-2 text-2xl font-semibold tracking-tight text-ink">Incidents</h1>
      <p className="mt-2 max-w-xl text-sm text-ink-muted">
        Every incident the detection engine has found, most recent first.
      </p>

      {error && (
        <div className="mt-4 border border-signal-critical/40 bg-signal-critical/10 px-4 py-3 text-sm text-signal-critical">
          {error}
        </div>
      )}

      <div className="mt-6">
        {loading ? (
          <div className="border border-base-border bg-base-panel px-4 py-10 text-center text-sm text-ink-muted">
            loading incidents...
          </div>
        ) : incidents.length === 0 ? (
          <div className="border border-base-border bg-base-panel px-4 py-10 text-center text-sm text-ink-muted">
            No incidents detected yet. Run detection from the Monitoring page after simulating an incident.
          </div>
        ) : (
          <div className="overflow-x-auto border border-base-border bg-base-panel">
            <table className="w-full min-w-[720px] text-left text-xs">
              <thead>
                <tr className="border-b border-base-border text-ink-muted">
                  <th className="px-3 py-2 font-medium">ID</th>
                  <th className="px-3 py-2 font-medium">Detected</th>
                  <th className="px-3 py-2 font-medium">Type</th>
                  <th className="px-3 py-2 font-medium">Severity</th>
                  <th className="px-3 py-2 font-medium">Status</th>
                  <th className="px-3 py-2 font-medium">Target</th>
                  <th className="px-3 py-2 text-right font-medium">Affected</th>
                  <th className="px-3 py-2 text-right font-medium">Revenue at risk</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-base-border font-mono">
                {incidents.map((incident) => (
                  <tr key={incident.id}>
                    <td className="px-3 py-2 text-ink-faint">
                      <a href={`/incidents/${incident.id}`} className="text-signal-wire underline underline-offset-2">
                        #{incident.id}
                      </a>
                    </td>
                    <td className="px-3 py-2 text-ink-muted">{new Date(incident.detectedAt).toLocaleString()}</td>
                    <td className="px-3 py-2 text-ink">{incident.type}</td>
                    <td className={`px-3 py-2 font-semibold ${SEVERITY_COLOR[incident.severity] ?? "text-ink"}`}>
                      {incident.severity}
                    </td>
                    <td className="px-3 py-2 text-ink-muted">{incident.status}</td>
                    <td className="px-3 py-2 text-ink-muted">
                      {incident.targetBank && incident.targetPaymentMethod
                        ? `${incident.targetBank} + ${incident.targetPaymentMethod}`
                        : incident.targetBank ?? incident.targetMerchantId ?? "system-wide"}
                    </td>
                    <td className="px-3 py-2 text-right text-ink">
                      {incident.affectedTransactions.toLocaleString("en-IN")}
                    </td>
                    <td className="px-3 py-2 text-right text-signal-critical">
                      ₹{Number(incident.revenueAtRisk).toLocaleString("en-IN")}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </main>
  );
}
