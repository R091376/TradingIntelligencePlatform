/**
 * Live pattern alerts (session-only). Newest first.
 * Click an alert → parent navigates chart + draws overlay.
 */

import { ALERTS_MAX } from '../utils/alertList'

const MAX_VISIBLE = ALERTS_MAX

const STAGE_TONE = {
  detected: 'info',
  confirmed: 'info',
  retested: 'warn',
  strengthened: 'warn',
  succeeded: 'success',
  failed: 'danger',
  expired: 'muted',
}

function stageTone(stage) {
  return STAGE_TONE[(stage || '').toLowerCase()] || 'muted'
}

function formatAlertTime(unixSec) {
  if (!unixSec) return '—'
  try {
    return new Date(unixSec * 1000).toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    })
  } catch {
    return '—'
  }
}

function formatPrice(n) {
  if (n == null || Number.isNaN(Number(n))) return '—'
  const v = Number(n)
  return v >= 100 ? v.toFixed(2) : v.toFixed(v >= 10 ? 2 : 4)
}

function shortSymbol(symbolId, displayName) {
  if (displayName) return displayName
  if (!symbolId) return '?'
  const pipe = symbolId.indexOf('|')
  return pipe >= 0 ? symbolId.slice(pipe + 1) : symbolId
}

export default function AlertsFeed({
  alerts,
  selectedId,
  selectedInstanceId,
  onSelect,
  onClear,
  symbolLabels = {},
}) {
  const items = alerts.slice(0, MAX_VISIBLE)

  return (
    <section className="alerts-feed" aria-label="Pattern alerts">
      <div className="alerts-feed__header">
        <h2 className="alerts-feed__title">Alerts</h2>
        <div className="alerts-feed__header-actions">
          <span className="alerts-feed__count">{items.length}</span>
          {items.length > 0 && (
            <button
              type="button"
              className="alerts-feed__clear"
              onClick={onClear}
              title="Clear session alerts"
            >
              Clear
            </button>
          )}
        </div>
      </div>

      {items.length === 0 ? (
        <p className="alerts-feed__empty">
          Live pattern events appear here while the app is open. Requires Postgres
          patterns enabled on the backend. Click an alert to show the full instance
          lifecycle (reference line + stage markers).
        </p>
      ) : (
        <ul className="alerts-feed__list">
          {items.map((a) => {
            const tone = stageTone(a.stage)
            const active =
              a.id === selectedId ||
              (selectedInstanceId && a.instanceId === selectedInstanceId)
            const focus = a.id === selectedId
            const label = shortSymbol(
              a.symbolId,
              symbolLabels[a.symbolId] || a.displayName,
            )
            return (
              <li key={a.id}>
                <button
                  type="button"
                  className={`alerts-feed__row${active ? ' alerts-feed__row--active' : ''}${focus ? ' alerts-feed__row--focus' : ''}`}
                  onClick={() => onSelect?.(a)}
                >
                  <div className="alerts-feed__row-top">
                    <span className={`alerts-feed__stage alerts-feed__stage--${tone}`}>
                      {(a.stage || 'event').toUpperCase()}
                    </span>
                    <span className="alerts-feed__tf">{a.timeframe}</span>
                  </div>
                  <div className="alerts-feed__row-main">
                    <span className="alerts-feed__symbol">{label}</span>
                    <span className="alerts-feed__pattern">
                      {(a.patternType || 'pattern').replace(/_/g, ' ')}
                      {a.direction ? ` · ${a.direction}` : ''}
                    </span>
                  </div>
                  <div className="alerts-feed__row-meta">
                    <span className="alerts-feed__price">@ {formatPrice(a.price)}</span>
                    <span className="alerts-feed__time">{formatAlertTime(a.time)}</span>
                  </div>
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}

export { ALERTS_MAX }
