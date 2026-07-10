import { useState } from 'react'

function statusClass(status) {
  const s = (status || '').toString().toUpperCase()
  if (s === 'READY') return 'badge-ready'
  if (s === 'PENDING') return 'badge-pending'
  if (s === 'FAILED') return 'badge-failed'
  return 'badge-unknown'
}

/**
 * Thin watchlist-driven symbol switcher (KD25).
 * Select among watchlist entries; optional add + remove for active.
 */
export default function SymbolSwitcher({
  watchlist,
  activeSymbolId,
  onSelect,
  onAdd,
  onRemove,
  disabled,
  adding,
}) {
  const [draft, setDraft] = useState('')

  const active = watchlist?.find((e) => e.symbolId === activeSymbolId) ?? null
  const badge = active?.bootstrapStatus ?? null

  async function handleAdd(e) {
    e.preventDefault()
    const symbol = draft.trim()
    if (!symbol || adding || disabled) return
    try {
      await onAdd(symbol)
      setDraft('')
    } catch {
      // parent surfaces error banner
    }
  }

  return (
    <div className="symbol-switcher">
      <label className="symbol-switcher__label" htmlFor="symbol-select">
        Symbol
      </label>
      <select
        id="symbol-select"
        className="symbol-switcher__select"
        value={activeSymbolId || ''}
        disabled={disabled || !watchlist?.length}
        onChange={(e) => onSelect(e.target.value)}
      >
        {!watchlist?.length && <option value="">No symbols</option>}
        {watchlist?.map((entry) => (
          <option key={entry.symbolId} value={entry.symbolId}>
            {entry.tradingSymbol || entry.displayName || entry.symbolId}
            {entry.bootstrapStatus && entry.bootstrapStatus !== 'READY'
              ? ` (${entry.bootstrapStatus})`
              : ''}
          </option>
        ))}
      </select>

      {badge && (
        <span
          className={`symbol-switcher__badge ${statusClass(badge)}`}
          title={
            active?.bootstrapError
              ? active.bootstrapError
              : `Bootstrap: ${badge}`
          }
        >
          {String(badge).toUpperCase()}
        </span>
      )}

      <form className="symbol-switcher__add" onSubmit={handleAdd}>
        <input
          type="text"
          className="symbol-switcher__input"
          placeholder="Add symbol…"
          value={draft}
          disabled={disabled || adding}
          onChange={(e) => setDraft(e.target.value)}
          aria-label="Trading symbol to add"
        />
        <button
          type="submit"
          className="symbol-switcher__btn"
          disabled={disabled || adding || !draft.trim()}
        >
          {adding ? 'Adding…' : 'Add'}
        </button>
      </form>

      {activeSymbolId && (
        <button
          type="button"
          className="symbol-switcher__btn symbol-switcher__btn--danger"
          disabled={disabled || adding}
          onClick={() => onRemove(activeSymbolId)}
          title="Remove active symbol from watchlist"
        >
          Remove
        </button>
      )}
    </div>
  )
}
