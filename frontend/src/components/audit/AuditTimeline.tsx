"use client";

import { useState, useEffect, useCallback, useMemo } from "react";
import {
  fetchIncidentAuditTimeline,
  verifyIncidentAuditChain,
  type AuditTimelineResponse,
  type AuditVerificationResponse,
  type AuditEventDto,
  type AuditActorType,
  type AuditEventCategory,
  type AuditSeverity,
  type AuditStatus,
} from "@/lib/audit";

interface AuditTimelineProps {
  incidentId: number;
}

function formatInr(value: number | string | undefined | null): string {
  if (value === undefined || value === null) return "₹0";
  const n = typeof value === "string" ? Number(value) : value;
  if (Number.isNaN(n)) return "₹0";
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(n);
}

function formatTimestamp(isoString: string): { time: string; date: string; relative: string } {
  try {
    const d = new Date(isoString);
    const time = d.toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
    const date = d.toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
    
    const diffSecs = Math.floor((Date.now() - d.getTime()) / 1000);
    let relative = "just now";
    if (diffSecs > 60) {
      const mins = Math.floor(diffSecs / 60);
      relative = `${mins}m ago`;
    } else if (diffSecs > 0) {
      relative = `${diffSecs}s ago`;
    }
    return { time, date, relative };
  } catch {
    return { time: isoString, date: "", relative: "" };
  }
}

// Inline SVG Icon components
function IconShieldCheck({ className = "w-4 h-4" }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
    </svg>
  );
}

function IconShieldAlert({ className = "w-4 h-4" }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
    </svg>
  );
}

function IconShield({ className = "w-4 h-4" }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 3.04A11.955 11.955 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016A11.955 11.955 0 0112 3.04z" />
    </svg>
  );
}

function IconClock({ className = "w-3 h-3" }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  );
}

function IconSparkles({ className = "w-3 h-3" }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z" />
    </svg>
  );
}

function IconRotateCcw({ className = "w-3 h-3" }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
    </svg>
  );
}

function IconCpu({ className = "w-3 h-3" }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 3v2m6-2v2M9 19v2m6-2v2M5 9H3m2 6H3m18-6h-2m2 6h-2M7 19h10a2 2 0 002-2V7a2 2 0 00-2-2H7a2 2 0 00-2 2v10a2 2 0 002 2zM9 9h6v6H9V9z" />
    </svg>
  );
}

function IconCheckCircle({ className = "w-3 h-3" }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  );
}

function IconXCircle({ className = "w-3 h-3" }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  );
}

function IconAlertTriangle({ className = "w-3 h-3" }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
    </svg>
  );
}

function IconFileText({ className = "w-3 h-3" }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
    </svg>
  );
}

function IconLayers({ className = "w-5 h-5" }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
    </svg>
  );
}

function IconSearch({ className = "w-3.5 h-3.5" }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
    </svg>
  );
}

function IconRefresh({ className = "w-4 h-4" }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
    </svg>
  );
}

function IconChevronDown({ className = "w-3.5 h-3.5" }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7" />
    </svg>
  );
}

function IconChevronRight({ className = "w-3.5 h-3.5" }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5l7 7-7 7" />
    </svg>
  );
}

function IconCopy({ className = "w-3 h-3" }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
    </svg>
  );
}

function IconCheck({ className = "w-3 h-3" }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5 13l4 4L19 7" />
    </svg>
  );
}

function actorBadge(actorType: AuditActorType, actorId: string) {
  switch (actorType) {
    case "AI":
      return {
        label: "AI ADVISORY",
        id: actorId,
        badge: "bg-purple-500/15 text-purple-300 border-purple-500/30",
        Icon: IconSparkles,
        ring: "ring-purple-500/30",
      };
    case "POLICY_ENGINE":
      return {
        label: "POLICY GATE",
        id: actorId,
        badge: "bg-blue-500/15 text-blue-300 border-blue-500/30",
        Icon: IconShield,
        ring: "ring-blue-500/30",
      };
    case "RECOVERY_ENGINE":
      return {
        label: "EXECUTION ENGINE",
        id: actorId,
        badge: "bg-emerald-500/15 text-emerald-300 border-emerald-500/30",
        Icon: IconRotateCcw,
        ring: "ring-emerald-500/30",
      };
    case "HUMAN_OPERATOR":
    case "USER":
      return {
        label: "HUMAN OPERATOR",
        id: actorId,
        badge: "bg-amber-500/15 text-amber-300 border-amber-500/30",
        Icon: IconCpu,
        ring: "ring-amber-500/30",
      };
    case "SYSTEM":
    default:
      return {
        label: "SYSTEM",
        id: actorId,
        badge: "bg-zinc-500/15 text-zinc-300 border-zinc-500/30",
        Icon: IconClock,
        ring: "ring-zinc-500/30",
      };
  }
}

