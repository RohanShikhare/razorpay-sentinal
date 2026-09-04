import { WorkflowChain } from "@/components/WorkflowChain";
import { BackendStatus } from "@/components/BackendStatus";
import { ModuleStatus } from "@/components/ModuleStatus";

export default function HomePage() {
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
          <p className="font-mono text-xs text-signal-wire">Track 3: AI Revenue Recovery · Complete Incident Commander</p>
          <h1 className="mt-4 max-w-2xl text-3xl font-semibold leading-tight tracking-tight text-ink sm:text-4xl">
            Payment failures, explained before they become losses.
          </h1>
          <p className="mt-4 max-w-xl text-[15px] leading-relaxed text-ink-muted">
            Sentinel watches Razorpay payment traffic for abnormal failure patterns, works out why
            they&apos;re happening and what revenue is at risk, then proposes a recovery strategy
            that a deterministic policy engine has to approve before anything runs.
          </p>
          <a
            href="/command-center"
            className="mt-6 inline-block border border-signal-wire/50 bg-signal-wire/10 px-4 py-2 text-sm font-medium text-signal-wire hover:bg-signal-wire/20"
          >
            Open Command Center →
          </a>
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-6 py-12">
        <h2 className="text-sm font-medium text-ink">Operating pipeline</h2>
        <p className="mt-1 text-sm text-ink-muted">
          Every incident moves through the core lifecycle stages, in order.
        </p>
        <div className="mt-4">
          <WorkflowChain />
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-6 pb-16">
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-[2fr_1fr]">
          <div className="border border-base-border bg-base-panel p-6">
            <h2 className="text-sm font-medium text-ink">End-to-End Capabilities</h2>
            <p className="mt-2 max-w-lg text-sm leading-relaxed text-ink-muted">
              Sentinel provides a complete, policy-gated incident lifecycle: synthetic payment generation (
              <a href="/payments" className="text-signal-wire underline underline-offset-2">
                Payments
              </a>
              ), controlled incident simulation (
              <a href="/simulator" className="text-signal-wire underline underline-offset-2">
                Simulator
              </a>
              ), real-time monitoring and anomaly detection (
              <a href="/monitoring" className="text-signal-wire underline underline-offset-2">
                Monitoring
              </a>{" "}
              /{" "}
              <a href="/incidents" className="text-signal-wire underline underline-offset-2">
                Incidents
              </a>
              ), financial quantification of revenue-at-risk, advisory AI diagnosis, root-cause investigation, bounded recovery strategy formulation, deterministic 12-rule policy validation, simulated recovery execution with circuit breakers, tamper-evident SHA-256 audit logging, a controlled Safety Lab (
              <a href="/safety" className="text-signal-wire underline underline-offset-2">
                Safety
              </a>
              ), and empirical benchmarks against naive baselines (
              <a href="/evaluation" className="text-signal-wire underline underline-offset-2">
                Evaluation
              </a>
              ). Open the{" "}
              <a href="/command-center" className="text-signal-wire font-semibold underline underline-offset-2">
                Command Center
              </a>{" "}
              for the unified judge control room.
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
