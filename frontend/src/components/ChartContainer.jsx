import { useCallback, useEffect, useRef, useState } from 'react'
import {
  CandlestickSeries,
  ColorType,
  createChart,
  LineSeries,
} from 'lightweight-charts'
import {
  DEFAULT_TIMEFRAME,
  DEFAULT_TIMEFRAMES,
  fetchCandles,
  fetchMarketStatus,
  fetchTimeframes,
} from '../services/marketApi'
import { addSymbol, fetchWatchlist, removeSymbol } from '../services/watchlistApi'
import { createLiveSocket } from '../services/liveSocket'
import { utcToNseChartTime } from '../utils/chartTime'
import ChartTypeToggle from './ChartTypeToggle'
import ConnectionStatus from './ConnectionStatus'
import SymbolSwitcher from './SymbolSwitcher'
import TimeframeSelector from './TimeframeSelector'

function toCandlestickPoint(candle) {
  return {
    time: utcToNseChartTime(candle.time),
    open: candle.open,
    high: candle.high,
    low: candle.low,
    close: candle.close,
  }
}

function toLinePoint(candle) {
  return {
    time: utcToNseChartTime(candle.time),
    value: candle.close,
  }
}

function resolveDisplayStatus(wsStatus, marketPhase) {
  if (marketPhase === 'closed') return 'market_closed'
  if (marketPhase === 'pre_open') return 'pre_open'
  return wsStatus
}

function normalizeStatus(s) {
  return (s || '').toString().toUpperCase()
}

function entryFailedMessage(entry) {
  if (!entry) return null
  if (normalizeStatus(entry.bootstrapStatus) !== 'FAILED') return null
  return entry.bootstrapError || `Bootstrap failed for ${entry.tradingSymbol || entry.symbolId}`
}

/**
 * Chart container state machine (KD25):
 * load watchlist → pick primary → candles + WS for active symbolId
 * On switch: invalidate in-flight (generation), re-fetch, re-subscribe
 * Filter WS by symbolId AND timeframe; per-symbol FAILED banner
 */
