import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { createChart } from 'lightweight-charts'
import { useAuth } from '../auth/AuthContext'
import {
  DEFAULT_TIMEFRAME,
  DEFAULT_TIMEFRAMES,
  fetchCandles,
  fetchMarketStatus,
  fetchTimeframes,
} from '../services/marketApi'
import { addSymbol, fetchWatchlist, removeSymbol } from '../services/watchlistApi'
import { createLiveSocket } from '../services/liveSocket'
import {
  BAR_SPACING,
  applyVolumePaneHeight,
  buildSymbolLabels,
  createPriceSeries,
  createVolumeSeries,
  createVolumeSmaSeries,
  defaultChartOptions,
  entryFailedMessage,
  findCandleByChartTime,
  lastVolumeSmaPoint,
  latestCandleFromMap,
  mapSeriesData,
  mapVolumeData,
  mapVolumeSmaData,
  MARKET_CLOSED_MSG,
  normalizeStatus,
  resolveDisplayStatus,
  sortedCandlesFromMap,
  toCandlestickPoint,
  toLinePoint,
  toVolumePoint,
} from '../chart/helpers'
import { usePatternOverlay } from '../chart/usePatternOverlay'
import { seriesKey } from '../utils/patternOverlay'
import AlertsFeed from './AlertsFeed'
import ChartTypeToggle from './ChartTypeToggle'
import ConnectionStatus from './ConnectionStatus'
import OhlcLegend from './OhlcLegend'
import SymbolSwitcher from './SymbolSwitcher'
import TimeframeSelector from './TimeframeSelector'

/**
 * Chart shell: watchlist, candles, live socket, pattern alerts/overlays.
 *
 * Overlay/alert logic lives in {@link usePatternOverlay}; pure LWC helpers in chart/helpers.
 */
