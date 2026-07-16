import {
  CandlestickSeries,
  ColorType,
  HistogramSeries,
  LineSeries,
} from 'lightweight-charts'
import { utcToNseChartTime } from '../utils/chartTime'

/** Candle/bar width in px (LWC default ~6). Applied on create and after symbol/TF load. */
export const BAR_SPACING = 7

/** Volume subplot ~22% of chart height (TradingView-style). */
export const VOLUME_PANE_HEIGHT_RATIO = 0.22

/** Matches backend breakout volume baseline (docs/indicators/volume-sma-20.md). */
export const VOLUME_SMA_PERIOD = 20

const VOLUME_UP_COLOR = 'rgba(34, 197, 94, 0.5)'
const VOLUME_DOWN_COLOR = 'rgba(239, 68, 68, 0.5)'
const VOLUME_SMA_COLOR = '#60a5fa'

export const MARKET_CLOSED_MSG = 'Market is closed. Showing last available candle data.'

export function toCandlestickPoint(candle) {
  return {
    time: utcToNseChartTime(candle.time),
    open: candle.open,
    high: candle.high,
    low: candle.low,
    close: candle.close,
  }
}

export function toLinePoint(candle) {
  return {
    time: utcToNseChartTime(candle.time),
    value: candle.close,
  }
}

export function mapSeriesData(type, candles) {
  return type === 'line'
    ? candles.map(toLinePoint)
    : candles.map(toCandlestickPoint)
}

/**
 * TradingView-style volume bar: green when close >= open, red otherwise.
 * @param {{ time: number, open: number, close: number, volume?: number }} candle
 */
export function toVolumePoint(candle) {
  const up = Number(candle.close) >= Number(candle.open)
  return {
    time: utcToNseChartTime(candle.time),
    value: Number(candle.volume) || 0,
    color: up ? VOLUME_UP_COLOR : VOLUME_DOWN_COLOR,
  }
}

export function mapVolumeData(candles) {
  return (candles || []).map(toVolumePoint)
}

/**
 * Simple SMA of volume. Emits points only once {@link VOLUME_SMA_PERIOD} bars exist.
 * @param {Array<{ time: number, volume?: number }>} candles sorted ascending by time
 * @param {number} [period=VOLUME_SMA_PERIOD]
 */
export function mapVolumeSmaData(candles, period = VOLUME_SMA_PERIOD) {
  const list = candles || []
  if (list.length < period || period <= 0) return []

  const points = []
  let windowSum = 0
  for (let i = 0; i < list.length; i++) {
    windowSum += Number(list[i].volume) || 0
    if (i >= period) {
      windowSum -= Number(list[i - period].volume) || 0
    }
    if (i >= period - 1) {
      points.push({
        time: utcToNseChartTime(list[i].time),
        value: windowSum / period,
      })
    }
  }
  return points
}

/**
 * Last SMA point for live updates (same formula as {@link mapVolumeSmaData}).
 * @returns {{ time: number, value: number } | null}
 */
export function lastVolumeSmaPoint(candles, period = VOLUME_SMA_PERIOD) {
  const list = candles || []
  if (list.length < period || period <= 0) return null
  let sum = 0
  for (let i = list.length - period; i < list.length; i++) {
    sum += Number(list[i].volume) || 0
  }
  const last = list[list.length - 1]
  return {
    time: utcToNseChartTime(last.time),
    value: sum / period,
  }
}

export function resolveDisplayStatus(wsStatus, marketPhase) {
  if (marketPhase === 'closed') return 'market_closed'
  if (marketPhase === 'pre_open') return 'pre_open'
  return wsStatus
}

export function normalizeStatus(s) {
  return (s || '').toString().toUpperCase()
}

export function entryFailedMessage(entry) {
  if (!entry) return null
  if (normalizeStatus(entry.bootstrapStatus) !== 'FAILED') return null
  return entry.bootstrapError || `Bootstrap failed for ${entry.tradingSymbol || entry.symbolId}`
}

