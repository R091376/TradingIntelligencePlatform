/**
 * Session alert list helpers: append + trim with lifecycle-preserving pins.
 */

/** Max non-pinned alerts retained in the session feed. */
export const ALERTS_MAX = 150

/**
 * Instance IDs that must never drop stages while the session is alive:
 * focused instance + any remembered per-series overlays.
 *
 * @param {string | null | undefined} focusedInstanceId
 * @param {Map<string, { instanceId?: string }>} overlayMemory
 * @returns {string[]}
 */
export function collectPinnedInstanceIds(focusedInstanceId, overlayMemory) {
  const pinned = new Set()
  if (focusedInstanceId) pinned.add(focusedInstanceId)
  if (overlayMemory) {
    for (const mem of overlayMemory.values()) {
      if (mem?.instanceId) pinned.add(mem.instanceId)
    }
  }
  return [...pinned]
}

/**
 * Keep list newest-first. Always retain every event for pinned instances;
 * fill remaining budget with other alerts up to max (pinned may exceed max).
 *
 * @param {Array} list newest-first
 * @param {{ max?: number, pinnedInstanceIds?: string[] }} opts
 */
export function compactAlerts(list, { max = 150, pinnedInstanceIds = [] } = {}) {
  if (!list?.length) return []
  const pinned = new Set(pinnedInstanceIds.filter(Boolean))
  const pinnedCount = list.reduce(
    (n, a) => n + (a.instanceId && pinned.has(a.instanceId) ? 1 : 0),
    0,
  )
  const budgetOthers = Math.max(0, max - pinnedCount)
  let others = 0
  const out = []
  for (const a of list) {
    if (a.instanceId && pinned.has(a.instanceId)) {
      out.push(a)
    } else if (others < budgetOthers) {
      out.push(a)
      others += 1
    }
  }
  return out
}

/**
 * @param {Array} prev
 * @param {object} alert
 * @param {{ max?: number, pinnedInstanceIds?: string[] }} opts
 */
export function appendAlert(prev, alert, opts = {}) {
  if (!alert) return prev || []
  const list = prev || []
  if (list.some((a) => a.id === alert.id)) return list
  return compactAlerts([alert, ...list], opts)
}
