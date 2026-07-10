import { useCallback, useEffect, useRef, useState } from 'react'
import {
  CandlestickSeries,
  ColorType,
  createChart,
  LineSeries,
} from 'lightweight-charts'
import { fetchCandles, fetchMarketStatus, fetchSymbol } from '../services/marketApi'
import { createLiveSocket } from '../services/liveSocket'
import { utcToNseChartTime } from '../utils/chartTime'
import ChartTypeToggle from './ChartTypeToggle'
import ConnectionStatus from './ConnectionStatus'

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

export default function ChartContainer() {
  const containerRef = useRef(null)
  const chartRef = useRef(null)
  const seriesRef = useRef(null)
  const candlesRef = useRef(new Map())
  const chartTypeRef = useRef('candlestick')
  const marketPhaseRef = useRef('unknown')
  const wsStatusRef = useRef('connecting')

  const [symbolInfo, setSymbolInfo] = useState(null)
  const [chartType, setChartType] = useState('candlestick')
  const [connectionStatus, setConnectionStatus] = useState('connecting')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [infoMessage, setInfoMessage] = useState(null)

  chartTypeRef.current = chartType

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

  useEffect(() => {
    let socket = null
    let resizeObserver = null
    let disposed = false

    async function init() {
      try {
        const info = await fetchSymbol()
        if (disposed) return
        setSymbolInfo(info)

        // Poll until multi-symbol recovery leaves PENDING (HTTP can accept before ApplicationRunner ends).
        let status = await fetchMarketStatus()
        if (disposed) return
        marketPhaseRef.current = status.marketPhase?.toLowerCase() ?? 'unknown'

        const normalizeBootstrap = (s) => (s || '').toString().toLowerCase()
        let polls = 0
        while (normalizeBootstrap(status.bootstrapStatus) === 'pending' && polls < 120 && !disposed) {
          setInfoMessage('Loading market data…')
          await new Promise((r) => setTimeout(r, 1000))
          status = await fetchMarketStatus()
          if (disposed) return
          marketPhaseRef.current = status.marketPhase?.toLowerCase() ?? marketPhaseRef.current
          polls += 1
        }

        if (normalizeBootstrap(status.bootstrapStatus) === 'failed') {
          setError(status.bootstrapError || 'Failed to connect to Upstox.')
          setLoading(false)
          updateConnectionStatus('error', marketPhaseRef.current)
          return
        }

        if (marketPhaseRef.current === 'closed') {
          setInfoMessage('Market is closed. Showing last available candle data.')
        } else {
          setInfoMessage(null)
        }

        const candles = await fetchCandles()
        if (disposed) return

        if (candles.length === 0) {
          setInfoMessage('No candle data available yet.')
        }

        candles.forEach(upsertCandle)

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
          },
          crosshair: {
            vertLine: { color: '#475569' },
            horzLine: { color: '#475569' },
          },
        })

        chartRef.current = chart
        seriesRef.current = createSeries(chart, chartTypeRef.current)
        applySeriesData(seriesRef.current, chartTypeRef.current)

        resizeObserver = new ResizeObserver((entries) => {
          const { width, height } = entries[0].contentRect
          chart.applyOptions({ width, height })
        })
        resizeObserver.observe(containerRef.current)

        socket = createLiveSocket({
          timeframe: info.timeframe,
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
                setInfoMessage(null)
              }
              return
            }

            if (message.type === 'candle_update' || message.type === 'candle_closed') {
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
      socket?.close()
      resizeObserver?.disconnect()
      chartRef.current?.remove()
      chartRef.current = null
      seriesRef.current = null
    }
  }, [updateConnectionStatus])

  useEffect(() => {
    if (!chartRef.current || loading) return
    switchChartType(chartType)
  }, [chartType, loading])

  return (
    <div className="chart-page">
      <header className="chart-header">
        <div className="chart-header__title">
          <h1>
            {symbolInfo ? `${symbolInfo.symbol} · ${symbolInfo.timeframe}` : 'Loading…'}
          </h1>
          <ConnectionStatus status={connectionStatus} />
        </div>
        <ChartTypeToggle chartType={chartType} onChange={setChartType} />
      </header>

      {error && <div className="chart-error">{error}</div>}
      {!error && infoMessage && <div className="chart-info">{infoMessage}</div>}

      <div className="chart-panel">
        {loading && <div className="chart-loading">Loading candles…</div>}
        <div ref={containerRef} className="chart-container" />
      </div>
    </div>
  )
}