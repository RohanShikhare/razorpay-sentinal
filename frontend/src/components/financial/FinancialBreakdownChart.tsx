"use client";

import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { FinancialSegment } from "@/lib/financial";

function formatInr(value: number): string {
  if (Math.abs(value) >= 100_000) {
    return `₹${(value / 100_000).toFixed(2)}L`;
  }
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 }).format(value);
}

export function FinancialBreakdownChart({ segments, title }: { segments: FinancialSegment[]; title: string }) {
  if (segments.length === 0) {
    return null;
  }

  const data = segments
    .map((s) => ({
      name: s.segment,
      revenueAtRisk: Number(s.revenueAtRisk),
      recoverable: Number(s.estimatedRecoverableRevenue),
    }))
    .slice(0, 8);

  return (
    <div className="border border-base-border bg-base-panel p-4">
      <div className="text-sm font-medium text-ink">{title}</div>
      <div className="mt-3 h-64 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#293347" vertical={false} />
            <XAxis dataKey="name" tick={{ fill: "#8B95AC", fontSize: 11 }} interval={0} angle={-20} textAnchor="end" height={50} />
            <YAxis tick={{ fill: "#8B95AC", fontSize: 12 }} tickFormatter={(v) => formatInr(v)} width={70} />
            <Tooltip
              formatter={(value: number, key: string) => [formatInr(value), key === "revenueAtRisk" ? "Revenue at risk" : "Recoverable"]}
              contentStyle={{ background: "#151C2C", border: "1px solid #293347", fontSize: 12 }}
              labelStyle={{ color: "#E7EBF3" }}
            />
            <Bar dataKey="revenueAtRisk" fill="#E15A45" radius={[2, 2, 0, 0]} barSize={16} />
            <Bar dataKey="recoverable" fill="#3FBBA3" radius={[2, 2, 0, 0]} barSize={16} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
