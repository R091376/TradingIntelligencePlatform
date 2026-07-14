import {
  CandlestickSeries,
  ColorType,
  LineSeries,
} from 'lightweight-charts'
import { utcToNseChartTime } from '../utils/chartTime'

/** Candle/bar width in px (LWC default ~6). Applied on create and after symbol/TF load. */
export const BAR_SPACING = 7

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
