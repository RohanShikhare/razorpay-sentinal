import type { CountBreakdown } from "@/lib/payments";

function formatInr(value: string): string {
  const n = Number(value);
  if (Number.isNaN(n)) return value;
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 }).format(n);
}

export function ErrorCodeBreakdownTable({ segments }: { segments: CountBreakdown[] }) {
  const sorted = [...segments].sort((a, b) => b.count - a.count);

  if (sorted.length === 0) {
    return (
      <div className="border border-base-border bg-base-panel px-4 py-6 text-center text-sm text-ink-muted">
        No failures recorded yet.
      </div>
    );
  }

  return (
    <div className="border border-base-border bg-base-panel">
      <div className="border-b border-base-border px-4 py-3">
        <span className="text-sm font-medium text-ink">Error code breakdown</span>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-left text-xs">
          <thead>
            <tr className="border-b border-base-border text-ink-muted">
              <th className="px-4 py-2 font-medium">Error code</th>
              <th className="px-4 py-2 text-right font-medium">Count</th>
              <th className="px-4 py-2 text-right font-medium">% of failures</th>
              <th className="px-4 py-2 text-right font-medium">Affected value</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-base-border font-mono">
            {sorted.map((segment) => (
              <tr key={segment.key}>
                <td className="px-4 py-2 text-ink">{segment.key}</td>
                <td className="px-4 py-2 text-right text-ink-muted">{segment.count.toLocaleString("en-IN")}</td>
                <td className="px-4 py-2 text-right font-semibold text-ink">
                  {segment.percentageOfFailures.toFixed(1)}%
                </td>
                <td className="px-4 py-2 text-right text-ink-muted">{formatInr(segment.value)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
