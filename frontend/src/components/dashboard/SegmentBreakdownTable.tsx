import type { CountValueBreakdown } from "@/lib/payments";

function formatInr(value: string): string {
  const n = Number(value);
  if (Number.isNaN(n)) return value;
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 }).format(n);
}

export function SegmentBreakdownTable({
  title,
  segments,
  labelHeader,
}: {
  title: string;
  segments: CountValueBreakdown[];
  labelHeader: string;
}) {
  const sorted = [...segments].sort((a, b) => b.failureRate - a.failureRate);

  return (
    <div className="border border-base-border bg-base-panel">
      <div className="border-b border-base-border px-4 py-3">
        <span className="text-sm font-medium text-ink">{title}</span>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-left text-xs">
          <thead>
            <tr className="border-b border-base-border text-ink-muted">
              <th className="px-4 py-2 font-medium">{labelHeader}</th>
              <th className="px-4 py-2 text-right font-medium">Transactions</th>
              <th className="px-4 py-2 text-right font-medium">Failed</th>
              <th className="px-4 py-2 text-right font-medium">Failure rate</th>
              <th className="px-4 py-2 text-right font-medium">Failed value</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-base-border font-mono">
            {sorted.map((segment) => (
              <tr key={segment.key}>
                <td className="px-4 py-2 text-ink">{segment.key}</td>
                <td className="px-4 py-2 text-right text-ink-muted">{segment.count.toLocaleString("en-IN")}</td>
                <td className="px-4 py-2 text-right text-ink-muted">{segment.failedCount.toLocaleString("en-IN")}</td>
                <td
                  className={`px-4 py-2 text-right font-semibold ${
                    segment.failureRate > 15 ? "text-signal-critical" : "text-ink"
                  }`}
                >
                  {segment.failureRate.toFixed(2)}%
                </td>
                <td className="px-4 py-2 text-right text-ink-muted">{formatInr(segment.failedValue)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
