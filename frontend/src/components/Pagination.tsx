interface PaginationProps {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  onSizeChange: (size: number) => void;
  disabled?: boolean;
}

const PAGE_SIZES = [10, 25, 50] as const;

export function Pagination({
  page,
  size,
  totalElements,
  totalPages,
  onPageChange,
  onSizeChange,
  disabled = false,
}: PaginationProps) {
  const first = totalElements === 0 ? 0 : page * size + 1;
  const last = Math.min((page + 1) * size, totalElements);

  return (
    <nav className="pagination" aria-label="Pagination">
      <span className="muted">
        Showing {first}-{last} of {totalElements}
      </span>
      <div className="pagination-controls">
        <label className="pagination-size">
          <span className="muted small">Per page</span>
          <select value={size} disabled={disabled} onChange={(event) => onSizeChange(Number(event.target.value))}>
            {PAGE_SIZES.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </label>
        <button
          type="button"
          className="button button-secondary"
          disabled={disabled || page <= 0}
          onClick={() => onPageChange(page - 1)}
        >
          Previous
        </button>
        <span aria-live="polite">
          Page {totalPages === 0 ? 0 : page + 1} of {totalPages}
        </span>
        <button
          type="button"
          className="button button-secondary"
          disabled={disabled || page + 1 >= totalPages}
          onClick={() => onPageChange(page + 1)}
        >
          Next
        </button>
      </div>
    </nav>
  );
}
