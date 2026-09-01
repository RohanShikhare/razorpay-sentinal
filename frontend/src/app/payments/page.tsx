"use client";

import { useCallback, useEffect, useState } from "react";
import { SummaryCards } from "@/components/payments/SummaryCards";
import { GenerateControl } from "@/components/payments/GenerateControl";
import { PaymentsFilters } from "@/components/payments/PaymentsFilters";
import { PaymentsTable } from "@/components/payments/PaymentsTable";
import { Pagination } from "@/components/payments/Pagination";
import {
  fetchPaymentSummary,
  fetchPayments,
  generatePayments,
  type Payment,
  type PaymentFilters,
  type PaymentSummary,
} from "@/lib/payments";

const PAGE_SIZE = 20;

export default function PaymentsPage() {
  const [summary, setSummary] = useState<PaymentSummary | null>(null);
  const [payments, setPayments] = useState<Payment[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState<PaymentFilters>({});

  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [summaryData, pageData] = await Promise.all([
        fetchPaymentSummary(),
        fetchPayments(page, PAGE_SIZE, filters),
      ]);
      setSummary(summaryData);
      setPayments(pageData.content);
      setTotalPages(pageData.totalPages);
      setTotalElements(pageData.totalElements);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load payments");
    } finally {
      setLoading(false);
    }
  }, [page, filters]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  async function handleGenerate(count: number) {
    setGenerating(true);
    setError(null);
    setNotice(null);
    try {
      const result = await generatePayments({ count });
      setNotice(`Generated ${result.generatedCount.toLocaleString("en-IN")} payments.`);
      setPage(0);
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to generate payments");
    } finally {
      setGenerating(false);
    }
  }

  function handleFiltersChange(next: PaymentFilters) {
    setFilters(next);
    setPage(0);
  }

  return (
    <main className="mx-auto max-w-6xl px-6 py-10">
      <p className="font-mono text-xs text-signal-wire">Module 1 · Payment Data Engine</p>
      <h1 className="mt-2 text-2xl font-semibold tracking-tight text-ink">Payments</h1>
      <p className="mt-2 max-w-xl text-sm text-ink-muted">
        Synthetic payment traffic used as the baseline for every later module — incident
        detection, revenue-at-risk, and recovery all read from this dataset.
      </p>

      <div className="mt-6">
        <GenerateControl onGenerate={handleGenerate} generating={generating} />
      </div>

      {error && (
        <div className="mt-4 border border-signal-critical/40 bg-signal-critical/10 px-4 py-3 text-sm text-signal-critical">
          {error}
        </div>
      )}
      {notice && !error && (
        <div className="mt-4 border border-signal-nominal/40 bg-signal-nominal/10 px-4 py-3 text-sm text-signal-nominal">
          {notice}
        </div>
      )}

      <div className="mt-6">
        {summary && summary.totalTransactions > 0 ? (
          <SummaryCards summary={summary} />
        ) : (
          !loading && (
            <div className="border border-base-border bg-base-panel px-4 py-6 text-sm text-ink-muted">
              No payments yet — generate a synthetic dataset above to get started.
            </div>
          )
        )}
      </div>

      <div className="mt-6">
        <PaymentsFilters filters={filters} onChange={handleFiltersChange} />
      </div>

      <div className="mt-3">
        {loading ? (
          <div className="border border-base-border bg-base-panel px-4 py-10 text-center text-sm text-ink-muted">
            loading payments...
          </div>
        ) : (
          <>
            <PaymentsTable payments={payments} />
            <Pagination
              page={page}
              totalPages={totalPages}
              totalElements={totalElements}
              onPageChange={setPage}
            />
          </>
        )}
      </div>
    </main>
  );
}
