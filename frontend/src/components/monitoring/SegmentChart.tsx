"use client";

import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { SegmentBreakdown } from "@/lib/monitoring";

export function SegmentChart({ segments, title }: { segments: SegmentBreakdown[]; title: string }) {
  if (segments.length === 0) {
    return null;
  }

  const data = segments.map((s) => ({
    name: s.label,
    baseline: s.baselineFailureRate,
    current: s.currentFailureRate,
    increase: s.failureRateIncrease,
  }));

  return (
    <div className="border border-base-border bg-base-panel p-4">
      <div className="text-sm font-medium text-ink">{title}</div>
      <div className="mt-3 h-64 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#293347" vertical={false} />
            <XAxis dataKey="name" tick={{ fill: "#8B95AC", fontSize: 11 }} interval={0} angle={-20} textAnchor="end" height={50} />
            <YAxis tick={{ fill: "#8B95AC", fontSize: 12 }} unit="%" />
            <Tooltip
              formatter={(value: number, key: string) => [`${value.toFixed(2)}%`, key === "baseline" ? "Baseline" : "Current"]}
              contentStyle={{ background: "#151C2C", border: "1px solid #293347", fontSize: 12 }}
              labelStyle={{ color: "#E7EBF3" }}
            />
            <Bar dataKey="baseline" fill="#4C8DFF" radius={[2, 2, 0, 0]} barSize={16} />
            <Bar dataKey="current" radius={[2, 2, 0, 0]} barSize={16}>
              {data.map((entry, index) => (
                <Cell key={index} fill={entry.increase > 5 ? "#E15A45" : "#3FBBA3"} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
