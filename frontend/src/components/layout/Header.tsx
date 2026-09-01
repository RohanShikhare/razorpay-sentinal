import Link from "next/link";
import { NAV_ITEMS } from "@/lib/navigation";

export function Header() {
  return (
    <header className="border-b border-base-border bg-base/95 backdrop-blur supports-[backdrop-filter]:bg-base/80 sticky top-0 z-10">
      <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
        <Link href="/" className="flex items-baseline gap-2.5">
          <span className="h-2 w-2 rounded-full bg-signal-nominal" aria-hidden />
          <span className="text-[15px] font-semibold tracking-tight text-ink">
            Razorpay Sentinel
          </span>
          <span className="hidden text-sm text-ink-faint sm:inline">
            AI Payment Incident Commander
          </span>
        </Link>

        <nav className="flex items-center gap-1">
          {NAV_ITEMS.map((item) =>
            item.available ? (
              <Link
                key={item.href}
                href={item.href}
                className="rounded px-3 py-1.5 text-sm text-ink hover:bg-base-raised"
              >
                {item.label}
              </Link>
            ) : (
              <span
                key={item.href}
                title="Planned for a later module"
                className="cursor-default rounded px-3 py-1.5 text-sm text-ink-faint"
              >
                {item.label}
              </span>
            )
          )}
        </nav>
      </div>
    </header>
  );
}