function statusBadge(status: AuditStatus, severity: AuditSeverity) {
  if (status === "SUCCESS") {
    return {
      label: "SUCCESS",
      classes: "bg-emerald-500/10 text-emerald-400 border-emerald-500/20",
      Icon: IconCheckCircle,
    };
  }
  if (status === "FAILURE" || severity === "CRITICAL") {
    return {
      label: "FAILURE",
      classes: "bg-rose-500/10 text-rose-400 border-rose-500/20",
      Icon: IconXCircle,
    };
  }
  if (status === "BLOCKED" || severity === "WARN") {
    return {
      label: "BLOCKED / WARN",
      classes: "bg-amber-500/10 text-amber-400 border-amber-500/20",
      Icon: IconAlertTriangle,
    };
  }
  return {
    label: "INFO",
    classes: "bg-blue-500/10 text-blue-400 border-blue-500/20",
    Icon: IconFileText,
  };
}

export function AuditTimeline({ incidentId }: AuditTimelineProps) {
  const [timeline, setTimeline] = useState<AuditTimelineResponse | null>(null);
  const [verification, setVerification] = useState<AuditVerificationResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [verifying, setVerifying] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [categoryFilter, setCategoryFilter] = useState<string>("ALL");
  const [searchQuery, setSearchQuery] = useState("");
  const [expandedEvents, setExpandedEvents] = useState<Set<number>>(new Set());
  const [copiedHash, setCopiedHash] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const [tl, ver] = await Promise.all([
        fetchIncidentAuditTimeline(incidentId),
        verifyIncidentAuditChain(incidentId),
      ]);
      setTimeline(tl);
      setVerification(ver);
    } catch (err: any) {
      console.error("Failed to load audit trail:", err);
      setError(err?.message || "Failed to load audit trail data.");
    } finally {
      setLoading(false);
    }
  }, [incidentId]);

  const handleVerifyChain = async () => {
    try {
      setVerifying(true);
      const ver = await verifyIncidentAuditChain(incidentId);
      setVerification(ver);
    } catch (err: any) {
      console.error("Failed to verify audit chain:", err);
    } finally {
      setVerifying(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [loadData]);

  const toggleExpand = (id: number) => {
    setExpandedEvents((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedHash(text);
    setTimeout(() => setCopiedHash(null), 2000);
  };

  const filteredEvents = useMemo(() => {
    if (!timeline?.events) return [];
    return timeline.events.filter((e) => {
      const matchCategory =
        categoryFilter === "ALL" || e.category === categoryFilter;
      const matchSearch =
        searchQuery === "" ||
        e.eventType.toLowerCase().includes(searchQuery.toLowerCase()) ||
        e.summary.toLowerCase().includes(searchQuery.toLowerCase()) ||
        (e.reason && e.reason.toLowerCase().includes(searchQuery.toLowerCase())) ||
        e.actorId.toLowerCase().includes(searchQuery.toLowerCase());
      return matchCategory && matchSearch;
    });
  }, [timeline, categoryFilter, searchQuery]);

  const categories: { label: string; value: string; count?: number }[] = useMemo(() => {
    if (!timeline?.events) return [{ label: "ALL", value: "ALL" }];
    const counts: Record<string, number> = {};
    timeline.events.forEach((e) => {
      counts[e.category] = (counts[e.category] || 0) + 1;
    });
    return [
      { label: "ALL", value: "ALL", count: timeline.events.length },
      { label: "DETECTION", value: "DETECTION", count: counts["DETECTION"] || 0 },
      { label: "FINANCIAL", value: "FINANCIAL", count: counts["FINANCIAL"] || 0 },
      { label: "DIAGNOSIS", value: "DIAGNOSIS", count: counts["DIAGNOSIS"] || 0 },
      { label: "INVESTIGATION", value: "INVESTIGATION", count: counts["INVESTIGATION"] || 0 },
      { label: "STRATEGY", value: "STRATEGY", count: counts["STRATEGY"] || 0 },
      { label: "POLICY", value: "POLICY", count: counts["POLICY"] || 0 },
      { label: "EXECUTION", value: "EXECUTION", count: counts["EXECUTION"] || 0 },
    ];
  }, [timeline]);

  const summary = timeline?.decisionChainSummary;

  return (
    <section id="audit-trail" className="space-y-6">
      {/* Section Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-base-subtle border border-base-border rounded-xl p-5 shadow-lg">
        <div>
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-lg bg-indigo-500/10 border border-indigo-500/20 text-indigo-400">
              <IconLayers className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-xl font-semibold text-ink-primary">
                  Audit Trail &amp; Decision History
                </h2>
                <span className="text-xs px-2 py-0.5 rounded-full font-mono bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                  MODULE 11
                </span>
              </div>
              <p className="text-xs text-ink-muted mt-0.5">
                Immutable, tamper-evident chronological timeline of all AI proposals, policy decisions, and execution outcomes
              </p>
            </div>
          </div>
        </div>

        {/* Verification Status & Refresh */}
        <div className="flex items-center gap-3">
          {verification?.valid ? (
            <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-emerald-500/10 border border-emerald-500/25 text-emerald-400 text-xs font-mono">
              <IconShieldCheck className="w-4 h-4 text-emerald-400 animate-pulse" />
              <span>CHAIN INTEGRITY: VERIFIED</span>
            </div>
          ) : verification && !verification.valid ? (
            <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-rose-500/10 border border-rose-500/25 text-rose-400 text-xs font-mono">
              <IconShieldAlert className="w-4 h-4 text-rose-400" />
              <span>CHAIN TAMPERING DETECTED</span>
            </div>
          ) : null}

          <button
            onClick={handleVerifyChain}
            disabled={verifying}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-base-raised hover:bg-base-border border border-base-border text-ink-secondary text-xs font-medium transition-colors"
            title="Verify SHA-256 cryptographic chain"
          >
            <IconShield className={`w-3.5 h-3.5 ${verifying ? "animate-spin text-accent-indigo" : ""}`} />
            <span>{verifying ? "Verifying..." : "Verify Hash Chain"}</span>
          </button>

          <button
            onClick={loadData}
            disabled={loading}
            className="p-1.5 rounded-lg bg-base-raised hover:bg-base-border border border-base-border text-ink-muted hover:text-ink-primary transition-colors"
            title="Refresh Audit Timeline"
          >
            <IconRefresh className={`w-4 h-4 ${loading ? "animate-spin" : ""}`} />
          </button>
        </div>
      </div>

      {/* Decision Chain Summary Banner */}
      {summary && (
        <div className="bg-base-subtle border border-base-border rounded-xl p-5 shadow-sm space-y-3">
          <div className="flex items-center justify-between text-xs font-medium text-ink-muted">
            <span className="uppercase tracking-wider">End-to-End Decision Chain Lifecycle</span>
            <span className="font-mono text-ink-secondary">
              Stage: <strong className="text-accent-indigo">{summary.currentStage}</strong>
            </span>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-7 gap-2">
            {/* 1. Detected */}
            <div className={`p-3 rounded-lg border flex flex-col justify-between ${
              summary.detected
                ? "bg-blue-500/10 border-blue-500/30 text-blue-300"
                : "bg-base-raised border-base-border text-ink-muted opacity-50"
            }`}>
              <div className="text-[10px] font-mono uppercase tracking-wider text-ink-muted">1. Detection</div>
              <div className="font-semibold text-xs mt-1">Detected</div>
              <div className="text-[10px] mt-1 text-ink-muted font-mono">System Signal</div>
            </div>

            {/* 2. Diagnosed */}
            <div className={`p-3 rounded-lg border flex flex-col justify-between ${
              summary.diagnosed
                ? "bg-purple-500/10 border-purple-500/30 text-purple-300"
                : "bg-base-raised border-base-border text-ink-muted opacity-50"
            }`}>
              <div className="text-[10px] font-mono uppercase tracking-wider text-ink-muted">2. AI Diagnosis</div>
              <div className="font-semibold text-xs mt-1">Diagnosed</div>
              <div className="text-[10px] mt-1 text-ink-muted font-mono">AI Advisory</div>
            </div>

            {/* 3. Investigated */}
            <div className={`p-3 rounded-lg border flex flex-col justify-between ${
              summary.investigated
                ? "bg-purple-500/10 border-purple-500/30 text-purple-300"
                : "bg-base-raised border-base-border text-ink-muted opacity-50"
            }`}>
              <div className="text-[10px] font-mono uppercase tracking-wider text-ink-muted">3. Investigation</div>
              <div className="font-semibold text-xs mt-1">Investigated</div>
              <div className="text-[10px] mt-1 text-ink-muted font-mono">Hypotheses Tested</div>
            </div>

            {/* 4. Strategy Proposed */}
            <div className={`p-3 rounded-lg border flex flex-col justify-between ${
              summary.strategyProposed
                ? "bg-indigo-500/10 border-indigo-500/30 text-indigo-300"
                : "bg-base-raised border-base-border text-ink-muted opacity-50"
            }`}>
              <div className="text-[10px] font-mono uppercase tracking-wider text-ink-muted">4. Strategy</div>
              <div className="font-semibold text-xs mt-1">Proposed</div>
              <div className="text-[10px] mt-1 text-ink-muted font-mono">AI Proposal</div>
            </div>

            {/* 5. Policy Decided */}
            <div className={`p-3 rounded-lg border flex flex-col justify-between ${
              summary.policyApproved
                ? "bg-blue-500/10 border-blue-500/30 text-blue-300"
                : "bg-base-raised border-base-border text-ink-muted opacity-50"
            }`}>
              <div className="text-[10px] font-mono uppercase tracking-wider text-ink-muted">5. Policy Gate</div>
              <div className="font-semibold text-xs mt-1">{summary.policyApproved ? "Approved" : "Evaluated"}</div>
              <div className="text-[10px] mt-1 text-ink-muted font-mono">Policy Decides</div>
            </div>

            {/* 6. Execution Obeys */}
            <div className={`p-3 rounded-lg border flex flex-col justify-between ${
              summary.recoveryExecuted
                ? "bg-emerald-500/10 border-emerald-500/30 text-emerald-300"
                : "bg-base-raised border-base-border text-ink-muted opacity-50"
            }`}>
              <div className="text-[10px] font-mono uppercase tracking-wider text-ink-muted">6. Execution</div>
              <div className="font-semibold text-xs mt-1">{summary.recoveryExecuted ? "Executed" : "Pending"}</div>
              <div className="text-[10px] mt-1 text-ink-muted font-mono">Simulation Bound</div>
            </div>

            {/* 7. Actual Recovery */}
            <div className={`p-3 rounded-lg border flex flex-col justify-between ${
              summary.recoveredAmount > 0
                ? "bg-emerald-500/15 border-emerald-500/40 text-emerald-300"
                : "bg-base-raised border-base-border text-ink-muted opacity-50"
            }`}>
              <div className="text-[10px] font-mono uppercase tracking-wider text-ink-muted">7. Outcome</div>
              <div className="font-semibold text-xs mt-1 text-emerald-400">
                {formatInr(summary.recoveredAmount)}
              </div>
              <div className="text-[10px] mt-1 text-ink-muted font-mono">Recovered</div>
            </div>
          </div>

          <div className="text-center pt-1 text-[11px] text-ink-muted font-mono">
            AI Proposes &bull; Policy Decides &bull; Execution Obeys &bull; Audit Records
          </div>
        </div>
      )}

      {/* Filter and Search Bar */}
      <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
        {/* Category Tabs */}
        <div className="flex flex-wrap items-center gap-1.5 p-1 bg-base-subtle border border-base-border rounded-lg">
          {categories.map((cat) => (
            <button
              key={cat.value}
              onClick={() => setCategoryFilter(cat.value)}
              className={`px-2.5 py-1 rounded text-xs font-medium transition-all ${
                categoryFilter === cat.value
                  ? "bg-accent-indigo text-white shadow-sm"
                  : "text-ink-secondary hover:text-ink-primary hover:bg-base-raised"
              }`}
            >
              {cat.label}
              {cat.count !== undefined && cat.count > 0 && (
                <span className={`ml-1.5 px-1 py-0.2 rounded text-[10px] font-mono ${
                  categoryFilter === cat.value ? "bg-white/20 text-white" : "bg-base-border text-ink-muted"
                }`}>
                  {cat.count}
                </span>
              )}
            </button>
          ))}
        </div>

        {/* Search */}
        <div className="relative min-w-[220px]">
          <IconSearch className="w-3.5 h-3.5 absolute left-3 top-1/2 -translate-y-1/2 text-ink-muted" />
          <input
            type="text"
            placeholder="Search events, summaries, actors..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-9 pr-3 py-1.5 rounded-lg bg-base-subtle border border-base-border text-xs text-ink-primary placeholder-ink-muted focus:outline-none focus:border-accent-indigo"
          />
        </div>
      </div>

      {/* Error state */}
      {error && (
        <div className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-400 text-xs flex items-center gap-2">
          <IconAlertTriangle className="w-4 h-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Timeline List */}
      {loading && !timeline ? (
        <div className="text-center py-12 text-ink-muted text-xs font-mono animate-pulse">
          Loading cryptographic audit trail...
        </div>
      ) : filteredEvents.length === 0 ? (
        <div className="text-center py-12 bg-base-subtle border border-base-border rounded-xl text-ink-muted text-xs">
          No audit events found matching the filter criteria.
        </div>
      ) : (
        <div className="relative pl-6 space-y-6 before:absolute before:left-2.5 before:top-3 before:bottom-3 before:w-0.5 before:bg-base-border">
          {filteredEvents.map((event) => {
            const actor = actorBadge(event.actorType, event.actorId);
            const status = statusBadge(event.status, event.severity);
            const isExpanded = expandedEvents.has(event.id);
            const ts = formatTimestamp(event.timestamp);

            let parsedMetadata: any = null;
            if (event.metadataJson) {
              try {
                parsedMetadata = JSON.parse(event.metadataJson);
              } catch {
                parsedMetadata = event.metadataJson;
              }
            }

            return (
              <div key={event.id} className="relative group">
                {/* Timeline Bullet Node */}
                <div className={`absolute -left-6 top-3 w-5 h-5 rounded-full border bg-base-default flex items-center justify-center transition-all ${
                  actor.ring
                } ${
                  event.status === "SUCCESS"
                    ? "border-emerald-500/50 text-emerald-400"
                    : event.status === "FAILURE"
                    ? "border-rose-500/50 text-rose-400"
                    : event.status === "BLOCKED"
                    ? "border-amber-500/50 text-amber-400"
                    : "border-blue-500/50 text-blue-400"
                }`}>
                  <div className={`w-2 h-2 rounded-full ${
                    event.status === "SUCCESS" ? "bg-emerald-400" :
                    event.status === "FAILURE" ? "bg-rose-400" :
                    event.status === "BLOCKED" ? "bg-amber-400" : "bg-blue-400"
                  }`} />
                </div>

                {/* Event Card */}
                <div className="bg-base-subtle hover:bg-base-raised/60 border border-base-border rounded-xl p-4 transition-all shadow-sm">
                  {/* Top Row: Event Type, Actor, Timestamp, Status */}
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                    <div className="flex flex-wrap items-center gap-2">
                      {/* Event Type */}
                      <span className="font-mono text-xs font-semibold text-ink-primary">
                        {event.eventType}
                      </span>

                      {/* Actor Badge */}
                      <span className={`flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-mono border ${actor.badge}`}>
                        <actor.Icon className="w-3 h-3" />
                        <span>{actor.label}: {event.actorId}</span>
                      </span>

                      {/* Category */}
                      <span className="px-1.5 py-0.5 rounded text-[10px] font-mono bg-base-border text-ink-muted">
                        {event.category}
                      </span>
                    </div>

                    <div className="flex items-center gap-2">
                      {/* Status */}
                      <span className={`flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-mono border ${status.classes}`}>
                        <status.Icon className="w-3 h-3" />
                        <span>{event.status}</span>
                      </span>

                      {/* Timestamp */}
                      <div className="text-[11px] text-ink-muted font-mono flex items-center gap-1" title={`${ts.date} ${ts.time}`}>
                        <IconClock className="w-3 h-3" />
                        <span>{ts.time}</span>
                        <span className="text-[10px] text-ink-muted">({ts.relative})</span>
                      </div>
                    </div>
                  </div>

                  {/* Summary */}
                  <div className="mt-2 text-xs text-ink-primary font-medium">
                    {event.summary}
                  </div>

                  {/* Reason if available */}
                  {event.reason && (
                    <div className="mt-1 text-xs text-ink-muted bg-base-raised/70 border border-base-border/50 rounded-lg p-2 font-mono">
                      <span className="text-ink-secondary font-semibold">Reason:</span> {event.reason}
                    </div>
                  )}

                  {/* Expand/Collapse Details */}
                  <div className="mt-3 pt-2.5 border-t border-base-border/60 flex items-center justify-between text-[11px] text-ink-muted">
                    <button
                      onClick={() => toggleExpand(event.id)}
                      className="flex items-center gap-1 text-accent-indigo hover:text-indigo-300 transition-colors font-medium"
                    >
                      {isExpanded ? <IconChevronDown className="w-3.5 h-3.5" /> : <IconChevronRight className="w-3.5 h-3.5" />}
                      <span>{isExpanded ? "Hide Metadata & Hash Chain" : "View Structured Metadata & Hash Chain"}</span>
                    </button>

                    {/* Truncated Hash Badge */}
                    <div className="flex items-center gap-1.5 font-mono text-[10px] text-ink-muted">
                      <span>SHA256:</span>
                      <span className="text-ink-secondary" title={event.eventHash}>
                        {event.eventHash.slice(0, 8)}...{event.eventHash.slice(-6)}
                      </span>
                      <button
                        onClick={() => copyToClipboard(event.eventHash)}
                        className="hover:text-ink-primary transition-colors"
                        title="Copy SHA-256 event hash"
                      >
                        {copiedHash === event.eventHash ? (
                          <IconCheck className="w-3 h-3 text-emerald-400" />
                        ) : (
                          <IconCopy className="w-3 h-3" />
                        )}
                      </button>
                    </div>
                  </div>

                  {/* Expanded Inspector */}
                  {isExpanded && (
                    <div className="mt-3 space-y-3 pt-2 border-t border-base-border/40 text-xs">
                      {/* Cryptographic Linkage Info */}
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 bg-base-raised rounded-lg p-3 font-mono text-[11px] border border-base-border">
                        <div>
                          <div className="text-[10px] text-ink-muted uppercase">Event ID</div>
                          <div className="text-ink-secondary break-all font-mono">{event.eventId}</div>
                        </div>
                        <div>
                          <div className="text-[10px] text-ink-muted uppercase">Previous Event Hash (Link)</div>
                          <div className="text-ink-secondary break-all font-mono">
                            {event.previousEventHash ? event.previousEventHash : "GENESIS (Root Event)"}
                          </div>
                        </div>
                      </div>

                      {/* Structured Metadata viewer */}
                      {parsedMetadata && (
                        <div>
                          <div className="text-[10px] uppercase font-mono text-ink-muted mb-1 font-semibold">
                            Structured Payload Metadata
                          </div>
                          <div className="bg-base-default rounded-lg p-3 border border-base-border overflow-x-auto max-h-60">
                            {typeof parsedMetadata === "object" ? (
                              <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-1.5 text-[11px] font-mono">
                                {Object.entries(parsedMetadata).map(([key, val]) => (
                                  <div key={key} className="flex items-baseline justify-between border-b border-base-border/30 pb-0.5">
                                    <span className="text-ink-muted">{key}:</span>
                                    <span className="text-ink-secondary font-semibold">
                                      {typeof val === "object" ? JSON.stringify(val) : String(val)}
                                    </span>
                                  </div>
                                ))}
                              </div>
                            ) : (
                              <pre className="text-[11px] text-ink-secondary font-mono whitespace-pre-wrap">
                                {String(parsedMetadata)}
                              </pre>
                            )}
                          </div>
                        </div>
                      )}
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}