export default function ChartContainer() {
  const containerRef = useRef(null)
  const chartRef = useRef(null)
  const seriesRef = useRef(null)
  const candlesRef = useRef(new Map())
  const chartTypeRef = useRef('candlestick')
  const marketPhaseRef = useRef('unknown')
  const wsStatusRef = useRef('connecting')
  const socketRef = useRef(null)
  const timeframeRef = useRef(DEFAULT_TIMEFRAME)
  const activeSymbolIdRef = useRef(null)
  const loadGenerationRef = useRef(0)

  const [watchlist, setWatchlist] = useState([])
  const [activeSymbolId, setActiveSymbolId] = useState(null)
  const [timeframes, setTimeframes] = useState(DEFAULT_TIMEFRAMES)
  const [timeframe, setTimeframe] = useState(DEFAULT_TIMEFRAME)
  const [chartType, setChartType] = useState('candlestick')
  const [connectionStatus, setConnectionStatus] = useState('connecting')
  const [loading, setLoading] = useState(true)
  const [switching, setSwitching] = useState(false)
  const [adding, setAdding] = useState(false)
  const [error, setError] = useState(null)
  const [infoMessage, setInfoMessage] = useState(null)

  chartTypeRef.current = chartType
  timeframeRef.current = timeframe
  activeSymbolIdRef.current = activeSymbolId

  const activeEntry = watchlist.find((e) => e.symbolId === activeSymbolId) ?? null
  const displayName =
    activeEntry?.tradingSymbol || activeEntry?.displayName || activeSymbolId || '…'

  const updateConnectionStatus = useCallback((wsStatus, marketPhase) => {
    wsStatusRef.current = wsStatus
    if (marketPhase) {
      marketPhaseRef.current = marketPhase
    }
    setConnectionStatus(resolveDisplayStatus(wsStatus, marketPhaseRef.current))
  }, [])

  function upsertCandle(candle) {
    candlesRef.current.set(candle.time, candle)
  }

  function getSortedCandles() {
    return Array.from(candlesRef.current.values()).sort((a, b) => a.time - b.time)
  }

  function mapSeriesData(type, candles) {
    return type === 'line'
      ? candles.map(toLinePoint)
      : candles.map(toCandlestickPoint)
  }

  function applySeriesData(series, type) {
    const candles = getSortedCandles()
    series.setData(mapSeriesData(type, candles))
  }

  function createSeries(chart, type) {
    if (type === 'line') {
      return chart.addSeries(LineSeries, {
        color: '#38bdf8',
        lineWidth: 2,
        priceLineVisible: false,
      })
    }

    return chart.addSeries(CandlestickSeries, {
      upColor: '#22c55e',
      downColor: '#ef4444',
      borderVisible: false,
      wickUpColor: '#22c55e',
      wickDownColor: '#ef4444',
    })
  }

  function switchChartType(type) {
    const chart = chartRef.current
    if (!chart) return

    if (seriesRef.current) {
      chart.removeSeries(seriesRef.current)
    }

    const series = createSeries(chart, type)
    applySeriesData(series, type)
    seriesRef.current = series
  }

  function applyFailedBanner(entry) {
    const msg = entryFailedMessage(entry)
    if (msg) {
      setError(msg)
    }
  }

  function applyInfoAfterLoad(candles) {
    if (marketPhaseRef.current === 'closed') {
      setInfoMessage('Market is closed. Showing last available candle data.')
    } else if (!candles || candles.length === 0) {
      setInfoMessage('No candle data available yet.')
    } else {
      setInfoMessage(null)
    }
  }

  /**
   * Fetch candles for symbol+tf with generation guard; apply to chart if still current.
   */
  const loadCandles = useCallback(async (symbolId, tf, generation) => {
    const candles = await fetchCandles({ symbolId, timeframe: tf })
    if (generation !== loadGenerationRef.current) {
      return null // stale
    }
    candlesRef.current.clear()
    candles.forEach((candle) => {
      candlesRef.current.set(candle.time, candle)
    })
    if (seriesRef.current) {
      applySeriesData(seriesRef.current, chartTypeRef.current)
    }
    return candles
  }, [])

  // ── Init: watchlist → status → candles → chart + WS ──────────────────
  useEffect(() => {
    let disposed = false
    let resizeObserver = null
    let pollTimer = null

    async function init() {
      try {
        const [list, status, tfInfo] = await Promise.all([
          fetchWatchlist(),
          fetchMarketStatus(),
          fetchTimeframes(),
        ])
        if (disposed) return

        const supported = tfInfo.supported?.length ? tfInfo.supported : DEFAULT_TIMEFRAMES
        const initialTf = supported.includes(tfInfo.defaultTimeframe)
          ? tfInfo.defaultTimeframe
          : supported[0] || DEFAULT_TIMEFRAME
        setTimeframes(supported)
        setTimeframe(initialTf)
        timeframeRef.current = initialTf

        marketPhaseRef.current = status.marketPhase?.toLowerCase() ?? 'unknown'

        let entries = Array.isArray(list) ? list : []
        setWatchlist(entries)

        // Global FAILED with empty / all-failed watchlist → hard bail
        const globalFailed = normalizeStatus(status.bootstrapStatus) === 'FAILED'
        const anyUsable = entries.some((e) => normalizeStatus(e.bootstrapStatus) !== 'FAILED')

        if (globalFailed && (!entries.length || !anyUsable)) {
          setError(status.bootstrapError || 'Failed to connect to Upstox.')
          setLoading(false)
          updateConnectionStatus('error', marketPhaseRef.current)
          return
        }

        // PENDING poll: wait for at least one READY (or give up after ~2 min)
        let polls = 0
        while (
          !disposed &&
          entries.length > 0 &&
          !entries.some((e) => normalizeStatus(e.bootstrapStatus) === 'READY') &&
          entries.some((e) => normalizeStatus(e.bootstrapStatus) === 'PENDING') &&
          polls < 120
        ) {
          setInfoMessage('Loading market data…')
          await new Promise((r) => {
            pollTimer = setTimeout(r, 1000)
          })
          if (disposed) return
          try {
            entries = await fetchWatchlist()
            setWatchlist(entries)
            const st = await fetchMarketStatus()
            marketPhaseRef.current = st.marketPhase?.toLowerCase() ?? marketPhaseRef.current
          } catch {
            // keep polling
          }
          polls += 1
        }

        if (disposed) return

        // Pick primary = first entry (seed order)
        const primary =
          entries.find((e) => normalizeStatus(e.bootstrapStatus) === 'READY') ||
          entries[0] ||
          null

        if (!primary) {
          setError('Watchlist is empty. Add a symbol to begin.')
          setLoading(false)
          updateConnectionStatus('error', marketPhaseRef.current)
          return
        }

        setActiveSymbolId(primary.symbolId)
        activeSymbolIdRef.current = primary.symbolId

        if (normalizeStatus(primary.bootstrapStatus) === 'FAILED') {
          setError(entryFailedMessage(primary))
        } else {
          setError(null)
        }

        if (marketPhaseRef.current === 'closed') {
          setInfoMessage('Market is closed. Showing last available candle data.')
        } else {
          setInfoMessage(null)
        }

        const generation = ++loadGenerationRef.current
        let candles = []
        if (normalizeStatus(primary.bootstrapStatus) !== 'FAILED') {
          try {
            candles = await loadCandles(primary.symbolId, initialTf, generation)
            if (disposed || candles === null) return
            applyInfoAfterLoad(candles)
          } catch (err) {
            if (!disposed) {
              setError(err instanceof Error ? err.message : 'Failed to load candles')
            }
          }
        }

        if (disposed) return

        const chart = createChart(containerRef.current, {
          layout: {
            background: { type: ColorType.Solid, color: '#0f1117' },
            textColor: '#94a3b8',
          },
          grid: {
            vertLines: { color: '#1e2433' },
            horzLines: { color: '#1e2433' },
          },
          rightPriceScale: {
            borderColor: '#2a3144',
          },
          timeScale: {
            borderColor: '#2a3144',
            timeVisible: true,
            secondsVisible: false,
            barSpacing: 8,
          },
          crosshair: {
            vertLine: { color: '#475569' },
            horzLine: { color: '#475569' },
          },
        })

        chartRef.current = chart
        seriesRef.current = createSeries(chart, chartTypeRef.current)
        applySeriesData(seriesRef.current, chartTypeRef.current)

        resizeObserver = new ResizeObserver((entriesRo) => {
          const { width, height } = entriesRo[0].contentRect
          chart.applyOptions({ width, height })
        })
        resizeObserver.observe(containerRef.current)

        socketRef.current = createLiveSocket({
          symbolId: primary.symbolId,
          timeframe: initialTf,
          onStatus: (wsStatus, message) => {
            if (disposed) return
            if (wsStatus === 'error' && message) {
              setError(message)
            }
            updateConnectionStatus(wsStatus, marketPhaseRef.current)
          },
          onMessage: (message) => {
            if (message.type === 'market_status') {
              updateConnectionStatus(wsStatusRef.current, message.marketPhase)
              if (message.marketPhase === 'closed') {
                setInfoMessage('Market is closed. Showing last available candle data.')
              } else {
                setInfoMessage((prev) =>
                  prev === 'Market is closed. Showing last available candle data.'
                    ? null
                    : prev,
                )
              }
              return
            }

            if (message.type === 'candle_update' || message.type === 'candle_closed') {
              // Required (KD25): accept only matching symbolId AND timeframe
              if (message.symbolId !== activeSymbolIdRef.current) return
              if (message.timeframe !== timeframeRef.current) return
              if (!message.candle) return
              upsertCandle(message.candle)
              seriesRef.current?.update(
                chartTypeRef.current === 'line'
                  ? toLinePoint(message.candle)
                  : toCandlestickPoint(message.candle),
              )
            }
          },
        })

        updateConnectionStatus('connecting', marketPhaseRef.current)
        setLoading(false)
      } catch (err) {
        if (!disposed) {
          setError(err instanceof Error ? err.message : 'Failed to load chart data')
          setLoading(false)
          updateConnectionStatus('error', marketPhaseRef.current)
        }
      }
    }

    init()

    return () => {
      disposed = true
      if (pollTimer) clearTimeout(pollTimer)
      socketRef.current?.close()
      socketRef.current = null
      resizeObserver?.disconnect()
      chartRef.current?.remove()
      chartRef.current = null
      seriesRef.current = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- mount-only init
  }, [updateConnectionStatus, loadCandles])

  useEffect(() => {
    if (!chartRef.current || loading) return
    switchChartType(chartType)
  }, [chartType, loading])

  /**
   * Switch active chart symbol. Optional `entryHint` avoids relying on stale
   * React state right after add/remove refreshes the list.
   */
  async function switchToSymbol(nextSymbolId, entryHint = null, { force = false } = {}) {
    if (!nextSymbolId || loading) return
    if (!force && nextSymbolId === activeSymbolIdRef.current) return

    const generation = ++loadGenerationRef.current
    setSwitching(true)
    setError(null)

    const entry =
      entryHint ||
      watchlist.find((e) => e.symbolId === nextSymbolId) ||
      null

    setActiveSymbolId(nextSymbolId)
    activeSymbolIdRef.current = nextSymbolId

    if (normalizeStatus(entry?.bootstrapStatus) === 'FAILED') {
      applyFailedBanner(entry)
      candlesRef.current.clear()
      if (seriesRef.current) {
        applySeriesData(seriesRef.current, chartTypeRef.current)
      }
      socketRef.current?.subscribe(nextSymbolId, timeframeRef.current)
      if (generation === loadGenerationRef.current) {
        setSwitching(false)
      }
      return
    }

    try {
      const candles = await loadCandles(nextSymbolId, timeframeRef.current, generation)
      if (candles === null) return // superseded
      applyInfoAfterLoad(candles)
      socketRef.current?.subscribe(nextSymbolId, timeframeRef.current)
    } catch (err) {
      if (generation === loadGenerationRef.current) {
        setError(err instanceof Error ? err.message : 'Failed to load symbol')
      }
    } finally {
      if (generation === loadGenerationRef.current) {
        setSwitching(false)
      }
    }
  }

  function handleSymbolChange(nextSymbolId) {
    if (switching || adding) return
    return switchToSymbol(nextSymbolId)
  }

  // ── Timeframe switch (preserve symbol + chart type) ──────────────────
  async function handleTimeframeChange(nextTf) {
    if (nextTf === timeframe || switching || loading || !activeSymbolId) return

    const generation = ++loadGenerationRef.current
    setSwitching(true)
    setError(null)

    try {
      const candles = await loadCandles(activeSymbolId, nextTf, generation)
      if (candles === null) return
      setTimeframe(nextTf)
      timeframeRef.current = nextTf
      applyInfoAfterLoad(candles)
      applyFailedBanner(activeEntry)
      socketRef.current?.subscribe(activeSymbolId, nextTf)
    } catch (err) {
      if (generation === loadGenerationRef.current) {
        setError(err instanceof Error ? err.message : 'Failed to load timeframe')
      }
    } finally {
      if (generation === loadGenerationRef.current) {
        setSwitching(false)
      }
    }
  }

  // ── Add symbol ───────────────────────────────────────────────────────
  async function handleAdd(tradingSymbol) {
    setAdding(true)
    setError(null)
    setInfoMessage('Adding & seeding…')
    try {
      const entry = await addSymbol(tradingSymbol)
      const refreshed = await fetchWatchlist()
      setWatchlist(refreshed)

      if (normalizeStatus(entry.bootstrapStatus) === 'FAILED') {
        setError(
          entry.bootstrapError ||
            `Failed to add ${entry.tradingSymbol || tradingSymbol}`,
        )
        setInfoMessage(null)
        return
      }

      // Auto-switch to the newly added symbol (pass entry to avoid stale state)
      setInfoMessage(null)
      await switchToSymbol(entry.symbolId, entry, { force: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to add symbol')
      setInfoMessage(null)
    } finally {
      setAdding(false)
    }
  }

  // ── Remove active symbol ─────────────────────────────────────────────
  async function handleRemove(symbolId) {
    if (!symbolId || switching || loading || adding) return
    setError(null)
    const wasActive = symbolId === activeSymbolIdRef.current
    try {
      await removeSymbol(symbolId)
      const refreshed = await fetchWatchlist()
      setWatchlist(refreshed)

      if (!refreshed.length) {
        loadGenerationRef.current += 1
        setActiveSymbolId(null)
        activeSymbolIdRef.current = null
        candlesRef.current.clear()
        if (seriesRef.current) {
          applySeriesData(seriesRef.current, chartTypeRef.current)
        }
        setError('Watchlist is empty. Add a symbol to begin.')
        return
      }

      if (wasActive) {
        const next =
          refreshed.find((e) => normalizeStatus(e.bootstrapStatus) === 'READY') ||
          refreshed[0]
        await switchToSymbol(next.symbolId, next, { force: true })
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to remove symbol')
    }
  }

  const busy = loading || switching || adding

  return (
    <div className="chart-page">
      <header className="chart-header">
        <div className="chart-header__title">
          <h1>
            {activeSymbolId ? `${displayName} · ${timeframe}` : 'Loading…'}
          </h1>
          <ConnectionStatus status={connectionStatus} />
        </div>
        <div className="chart-header__controls">
          <SymbolSwitcher
            watchlist={watchlist}
            activeSymbolId={activeSymbolId}
            onSelect={handleSymbolChange}
            onAdd={handleAdd}
            onRemove={handleRemove}
            disabled={loading}
            adding={adding}
          />
          <TimeframeSelector
            timeframes={timeframes}
            value={timeframe}
            onChange={handleTimeframeChange}
            disabled={busy || !activeSymbolId}
          />
          <ChartTypeToggle chartType={chartType} onChange={setChartType} />
        </div>
      </header>

      {error && <div className="chart-error">{error}</div>}
      {!error && infoMessage && <div className="chart-info">{infoMessage}</div>}

      <div className="chart-panel">
        {(loading || switching) && (
          <div className="chart-loading">
            {switching
              ? adding
                ? 'Adding & seeding…'
                : 'Loading symbol…'
              : 'Loading candles…'}
          </div>
        )}
        <div ref={containerRef} className="chart-container" />
      </div>
    </div>
  )
}
