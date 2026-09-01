"use client";

import { BANKS, type ErrorCode, type PaymentFilters, type PaymentMethod, type PaymentStatus } from "@/lib/payments";

const STATUSES: PaymentStatus[] = ["SUCCESS", "FAILED", "PENDING"];
const METHODS: PaymentMethod[] = ["UPI", "CARD", "NETBANKING", "WALLET"];
const ERROR_CODES: ErrorCode[] = [
  "BANK_TIMEOUT",
  "INSUFFICIENT_FUNDS",
  "NETWORK_ERROR",
  "ISSUER_DECLINED",
  "RATE_LIMIT",
  "UNKNOWN",
];

function Select<T extends string>({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: T | undefined;
  options: readonly T[];
  onChange: (value: T | undefined) => void;
}) {
  return (
    <label className="flex flex-col gap-1 text-xs text-ink-muted">
      {label}
      <select
        value={value ?? ""}
        onChange={(e) => onChange((e.target.value || undefined) as T | undefined)}
        className="border border-base-border bg-base px-2 py-1.5 font-mono text-xs text-ink focus:border-signal-wire focus:outline-none"
      >
        <option value="">all</option>
        {options.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
    </label>
  );
}

export function PaymentsFilters({
  filters,
  onChange,
}: {
  filters: PaymentFilters;
  onChange: (filters: PaymentFilters) => void;
}) {
  return (
    <div className="flex flex-wrap gap-3 border border-base-border bg-base-panel p-4">
      <Select
        label="status"
        value={filters.status}
        options={STATUSES}
        onChange={(status) => onChange({ ...filters, status })}
      />
      <Select
        label="payment method"
        value={filters.paymentMethod}
        options={METHODS}
        onChange={(paymentMethod) => onChange({ ...filters, paymentMethod })}
      />
      <Select
        label="bank"
        value={filters.bank as (typeof BANKS)[number] | undefined}
        options={BANKS}
        onChange={(bank) => onChange({ ...filters, bank })}
      />
      <Select
        label="error code"
        value={filters.errorCode}
        options={ERROR_CODES}
        onChange={(errorCode) => onChange({ ...filters, errorCode })}
      />

      {(filters.status || filters.paymentMethod || filters.bank || filters.errorCode) && (
        <button
          type="button"
          onClick={() => onChange({})}
          className="self-end text-xs text-signal-wire underline underline-offset-2"
        >
          clear filters
        </button>
      )}
    </div>
  );
}
