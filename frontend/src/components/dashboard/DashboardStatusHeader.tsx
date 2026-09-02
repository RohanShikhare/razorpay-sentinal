import type { MonitoringSummary } from "@/lib/monitoring";

export function DashboardStatusHeader({
  summary,
  lastUpdated,
  refreshing,
  onRefresh,
}: {
  summary: MonitoringSummary | null;
  lastUpdated: Date | null;
  refreshing: boolean;
  onRefresh: () => void;
}) {
  const isNormal = !summary || summary.paymentHealth === "NORMAL";

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border border-base-border bg-base-panel px-4 py-2.5">
      <div className="flex items-center gap-2 text-xs">
        <span
          className={`h-2 w-2 rounded-full ${isNormal ? "bg-signal-nominal" : "bg-signal-critical animate-pulse"}`}
          aria-hidden
        />
        <span className={isNormal ? "text-signal-nominal" : "text-signal-critical"}>
          {isNormal ? "Monitoring: Healthy" : `Monitoring: ${summary?.paymentHealth} Incident`}
        </span>
      </div>

      <div className="flex items-center gap-3 text-xs text-ink-faint">
        <span className="font-mono">
          {lastUpdated ? `Last updated ${lastUpdated.toLocaleTimeString()}` : "Loading..."}
        </span>
        <button
          type="button"
          onClick={onRefresh}
          disabled={refreshing}
          className="text-signal-wire underline underline-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {refreshing ? "Refreshing..." : "Refresh"}
        </button>
      </div>
    </div>
  );
}
