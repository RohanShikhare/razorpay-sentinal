"use client";

import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

export function FailureRateComparisonChart({
  baselineFailureRate,
  currentFailureRate,
}: {
  baselineFailureRate: number;
  currentFailureRate: number;
}) {
  const data = [
    { name: "Baseline", rate: baselineFailureRate },
    { name: "Current", rate: currentFailureRate },
  ];

  return (
    <div className="h-56 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} layout="vertical" margin={{ top: 8, right: 24, bottom: 8, left: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#293347" horizontal={false} />
          <XAxis type="number" domain={[0, "dataMax + 5"]} tick={{ fill: "#8B95AC", fontSize: 12 }} unit="%" />
          <YAxis type="category" dataKey="name" tick={{ fill: "#E7EBF3", fontSize: 13 }} width={70} />
          <Tooltip
            formatter={(value: number) => [`${value.toFixed(2)}%`, "Failure rate"]}
            contentStyle={{ background: "#151C2C", border: "1px solid #293347", fontSize: 12 }}
            labelStyle={{ color: "#E7EBF3" }}
          />
          <Bar dataKey="rate" radius={[0, 2, 2, 0]} barSize={28}>
            <Cell fill="#4C8DFF" />
            <Cell fill={currentFailureRate > baselineFailureRate + 5 ? "#E15A45" : "#3FBBA3"} />
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
