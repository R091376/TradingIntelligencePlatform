import { useMemo, useState } from 'react'
import InstrumentSearch from './InstrumentSearch'
import WatchlistBar from './WatchlistBar'

/**
 * Right-side watchlist panel: search + vertical list with per-row remove.
 */
export default function SymbolSwitcher({
  watchlist,
  activeSymbolId,
  onSelect,
  onAdd,
  onRemove,
  disabled,
  adding,
  hardMax = 50,
}) {
  const [removingId, setRemovingId] = useState(null)

  const watchlistKeys = useMemo(
    () => new Set((watchlist || []).map((e) => e.symbolId)),
    [watchlist],
  )

  async function handleRemove(symbolId) {
    if (!symbolId || disabled || adding) return
    setRemovingId(symbolId)
    try {
      await onRemove(symbolId)
    } finally {
      setRemovingId(null)
    }
  }

  const count = watchlist?.length ?? 0

  return (
    <aside className="watchlist-sidebar" aria-label="Watchlist sidebar">
      <div className="watchlist-sidebar__header">
        <h2 className="watchlist-sidebar__title">Watchlist</h2>
        <span className="watchlist-sidebar__count">
          {count}/{hardMax}
        </span>
      </div>

      <div className="watchlist-sidebar__search">
        <InstrumentSearch
          onAdd={onAdd}
          watchlistKeys={watchlistKeys}
          hardMax={hardMax}
          watchlistSize={count}
          disabled={disabled}
          adding={adding}
          compact
        />
      </div>

      <div className="watchlist-sidebar__list">
        <WatchlistBar
          watchlist={watchlist}
          activeSymbolId={activeSymbolId}
          onSelect={onSelect}
          onRemove={handleRemove}
          disabled={disabled || adding}
          removingId={removingId}
        />
      </div>
    </aside>
  )
}