export default function ChartContainer() {
  const { user, isAdmin, logout } = useAuth()
  const containerRef = useRef(null)
  const chartRef = useRef(null)
  const seriesRef = useRef(null)
  const volumeSeriesRef = useRef(null)
  const volumeSmaSeriesRef = useRef(null)
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
  const loadedSeriesKeyRef = useRef(null)
  /** True while crosshair is over a bar — live ticks must not overwrite hover OHLC. */
  const ohlcHoveringRef = useRef(false)

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
  /** Always-on OHLC+Vol legend (last bar, or hovered bar). */
  const [ohlcLegend, setOhlcLegend] = useState(null)

  chartTypeRef.current = chartType
  timeframeRef.current = timeframe
  activeSymbolIdRef.current = activeSymbolId
  watchlistRef.current = watchlist

  const overlay = usePatternOverlay({
    chartRef,
    seriesRef,
    candlesRef,
    activeSymbolIdRef,
    timeframeRef,
    loadedSeriesKeyRef,
    disposedRef,
    loading,
    activeSymbolId,
    timeframe,
  })

  const activeEntry = watchlist.find((e) => e.symbolId === activeSymbolId) ?? null
  const displayName =
    activeEntry?.tradingSymbol || activeEntry?.displayName || activeSymbolId || '…'
  const activeIsFailed = normalizeStatus(activeEntry?.bootstrapStatus) === 'FAILED'
  const activeIsPending = normalizeStatus(activeEntry?.bootstrapStatus) === 'PENDING'
  const symbolLabels = useMemo(() => buildSymbolLabels(watchlist), [watchlist])

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

  function applyFailedBanner(entry) {
    const msg = entryFailedMessage(entry)
    if (msg) setError(msg)
  }

  function applyInfoAfterLoad(candles) {
    if (marketPhaseRef.current === 'closed') {
      setInfoMessage(MARKET_CLOSED_MSG)
    } else if (!candles || candles.length === 0) {
      setInfoMessage('No candle data available yet.')
    } else {
      setInfoMessage(null)
    }
  }

  function applyVolumeSeriesData(candles) {
    volumeSeriesRef.current?.setData(mapVolumeData(candles))
    volumeSmaSeriesRef.current?.setData(mapVolumeSmaData(candles))
  }

  function syncOhlcLegendFromLatest() {
    if (ohlcHoveringRef.current) return
    setOhlcLegend(latestCandleFromMap(candlesRef.current))
  }

  function updateVolumeLive(candle) {
    try {
      volumeSeriesRef.current?.update(toVolumePoint(candle))
      const smaPoint = lastVolumeSmaPoint(sortedCandlesFromMap(candlesRef.current))
      if (smaPoint) {
        volumeSmaSeriesRef.current?.update(smaPoint)
      }
    } catch {
      applyVolumeSeriesData(sortedCandlesFromMap(candlesRef.current))
    }
  }

  /**
   * Bulk-load series data; re-attach pattern overlay when data is ready.
   */
  function applySeriesData(series, type, { resetView = true } = {}) {
    const candles = sortedCandlesFromMap(candlesRef.current)
    overlay.wipeOverlay()
    series.setData(mapSeriesData(type, candles))
    applyVolumeSeriesData(candles)

    const restoring = overlay.willRestoreForCurrentSeries()

    if (resetView) {
      ohlcHoveringRef.current = false
      series.priceScale().applyOptions({ autoScale: true })
      volumeSeriesRef.current?.priceScale().applyOptions({ autoScale: true })
      const chart = chartRef.current
      if (chart && candles.length > 0) {
        chart.timeScale().applyOptions({ barSpacing: BAR_SPACING })
        if (!restoring) {
          chart.timeScale().scrollToRealTime()
        }
      }
    }
    if (!ohlcHoveringRef.current) {
      setOhlcLegend(candles.length ? candles[candles.length - 1] : null)
    }
    overlay.restoreOverlayAfterData({ focusView: resetView })
  }

  function switchChartType(type) {
    const chart = chartRef.current
    if (!chart) return

    const keepAlert = overlay.captureKeepAlertForChartTypeSwitch()
    overlay.wipeOverlay()
    // Price series only — volume histogram + SMA stay on pane 1.
    if (seriesRef.current) {
      chart.removeSeries(seriesRef.current)
    }

    const series = createPriceSeries(chart, type)
    seriesRef.current = series
    if (keepAlert) {
      overlay.setPendingAlert(keepAlert)
    }
    applySeriesData(series, type)
  }

  const loadCandles = useCallback(async (symbolId, tf, generation) => {
    const candles = await fetchCandles({ symbolId, timeframe: tf })
    if (generation !== loadGenerationRef.current) {
      return null
    }
    candlesRef.current.clear()
    candles.forEach((candle) => {
      candlesRef.current.set(candle.time, candle)
    })
    loadedSeriesKeyRef.current = seriesKey(symbolId, tf)
    if (seriesRef.current) {
      applySeriesData(seriesRef.current, chartTypeRef.current)
    }
    return candles
    // applySeriesData/overlay closed over latest render; mount-style usage via generation
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // ── Init: watchlist → status → candles → chart + WS ──────────────────
  useEffect(() => {
    // Per-effect cancel token — StrictMode remount must not resurrect an older init.
    let cancelled = false
    disposedRef.current = false
    let resizeObserver = null
    let pollTimer = null
    let localSocket = null
    let localChart = null

    function isStale() {
      return cancelled || disposedRef.current
    }

    async function init() {
      try {
        const [list, status, tfInfo] = await Promise.all([
          fetchWatchlist(),
          fetchMarketStatus(),
          fetchTimeframes(),
        ])
        if (isStale()) return

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

        const globalFailed = normalizeStatus(status.bootstrapStatus) === 'FAILED'
        const anyUsable = entries.some((e) => normalizeStatus(e.bootstrapStatus) !== 'FAILED')

        if (globalFailed && (!entries.length || !anyUsable)) {
          setError(status.bootstrapError || 'Failed to connect to Upstox.')
          setLoading(false)
          updateConnectionStatus('error', marketPhaseRef.current)
          return
        }

        let polls = 0
        while (
          !isStale() &&
          entries.length > 0 &&
          !entries.some((e) => normalizeStatus(e.bootstrapStatus) === 'READY') &&
          entries.some((e) => normalizeStatus(e.bootstrapStatus) === 'PENDING') &&
          polls < 120
        ) {
          setInfoMessage('Loading market data…')
          await new Promise((r) => {
            pollTimer = setTimeout(r, 1000)
          })
          if (isStale()) return
          try {
            entries = await fetchWatchlist()
            if (isStale()) return
            setWatchlist(entries)
            watchlistRef.current = entries
            const st = await fetchMarketStatus()
            if (isStale()) return
            marketPhaseRef.current = st.marketPhase?.toLowerCase() ?? marketPhaseRef.current
          } catch {
            // keep polling
          }
          polls += 1
        }

        if (isStale()) return

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
          setInfoMessage(MARKET_CLOSED_MSG)
        } else if (normalizeStatus(primary.bootstrapStatus) === 'PENDING') {
          setInfoMessage('Loading market data…')
        } else {
          setInfoMessage(null)
        }

        const generation = ++loadGenerationRef.current
        if (normalizeStatus(primary.bootstrapStatus) === 'READY') {
          try {
            const candles = await loadCandles(primary.symbolId, initialTf, generation)
            if (isStale() || candles === null) return
            applyInfoAfterLoad(candles)
          } catch (err) {
            if (!isStale()) {
              setError(err instanceof Error ? err.message : 'Failed to load candles')
            }
          }
        } else if (normalizeStatus(primary.bootstrapStatus) === 'PENDING') {
          candlesRef.current.clear()
          loadedSeriesKeyRef.current = seriesKey(primary.symbolId, initialTf)
        }

        if (isStale()) return
        if (!containerRef.current) return

        const chart = createChart(containerRef.current, defaultChartOptions())
        localChart = chart
        chartRef.current = chart
        seriesRef.current = createPriceSeries(chart, chartTypeRef.current)
        volumeSeriesRef.current = createVolumeSeries(chart)
        volumeSmaSeriesRef.current = createVolumeSmaSeries(chart)
        applySeriesData(seriesRef.current, chartTypeRef.current)

        const initialHeight = containerRef.current?.clientHeight ?? 0
        applyVolumePaneHeight(chart, initialHeight)

        chart.subscribeCrosshairMove((param) => {
          if (isStale()) return
          const leftChart =
            param.point === undefined ||
            param.time === undefined ||
            param.point.x < 0 ||
            param.point.y < 0
          if (leftChart) {
            ohlcHoveringRef.current = false
            setOhlcLegend(latestCandleFromMap(candlesRef.current))
            return
          }
          const candle = findCandleByChartTime(candlesRef.current, param.time)
          if (candle) {
            ohlcHoveringRef.current = true
            setOhlcLegend(candle)
          }
        })

        resizeObserver = new ResizeObserver((entriesRo) => {
          const { width, height } = entriesRo[0].contentRect
          chart.applyOptions({ width, height })
          applyVolumePaneHeight(chart, height)
        })
        resizeObserver.observe(containerRef.current)

        localSocket = createLiveSocket({
          symbolId: primary.symbolId,
          timeframe: initialTf,
          onStatus: (wsStatus, message) => {
            if (isStale()) return
            if (wsStatus === 'error' && message) {
              // Control message after intentional watchlist remove — not a user-facing failure.
              if (
                typeof message === 'string' &&
                message.toLowerCase().includes('symbol removed from watchlist')
              ) {
                return
              }
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
            if (isStale()) return
            if (message.type === 'market_status') {
              updateConnectionStatus(wsStatusRef.current, message.marketPhase)
              if (message.marketPhase === 'closed') {
                setInfoMessage(MARKET_CLOSED_MSG)
              } else {
                setInfoMessage((prev) => (prev === MARKET_CLOSED_MSG ? null : prev))
              }
              return
            }

            if (message.type === 'pattern_event') {
              overlay.pushPatternAlert(message)
              return
            }

            if (message.type === 'candle_update' || message.type === 'candle_closed') {
              if (message.symbolId !== activeSymbolIdRef.current) return
              if (message.timeframe !== timeframeRef.current) return
              if (!message.candle) return
              upsertCandle(message.candle)
              try {
                seriesRef.current?.update(
                  chartTypeRef.current === 'line'
                    ? toLinePoint(message.candle)
                    : toCandlestickPoint(message.candle),
                )
                updateVolumeLive(message.candle)
                syncOhlcLegendFromLatest()
              } catch {
                if (seriesRef.current) {
                  applySeriesData(seriesRef.current, chartTypeRef.current, {
                    resetView: false,
                  })
                }
              }
            }
          },
        })
        socketRef.current = localSocket

        if (isStale()) {
          try {
            localSocket.close()
          } catch {
            // ignore
          }
          localSocket = null
          socketRef.current = null
          try {
            resizeObserver?.disconnect()
          } catch {
            // ignore
          }
          try {
            chart.remove()
          } catch {
            // ignore
          }
          localChart = null
          chartRef.current = null
          seriesRef.current = null
          volumeSeriesRef.current = null
          volumeSmaSeriesRef.current = null
          return
        }

        updateConnectionStatus('connecting', marketPhaseRef.current)
        setLoading(false)
      } catch (err) {
        if (!isStale()) {
          setError(err instanceof Error ? err.message : 'Failed to load chart data')
          setLoading(false)
          updateConnectionStatus('error', marketPhaseRef.current)
        }
      }
    }

    init()

    return () => {
      cancelled = true
      disposedRef.current = true
      loadGenerationRef.current += 1
      if (pollTimer) clearTimeout(pollTimer)
      try {
        localSocket?.close()
      } catch {
        // ignore
      }
      try {
        if (socketRef.current && socketRef.current !== localSocket) {
          socketRef.current.close()
        }
      } catch {
        // ignore
      }
      socketRef.current = null
      try {
        resizeObserver?.disconnect()
      } catch {
        // ignore
      }
      // Remove chart once only — double remove() throws and can blank the next route.
      const chartToRemove = localChart || chartRef.current
      localChart = null
      chartRef.current = null
      seriesRef.current = null
      volumeSeriesRef.current = null
      volumeSmaSeriesRef.current = null
      ohlcHoveringRef.current = false
      if (chartToRemove) {
        try {
          chartToRemove.remove()
        } catch {
          // LWC may throw if already disposed
        }
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- mount-only init
  }, [updateConnectionStatus, loadCandles])

  // ── PENDING recovery poll ────────────────────────────────────────────
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
          const candles = await loadCandles(activeId, timeframeRef.current, generation)
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
          prevMsg === MARKET_CLOSED_MSG ? prevMsg : 'Loading market data…',
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
            void recoverActiveIfReady(prevActive, nextActive)
          }
        }
      } catch {
        // transient
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

  const chartTypeEffectReadyRef = useRef(false)

  useEffect(() => {
    // Init already builds the series; only react to user chart-type changes after ready
    if (!chartRef.current || loading) {
      if (!loading) chartTypeEffectReadyRef.current = true
      return
    }
    if (!chartTypeEffectReadyRef.current) {
      chartTypeEffectReadyRef.current = true
      return
    }
    switchChartType(chartType)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chartType, loading])

  function clearSeriesDataFor(symbolId, tf) {
    candlesRef.current.clear()
    loadedSeriesKeyRef.current = seriesKey(symbolId, tf)
    if (seriesRef.current) {
      applySeriesData(seriesRef.current, chartTypeRef.current)
    }
  }

  /**
   * Navigate to a symbol: always remember/wipe overlay for the series being left.
   */
  async function switchToSymbol(
    nextSymbolId,
    entryHint = null,
    { force = false, navigateOverlay = true } = {},
  ) {
    if (!nextSymbolId || loading) return
    if (!force && nextSymbolId === activeSymbolIdRef.current) return

    if (navigateOverlay) {
      overlay.beginSeriesNavigation(nextSymbolId, timeframeRef.current)
    }

    const generation = ++loadGenerationRef.current
    setSwitching(true)
    setError(null)
    setInfoMessage(null)
    ohlcHoveringRef.current = false
    setOhlcLegend(null)

    const entry =
      entryHint ||
      watchlistRef.current.find((e) => e.symbolId === nextSymbolId) ||
      null

    setActiveSymbolId(nextSymbolId)
    activeSymbolIdRef.current = nextSymbolId

    // Drop previous series immediately so header never shows another symbol's bars.
    clearSeriesDataFor(nextSymbolId, timeframeRef.current)

    if (normalizeStatus(entry?.bootstrapStatus) === 'FAILED') {
      applyFailedBanner(entry)
      socketRef.current?.subscribe(nextSymbolId, timeframeRef.current)
      if (generation === loadGenerationRef.current) setSwitching(false)
      return
    }

    if (normalizeStatus(entry?.bootstrapStatus) === 'PENDING') {
      setInfoMessage('Loading market data…')
      socketRef.current?.subscribe(nextSymbolId, timeframeRef.current)
      if (generation === loadGenerationRef.current) setSwitching(false)
      return
    }

    try {
      const candles = await loadCandles(nextSymbolId, timeframeRef.current, generation)
      if (candles === null) return
      applyInfoAfterLoad(candles)
      socketRef.current?.subscribe(nextSymbolId, timeframeRef.current)
    } catch (err) {
      if (generation === loadGenerationRef.current) {
        setError(err instanceof Error ? err.message : 'Failed to load symbol')
      }
    } finally {
      if (generation === loadGenerationRef.current) setSwitching(false)
    }
  }

  function handleSymbolChange(nextSymbolId) {
    if (adding || loading) return
    if (!nextSymbolId || nextSymbolId === activeSymbolIdRef.current) return
    // beginSeriesNavigation inside switchToSymbol
    return switchToSymbol(nextSymbolId)
  }

  async function handleTimeframeChange(nextTf) {
    if (nextTf === timeframe || loading || !activeSymbolId || adding) return

    overlay.beginSeriesNavigation(activeSymbolIdRef.current, nextTf)

    // TF refs before load so restore matches pending
    setTimeframe(nextTf)
    timeframeRef.current = nextTf

    const entry =
      watchlistRef.current.find((e) => e.symbolId === activeSymbolIdRef.current) ||
      null

    // Always drop old-TF candles so header/live stream cannot mix series
    clearSeriesDataFor(activeSymbolIdRef.current, nextTf)

    if (normalizeStatus(entry?.bootstrapStatus) === 'FAILED') {
      ++loadGenerationRef.current
      applyFailedBanner(entry)
      socketRef.current?.subscribe(activeSymbolIdRef.current, nextTf)
      return
    }

    if (normalizeStatus(entry?.bootstrapStatus) === 'PENDING') {
      ++loadGenerationRef.current
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
      applyInfoAfterLoad(candles)
      socketRef.current?.subscribe(activeSymbolIdRef.current, nextTf)
    } catch (err) {
      if (generation === loadGenerationRef.current) {
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
      if (generation === loadGenerationRef.current) setSwitching(false)
    }
  }

  async function handleAlertSelect(alert) {
    if (!alert || loading || adding) return

    const entry =
      watchlistRef.current.find((e) => e.symbolId === alert.symbolId) || null

    if (!entry) {
      setError('Symbol is not on the watchlist for this alert.')
      overlay.setFocusSelection(alert)
      overlay.clearPendingAlert()
      return
    }

    if (
      activeSymbolIdRef.current &&
      timeframeRef.current &&
      (alert.symbolId !== activeSymbolIdRef.current ||
        alert.timeframe !== timeframeRef.current)
    ) {
      overlay.rememberOverlay(
        activeSymbolIdRef.current,
        timeframeRef.current,
        overlay.focusedInstanceIdRef.current,
        overlay.selectedAlertIdRef.current,
      )
    }

    overlay.setFocusSelection(alert)
    overlay.setPendingAlert(alert)
    overlay.rememberOverlay(
      alert.symbolId,
      alert.timeframe,
      alert.instanceId,
      alert.id,
    )

    const sameSymbol = alert.symbolId === activeSymbolIdRef.current
    const sameTf = alert.timeframe === timeframeRef.current

    if (sameSymbol && sameTf) {
      overlay.paintOverlayIfReady(alert)
      overlay.clearPendingAlert()
      return
    }

    if (normalizeStatus(entry.bootstrapStatus) === 'FAILED') {
      applyFailedBanner(entry)
      setActiveSymbolId(alert.symbolId)
      activeSymbolIdRef.current = alert.symbolId
      setTimeframe(alert.timeframe)
      timeframeRef.current = alert.timeframe
      candlesRef.current.clear()
      loadedSeriesKeyRef.current = seriesKey(alert.symbolId, alert.timeframe)
      overlay.wipeOverlay()
      if (seriesRef.current) {
        applySeriesData(seriesRef.current, chartTypeRef.current)
      }
      socketRef.current?.subscribe(alert.symbolId, alert.timeframe)
      overlay.clearPendingAlert()
      return
    }

    const generation = ++loadGenerationRef.current
    setSwitching(true)
    setError(null)
    setInfoMessage(null)
    ohlcHoveringRef.current = false
    setOhlcLegend(null)
    setActiveSymbolId(alert.symbolId)
    activeSymbolIdRef.current = alert.symbolId
    setTimeframe(alert.timeframe)
    timeframeRef.current = alert.timeframe

    // Clear old series immediately (same as TF / symbol switch).
    clearSeriesDataFor(alert.symbolId, alert.timeframe)

    if (normalizeStatus(entry.bootstrapStatus) === 'PENDING') {
      setInfoMessage('Loading market data…')
      socketRef.current?.subscribe(alert.symbolId, alert.timeframe)
      if (generation === loadGenerationRef.current) setSwitching(false)
      return
    }

    try {
      const candles = await loadCandles(alert.symbolId, alert.timeframe, generation)
      if (candles === null) return
      applyInfoAfterLoad(candles)
      socketRef.current?.subscribe(alert.symbolId, alert.timeframe)
    } catch (err) {
      if (generation === loadGenerationRef.current) {
        setError(err instanceof Error ? err.message : 'Failed to open alert on chart')
      }
    } finally {
      if (generation === loadGenerationRef.current) setSwitching(false)
    }
  }

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

      setInfoMessage(null)
      // Navigates overlay memory for the series being left
      await switchToSymbol(entry.symbolId, entry, { force: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to add symbol')
      setInfoMessage(null)
    } finally {
      setAdding(false)
    }
  }

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
        loadedSeriesKeyRef.current = null
        overlay.resetOverlayForEmptyWatchlist()
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
        // beginSeriesNavigation runs inside switchToSymbol
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
          <h1>{activeSymbolId ? displayName : 'Loading…'}</h1>
          <ConnectionStatus status={connectionStatus} />
        </div>

        <div className="chart-header__controls" aria-label="Chart controls">
          <TimeframeSelector
            orientation="horizontal"
            timeframes={timeframes}
            value={timeframe}
            onChange={handleTimeframeChange}
            disabled={
              loading ||
              adding ||
              !activeSymbolId ||
              activeIsFailed ||
              activeIsPending
            }
          />
          <ChartTypeToggle chartType={chartType} onChange={setChartType} />
        </div>

        <div className="chart-header__user">
          <span className="chart-header__user-name" title={user?.username}>
            {user?.displayName || user?.username}
            {user?.cashBalance != null && (
              <span className="chart-header__cash">
                {' '}
                · ₹
                {Number(user.cashBalance).toLocaleString('en-IN', {
                  maximumFractionDigits: 0,
                })}
              </span>
            )}
          </span>
          {isAdmin && (
            <Link to="/admin/users" className="chart-header__admin-link">
              Admin
            </Link>
          )}
          <button
            type="button"
            className="chart-header__logout"
            onClick={() => logout()}
          >
            Logout
          </button>
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
          <OhlcLegend candle={ohlcLegend} />
          <div ref={containerRef} className="chart-container" />
        </div>

        <div className="right-rail">
          <AlertsFeed
            alerts={overlay.alerts}
            selectedId={overlay.selectedAlertId}
            selectedInstanceId={overlay.selectedInstanceId}
            onSelect={handleAlertSelect}
            onClear={overlay.clearAllAlerts}
            symbolLabels={symbolLabels}
          />
          <SymbolSwitcher
            watchlist={watchlist}
            activeSymbolId={activeSymbolId}
            onSelect={handleSymbolChange}
            onAdd={handleAdd}
            onRemove={handleRemove}
            disabled={loading}
            adding={adding}
            canManage={isAdmin}
          />
        </div>
      </div>
    </div>
  )
}
