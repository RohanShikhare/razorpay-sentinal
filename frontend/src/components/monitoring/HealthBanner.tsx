import type { MonitoringSummary } from "@/lib/monitoring";

const SEVERITY_STYLES: Record<string, { badge: string; icon: string }> = {
  NORMAL: { badge: "bg-signal-nominal/15 text-signal-nominal", icon: "" },
  LOW: { badge: "bg-signal-watch/15 text-signal-watch", icon: "🟡" },
  MEDIUM: { badge: "bg-signal-watch/15 text-signal-watch", icon: "🟠" },
  HIGH: { badge: "bg-signal-critical/15 text-signal-critical", icon: "🔴" },
  CRITICAL: { badge: "bg-signal-critical/20 text-signal-critical", icon: "🔴" },
};

export function HealthBanner({ summary }: { summary: MonitoringSummary }) {
  const style = SEVERITY_STYLES[summary.paymentHealth] ?? SEVERITY_STYLES.NORMAL;
  const isNormal = summary.paymentHealth === "NORMAL";

  return (
    <div className={`border border-base-border px-5 py-4 ${isNormal ? "bg-base-panel" : "bg-signal-critical/5"}`}>
      <div className="flex items-center gap-3">
        <span className={`px-2.5 py-1 text-sm font-semibold tracking-wide ${style.badge}`}>
          {style.icon} {isNormal ? "NORMAL" : `${summary.paymentHealth} INCIDENT`}
        </span>
        {!isNormal && (
          <span className="text-sm text-ink-muted">
            {summary.activeIncidents} active incident{summary.activeIncidents === 1 ? "" : "s"} —{" "}
            {summary.affectedTransactions.toLocaleString("en-IN")} transactions affected
          </span>
        )}
      </div>
      {isNormal && (
        <p className="mt-1 text-sm text-ink-muted">
          Payment behavior is within the normal baseline. No active incidents.
        </p>
      )}
    </div>
  );
}
