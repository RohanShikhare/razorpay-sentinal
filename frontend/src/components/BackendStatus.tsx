"use client";

import { useEffect, useState } from "react";
import { fetchHealth, type BackendConnection } from "@/lib/api";
import { API_BASE_URL } from "@/lib/config";

const DOT_COLOR: Record<BackendConnection["state"], string> = {
  loading: "bg-signal-watch",
  online: "bg-signal-nominal",
  offline: "bg-signal-critical",
};

const LABEL: Record<BackendConnection["state"], string> = {
  loading: "Connecting",
  online: "Connected",
  offline: "Unreachable",
};

export function BackendStatus() {
  const [connection, setConnection] = useState<BackendConnection>({ state: "loading" });

  useEffect(() => {
    let cancelled = false;

    fetchHealth()
      .then((data) => {
        if (!cancelled) setConnection({ state: "online", data });
      })
      .catch((err: Error) => {
        if (!cancelled) setConnection({ state: "offline", error: err.message });
      });

    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="border border-base-border bg-base-panel">
      <div className="flex items-center justify-between border-b border-base-border px-4 py-3">
        <span className="text-sm font-medium text-ink">Backend connection</span>
        <span className="flex items-center gap-2 text-xs text-ink-muted">
          <span className={`h-1.5 w-1.5 rounded-full ${DOT_COLOR[connection.state]}`} aria-hidden />
          {LABEL[connection.state]}
        </span>
      </div>

      <div className="px-4 py-3 font-mono text-xs leading-relaxed text-ink-muted">
        <div>
          target <span className="text-ink">{API_BASE_URL}/api/v1/health</span>
        </div>

        {connection.state === "loading" && <div className="mt-1 text-ink-faint">awaiting response...</div>}

        {connection.state === "online" && (
          <div className="mt-1 space-y-0.5">
            <div>
              status <span className="text-signal-nominal">{connection.data.status}</span>
            </div>
            <div>
              service <span className="text-ink">{connection.data.service}</span>
            </div>
            <div>
              component <span className="text-ink">{connection.data.module}</span>
            </div>
            <div>
              checked <span className="text-ink">{new Date(connection.data.timestamp).toLocaleTimeString()}</span>
            </div>
          </div>
        )}

        {connection.state === "offline" && (
          <div className="mt-1 space-y-1">
            <div className="text-signal-critical">{connection.error}</div>
            <div className="text-ink-faint">start the backend, then reload this page</div>
          </div>
        )}
      </div>
    </div>
  );
}
