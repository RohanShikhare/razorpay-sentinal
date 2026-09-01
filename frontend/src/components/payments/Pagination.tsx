export function Pagination({
  page,
  totalPages,
  totalElements,
  onPageChange,
}: {
  page: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (page: number) => void;
}) {
  return (
    <div className="flex items-center justify-between border border-t-0 border-base-border bg-base-panel px-4 py-2.5 text-xs text-ink-muted">
      <span>{totalElements.toLocaleString("en-IN")} total</span>
      <div className="flex items-center gap-3">
        <button
          type="button"
          disabled={page <= 0}
          onClick={() => onPageChange(page - 1)}
          className="text-signal-wire disabled:cursor-not-allowed disabled:text-ink-faint"
        >
          prev
        </button>
        <span className="font-mono">
          page {page + 1} / {Math.max(totalPages, 1)}
        </span>
        <button
          type="button"
          disabled={page + 1 >= totalPages}
          onClick={() => onPageChange(page + 1)}
          className="text-signal-wire disabled:cursor-not-allowed disabled:text-ink-faint"
        >
          next
        </button>
      </div>
    </div>
  );
}