export function buildSymbolLabels(watchlist) {
  const map = {}
  for (const e of watchlist || []) {
    map[e.symbolId] = e.tradingSymbol || e.displayName || e.symbolId
  }
  return map
}

export function createPriceSeries(chart, type) {
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

/**
 * Volume histogram on pane 1 (created on first use). Shares scale id with SMA.
 * @returns {import('lightweight-charts').ISeriesApi<'Histogram'>}
 */
export function createVolumeSeries(chart) {
  const series = chart.addSeries(
    HistogramSeries,
    {
      priceFormat: { type: 'volume' },
      priceScaleId: 'volume',
      priceLineVisible: false,
      lastValueVisible: true,
      base: 0,
    },
    1,
  )
  series.priceScale().applyOptions({
    scaleMargins: { top: 0.15, bottom: 0 },
    borderColor: '#2a3144',
  })
  return series
}

/**
 * Volume SMA line on the same pane/scale as the histogram.
 * @returns {import('lightweight-charts').ISeriesApi<'Line'>}
 */
export function createVolumeSmaSeries(chart) {
  return chart.addSeries(
    LineSeries,
    {
      color: VOLUME_SMA_COLOR,
      lineWidth: 1,
      priceScaleId: 'volume',
      priceLineVisible: false,
      lastValueVisible: false,
      crosshairMarkerVisible: false,
    },
    1,
  )
}

/**
 * Size the volume pane relative to the chart container height.
 * @param {import('lightweight-charts').IChartApi} chart
 * @param {number} containerHeight
 */
export function applyVolumePaneHeight(chart, containerHeight) {
  if (!chart || !(containerHeight > 0)) return
  const panes = chart.panes()
  if (panes.length < 2) return
  const volHeight = Math.max(48, Math.round(containerHeight * VOLUME_PANE_HEIGHT_RATIO))
  panes[1].setHeight(volHeight)
}

export function defaultChartOptions() {
  return {
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
      barSpacing: BAR_SPACING,
      minBarSpacing: 2,
    },
    crosshair: {
      vertLine: { color: '#475569' },
      horzLine: { color: '#475569' },
    },
  }
}

export function sortedCandlesFromMap(candlesMap) {
  return Array.from(candlesMap.values()).sort((a, b) => a.time - b.time)
}

export function seriesChartTimesFromCandles(candles) {
  return candles.map((c) => utcToNseChartTime(c.time))
}

/** Latest candle in the map, or null. */
export function latestCandleFromMap(candlesMap) {
  const candles = sortedCandlesFromMap(candlesMap)
  return candles.length ? candles[candles.length - 1] : null
}

/**
 * Find candle whose NSE chart time matches LWC crosshair time.
 * @param {Map<number, object>|Iterable} candlesMap
 * @param {number} chartTime LWC time (seconds, NSE-shifted)
 */
export function findCandleByChartTime(candlesMap, chartTime) {
  if (chartTime == null || !candlesMap) return null
  for (const candle of candlesMap.values()) {
    if (utcToNseChartTime(candle.time) === chartTime) {
      return candle
    }
  }
  return null
}

export function formatPrice(value) {
  const n = Number(value)
  if (!Number.isFinite(n)) return '—'
  return n.toLocaleString('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

/** Compact volume like TradingView (K / L / Cr for Indian scale feel). */
export function formatVolume(value) {
  const n = Number(value)
  if (!Number.isFinite(n) || n < 0) return '—'
  if (n >= 1e7) return `${(n / 1e7).toFixed(2)}Cr`
  if (n >= 1e5) return `${(n / 1e5).toFixed(2)}L`
  if (n >= 1e3) return `${(n / 1e3).toFixed(2)}K`
  return String(Math.round(n))
}

export function isCandleUp(candle) {
  if (!candle) return true
  return Number(candle.close) >= Number(candle.open)
}
