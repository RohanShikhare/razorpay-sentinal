"use client";

import { useState } from "react";

const QUICK_COUNTS = [100, 500, 1_000, 5_000];

export function GenerateControl({
  onGenerate,
  generating,
}: {
  onGenerate: (count: number) => void;
  generating: boolean;
}) {
  const [custom, setCustom] = useState("");

  return (
    <div className="border border-base-border bg-base-panel p-4">
      <div className="text-sm font-medium text-ink">Generate synthetic payments</div>
      <p className="mt-1 text-xs text-ink-muted">
        Appends new synthetic payments to the dataset. Does not clear existing data.
      </p>

      <div className="mt-3 flex flex-wrap items-center gap-2">
        {QUICK_COUNTS.map((count) => (
          <button
            key={count}
            type="button"
            disabled={generating}
            onClick={() => onGenerate(count)}
            className="border border-base-border bg-base-raised px-3 py-1.5 text-xs font-medium text-ink hover:border-signal-wire disabled:cursor-not-allowed disabled:opacity-50"
          >
            Generate {count.toLocaleString("en-IN")}
          </button>
        ))}

        <div className="flex items-center gap-1.5">
          <input
            type="number"
            min={1}
            max={10_000}
            value={custom}
            onChange={(e) => setCustom(e.target.value)}
            placeholder="custom"
            className="w-24 border border-base-border bg-base px-2 py-1.5 font-mono text-xs text-ink placeholder:text-ink-faint focus:border-signal-wire focus:outline-none"
          />
          <button
            type="button"
            disabled={generating || !custom}
            onClick={() => {
              const n = Number(custom);
              if (n > 0) onGenerate(n);
            }}
            className="border border-base-border bg-base-raised px-3 py-1.5 text-xs font-medium text-ink hover:border-signal-wire disabled:cursor-not-allowed disabled:opacity-50"
          >
            Generate
          </button>
        </div>
      </div>

      {generating && <p className="mt-2 font-mono text-xs text-signal-watch">generating...</p>}
    </div>
  );
}
