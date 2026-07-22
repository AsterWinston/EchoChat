export function convKey(targetId: string, sessionType: string): string {
  return `${sessionType}:${targetId}`
}
