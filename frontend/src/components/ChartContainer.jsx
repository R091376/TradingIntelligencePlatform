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

/** Candle/bar width in px (LWC default ~6). Applied on create and after symbol/TF load. */
const BAR_SPACING = 7

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
  const watchlistRef = useRef([])
  const disposedRef = useRef(false)

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
  watchlistRef.current = watchlist

  const activeEntry = watchlist.find((e) => e.symbolId === activeSymbolId) ?? null
  const displayName =
    activeEntry?.tradingSymbol || activeEntry?.displayName || activeSymbolId || '…'
  const activeIsFailed = normalizeStatus(activeEntry?.bootstrapStatus) === 'FAILED'
  const activeIsPending = normalizeStatus(activeEntry?.bootstrapStatus) === 'PENDING'

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

  /**
   * Bulk-load series data. After setData, re-enable price autoScale so a manual
   * price zoom on the previous symbol does not stick when switching stocks/TFs.
   * Do NOT fitContent() over full history — that crushes barSpacing to min and
   * makes candles look tiny. Keep spacing and scroll to the latest bars.
   */
  function applySeriesData(series, type, { resetView = true } = {}) {
    const candles = getSortedCandles()
    series.setData(mapSeriesData(type, candles))
    if (resetView) {
      // User zoom/scroll on the price scale turns autoScale off; restore it
      // so the next symbol gets a full price range for its own data.
      series.priceScale().applyOptions({ autoScale: true })
      const chart = chartRef.current
      if (chart && candles.length > 0) {
        chart.timeScale().applyOptions({ barSpacing: BAR_SPACING })
        chart.timeScale().scrollToRealTime()
      }
    }
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
    disposedRef.current = false
    let resizeObserver = null
    let pollTimer = null

    async function init() {
      try {
        const [list, status, tfInfo] = await Promise.all([
          fetchWatchlist(),
          fetchMarketStatus(),
          fetchTimeframes(),
        ])
        if (disposedRef.current) return

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
        watchlistRef.current = entries

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
          !disposedRef.current &&
          entries.length > 0 &&
          !entries.some((e) => normalizeStatus(e.bootstrapStatus) === 'READY') &&
          entries.some((e) => normalizeStatus(e.bootstrapStatus) === 'PENDING') &&
          polls < 120
        ) {
          setInfoMessage('Loading market data…')
          await new Promise((r) => {
            pollTimer = setTimeout(r, 1000)
          })
          if (disposedRef.current) return
          try {
            entries = await fetchWatchlist()
            setWatchlist(entries)
            watchlistRef.current = entries
            const st = await fetchMarketStatus()
            marketPhaseRef.current = st.marketPhase?.toLowerCase() ?? marketPhaseRef.current
          } catch {
            // keep polling
          }
          polls += 1
        }

        if (disposedRef.current) return

        // Prefer first READY; otherwise first entry (seed order)
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
        } else if (normalizeStatus(primary.bootstrapStatus) === 'PENDING') {
          setInfoMessage('Loading market data…')
        } else {
          setInfoMessage(null)
        }

        const generation = ++loadGenerationRef.current
        let candles = []
        if (normalizeStatus(primary.bootstrapStatus) === 'READY') {
          try {
            candles = await loadCandles(primary.symbolId, initialTf, generation)
            if (disposedRef.current || candles === null) return
            applyInfoAfterLoad(candles)
          } catch (err) {
            if (!disposedRef.current) {
              setError(err instanceof Error ? err.message : 'Failed to load candles')
            }
          }
        } else if (normalizeStatus(primary.bootstrapStatus) === 'PENDING') {
          // PENDING returns [] from backend; keep empty until post-init poll recovers
          candlesRef.current.clear()
        }

        if (disposedRef.current) return

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
            // Wider than LWC default (~6); re-applied on each symbol/TF load
            barSpacing: BAR_SPACING,
            minBarSpacing: 2,
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
            if (disposedRef.current) return
            if (wsStatus === 'error' && message) {
              // Don't clobber per-symbol bootstrapError with WS noise while FAILED
              const active = watchlistRef.current.find(
                (e) => e.symbolId === activeSymbolIdRef.current,
              )
              if (normalizeStatus(active?.bootstrapStatus) !== 'FAILED') {
                setError(message)
              }
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
        if (!disposedRef.current) {
          setError(err instanceof Error ? err.message : 'Failed to load chart data')
          setLoading(false)
          updateConnectionStatus('error', marketPhaseRef.current)
        }
      }
    }

    init()

    return () => {
      disposedRef.current = true
      loadGenerationRef.current += 1
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

  // ── Post-init PENDING recovery: poll while any entry is PENDING ──────
  // Uses refs so effect cleanup does not cancel an in-flight READY recovery load.
  const needsPendingPoll =
    !loading &&
    watchlist.some((e) => normalizeStatus(e.bootstrapStatus) === 'PENDING')

  useEffect(() => {
    if (!needsPendingPoll) return

    let stopped = false
    let timer = null

    async function recoverActiveIfReady(prevActive, nextActive) {
      const prevStatus = normalizeStatus(prevActive?.bootstrapStatus)
      const nextStatus = normalizeStatus(nextActive.bootstrapStatus)
      const activeId = nextActive.symbolId

      if (prevStatus === 'PENDING' && nextStatus === 'READY') {
        const generation = ++loadGenerationRef.current
        setSwitching(true)
        setError(null)
        setInfoMessage(null)
        try {
          const candles = await loadCandles(
            activeId,
            timeframeRef.current,
            generation,
          )
          // generation / disposed only — not effect `stopped` (READY load must finish)
          if (candles === null || disposedRef.current) return
          applyInfoAfterLoad(candles)
          socketRef.current?.subscribe(activeId, timeframeRef.current)
        } catch (err) {
          if (generation === loadGenerationRef.current && !disposedRef.current) {
            setError(err instanceof Error ? err.message : 'Failed to load candles')
          }
        } finally {
          if (generation === loadGenerationRef.current) {
            setSwitching(false)
          }
        }
      } else if (prevStatus === 'PENDING' && nextStatus === 'FAILED') {
        applyFailedBanner(nextActive)
        setInfoMessage(null)
      } else if (nextStatus === 'PENDING') {
        setInfoMessage((prevMsg) =>
          prevMsg === 'Market is closed. Showing last available candle data.'
            ? prevMsg
            : 'Loading market data…',
        )
      }
    }

    async function pollPending() {
      if (stopped || disposedRef.current) return

      const stillPending = watchlistRef.current.some(
        (e) => normalizeStatus(e.bootstrapStatus) === 'PENDING',
      )
      if (!stillPending) return

      try {
        const entries = await fetchWatchlist()
        if (stopped || disposedRef.current) return

        const prev = watchlistRef.current
        setWatchlist(entries)
        watchlistRef.current = entries

        const activeId = activeSymbolIdRef.current
        if (activeId) {
          const prevActive = prev.find((e) => e.symbolId === activeId)
          const nextActive = entries.find((e) => e.symbolId === activeId)
          if (nextActive) {
            // fire recovery without blocking the poll loop
            void recoverActiveIfReady(prevActive, nextActive)
          }
        }
      } catch {
        // transient — keep polling
      }

      if (!stopped && !disposedRef.current) {
        timer = setTimeout(pollPending, 1000)
      }
    }

    timer = setTimeout(pollPending, 1000)

    return () => {
      stopped = true
      if (timer) clearTimeout(timer)
    }
  }, [needsPendingPoll, loadCandles])

  useEffect(() => {
    if (!chartRef.current || loading) return
    switchChartType(chartType)
  }, [chartType, loading])

  /**
   * Switch active chart symbol. Optional `entryHint` avoids relying on stale
   * React state right after add/remove refreshes the list.
   * Allows mid-flight supersede: always bumps generation so in-flight loads are discarded.
   */
  async function switchToSymbol(nextSymbolId, entryHint = null, { force = false } = {}) {
    if (!nextSymbolId || loading) return
    if (!force && nextSymbolId === activeSymbolIdRef.current) return

    const generation = ++loadGenerationRef.current
    setSwitching(true)
    setError(null)

    const entry =
      entryHint ||
      watchlistRef.current.find((e) => e.symbolId === nextSymbolId) ||
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

    if (normalizeStatus(entry?.bootstrapStatus) === 'PENDING') {
      // Wait for post-init PENDING poll to recover; clear series for now
      candlesRef.current.clear()
      if (seriesRef.current) {
        applySeriesData(seriesRef.current, chartTypeRef.current)
      }
      setInfoMessage('Loading market data…')
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

  // Open #1: allow superseding in-flight symbol switch via generation bump
  function handleSymbolChange(nextSymbolId) {
    if (adding || loading) return
    return switchToSymbol(nextSymbolId)
  }

  // ── Timeframe switch (preserve symbol + chart type) ──────────────────
  async function handleTimeframeChange(nextTf) {
    if (nextTf === timeframe || loading || !activeSymbolId) return
    // Allow supersede while switching (generation), but not while adding
    if (adding) return

    const entry =
      watchlistRef.current.find((e) => e.symbolId === activeSymbolIdRef.current) ||
      null

    // Open #3: FAILED — skip candles fetch (would 503), keep bootstrapError
    if (normalizeStatus(entry?.bootstrapStatus) === 'FAILED') {
      ++loadGenerationRef.current // discard any in-flight load
      setTimeframe(nextTf)
      timeframeRef.current = nextTf
      applyFailedBanner(entry)
      socketRef.current?.subscribe(activeSymbolIdRef.current, nextTf)
      return
    }

    // PENDING — update TF locally; post-init poll will load when READY
    if (normalizeStatus(entry?.bootstrapStatus) === 'PENDING') {
      ++loadGenerationRef.current
      setTimeframe(nextTf)
      timeframeRef.current = nextTf
      setInfoMessage('Loading market data…')
      socketRef.current?.subscribe(activeSymbolIdRef.current, nextTf)
      return
    }

    const generation = ++loadGenerationRef.current
    setSwitching(true)
    setError(null)

    try {
      const candles = await loadCandles(activeSymbolIdRef.current, nextTf, generation)
      if (candles === null) return
      setTimeframe(nextTf)
      timeframeRef.current = nextTf
      applyInfoAfterLoad(candles)
      socketRef.current?.subscribe(activeSymbolIdRef.current, nextTf)
    } catch (err) {
      if (generation === loadGenerationRef.current) {
        // Prefer bootstrapError if active is FAILED mid-flight
        const active = watchlistRef.current.find(
          (e) => e.symbolId === activeSymbolIdRef.current,
        )
        const failedMsg = entryFailedMessage(active)
        setError(
          failedMsg ||
            (err instanceof Error ? err.message : 'Failed to load timeframe'),
        )
      }
    } finally {
      if (generation === loadGenerationRef.current) {
        setSwitching(false)
      }
    }
  }

  // ── Add symbol (string trading symbol or { symbol, instrumentKey }) ───
  async function handleAdd(input) {
    setAdding(true)
    setError(null)
    setInfoMessage('Adding & seeding…')
    const label =
      typeof input === 'string'
        ? input
        : input?.symbol || input?.instrumentKey || 'symbol'
    try {
      const entry = await addSymbol(input)
      const refreshed = await fetchWatchlist()
      setWatchlist(refreshed)
      watchlistRef.current = refreshed

      if (normalizeStatus(entry.bootstrapStatus) === 'FAILED') {
        setError(
          entry.bootstrapError ||
            `Failed to add ${entry.tradingSymbol || label}`,
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
    if (!symbolId || loading || adding) return
    setError(null)
    const wasActive = symbolId === activeSymbolIdRef.current
    try {
      await removeSymbol(symbolId)
      const refreshed = await fetchWatchlist()
      setWatchlist(refreshed)
      watchlistRef.current = refreshed

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
          <TimeframeSelector
            timeframes={timeframes}
            value={timeframe}
            onChange={handleTimeframeChange}
            disabled={loading || adding || !activeSymbolId || activeIsFailed}
          />
          <ChartTypeToggle chartType={chartType} onChange={setChartType} />
        </div>
      </header>

      {error && <div className="chart-error">{error}</div>}
      {!error && infoMessage && <div className="chart-info">{infoMessage}</div>}

      <div className="chart-body">
        <div className="chart-panel">
          {(loading || switching || activeIsPending) && (
            <div className="chart-loading">
              {adding
                ? 'Adding & seeding…'
                : activeIsPending
                  ? 'Waiting for symbol bootstrap…'
                  : switching
                    ? 'Loading symbol…'
                    : 'Loading candles…'}
            </div>
          )}
          <div ref={containerRef} className="chart-container" />
        </div>
        <SymbolSwitcher
          watchlist={watchlist}
          activeSymbolId={activeSymbolId}
          onSelect={handleSymbolChange}
          onAdd={handleAdd}
          onRemove={handleRemove}
          disabled={loading}
          adding={adding}
        />
      </div>
    </div>
  )
}
