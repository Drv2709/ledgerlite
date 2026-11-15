/**
 * Where to go after logging in: the page the user was trying to reach, if it is a safe in-app path.
 * Anything else (external URLs, "//evil.com", backslash tricks, control characters) falls back to
 * the dashboard, so the login redirect cannot be abused as an open redirect.
 */
function isSafeInAppPath(path: string): boolean {
  return (
    path.startsWith("/") &&
    !path.startsWith("//") &&
    !path.includes("\\") &&
    !/[\u0000-\u001f]/.test(path)
  );
}

export function redirectTarget(state: unknown): string {
  if (typeof state === "object" && state !== null && "from" in state) {
    const from = state.from;
    if (typeof from === "string" && isSafeInAppPath(from)) {
      return from;
    }
  }
  return "/";
}
