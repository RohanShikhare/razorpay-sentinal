import { WorkflowChain } from "@/components/WorkflowChain";
import { BackendStatus } from "@/components/BackendStatus";
import { ModuleStatus } from "@/components/ModuleStatus";

export default function DashboardPage() {
  return (
    <main>
      <section className="relative overflow-hidden border-b border-base-border">
        <div
          className="pointer-events-none absolute inset-0 bg-grid bg-grid opacity-[0.35]"
          style={{
            maskImage: "linear-gradient(to bottom, black, transparent)",
            WebkitMaskImage: "linear-gradient(to bottom, black, transparent)",
          }}
          aria-hidden
        />
        <div className="relative mx-auto max-w-6xl px-6 py-16">
          <p className="font-mono text-xs text-signal-wire">Module 2 · Incident Simulator</p>
          <h1 className="mt-4 max-w-2xl text-3xl font-semibold leading-tight tracking-tight text-ink sm:text-4xl">
            Payment failures, explained before they become losses.
          </h1>
          <p className="mt-4 max-w-xl text-[15px] leading-relaxed text-ink-muted">
            Sentinel watches Razorpay payment traffic for abnormal failure patterns, works out why
            they&apos;re happening and what revenue is at risk, then proposes a recovery strategy
            that a deterministic policy engine has to approve before anything runs.
          </p>
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-6 py-12">
        <h2 className="text-sm font-medium text-ink">Operating pipeline</h2>
        <p className="mt-1 text-sm text-ink-muted">
          Every incident moves through the same seven stages, in order.
        </p>
        <div className="mt-4">
          <WorkflowChain />
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-6 pb-16">
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-[2fr_1fr]">
          <div className="border border-base-border bg-base-panel p-6">
            <h2 className="text-sm font-medium text-ink">What exists right now</h2>
            <p className="mt-2 max-w-lg text-sm leading-relaxed text-ink-muted">
              Sentinel can generate and query synthetic payment data (
              <a href="/payments" className="text-signal-wire underline underline-offset-2">
                Payments
              </a>
              ) and deliberately degrade it into reproducible incident scenarios (
              <a href="/simulator" className="text-signal-wire underline underline-offset-2">
                Simulator
              </a>
              ). Incident detection, revenue-at-risk, AI diagnosis, policy gates, and recovery
              execution are not implemented yet — the pipeline above describes where they&apos;ll
              sit.
            </p>
          </div>

          <div className="flex flex-col gap-6">
            <BackendStatus />
            <ModuleStatus />
          </div>
        </div>
      </section>
    </main>
  );
}
