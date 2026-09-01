"use client";

import { SCENARIOS, type SimulationScenario } from "@/lib/simulations";

export function ScenarioSelector({
  value,
  onChange,
  disabled,
}: {
  value: SimulationScenario;
  onChange: (scenario: SimulationScenario) => void;
  disabled: boolean;
}) {
  return (
    <div className="grid gap-2 sm:grid-cols-3">
      {SCENARIOS.map((scenario) => {
        const selected = scenario.value === value;
        return (
          <button
            key={scenario.value}
            type="button"
            disabled={disabled}
            onClick={() => onChange(scenario.value)}
            className={`border px-4 py-3 text-left transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${
              selected
                ? "border-signal-wire bg-signal-wire/10"
                : "border-base-border bg-base-panel hover:border-ink-faint"
            }`}
          >
            <div className={`text-sm font-medium ${selected ? "text-signal-wire" : "text-ink"}`}>
              {scenario.label}
            </div>
            <div className="mt-1 text-xs leading-snug text-ink-muted">{scenario.description}</div>
          </button>
        );
      })}
    </div>
  );
}
