interface SkeletonProps {
  width?: string;
  height?: string;
}

/** A shimmering placeholder block shown while data loads. */
export function Skeleton({ width = '100%', height = '1rem' }: SkeletonProps) {
  return <span className="skeleton" style={{ width, height }} aria-hidden="true" />;
}
