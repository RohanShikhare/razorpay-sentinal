"use client";

import { useCallback, useEffect, useState } from "react";
import { IncidentsTable } from "@/components/monitoring/IncidentsTable";
import { fetchIncidents, type Incident } from "@/lib/monitoring";

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
        Every incident the detection engine has found, most recent first. See the{" "}
        <a href="/dashboard" className="text-signal-wire underline underline-offset-2">
          Command Center
        </a>{" "}
        for the consolidated operations view.
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
        ) : (
          <IncidentsTable
            incidents={incidents}
            emptyMessage="No incidents detected yet. Run detection from the Simulator or Command Center after simulating an incident."
          />
        )}
      </div>
    </main>
  );
}
