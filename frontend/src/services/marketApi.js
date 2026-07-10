import { encodeSymbolId } from './watchlistApi'

const DEFAULT_TIMEFRAMES = ['1m', '5m', '15m', '1h', '4h', '1d']
const DEFAULT_TIMEFRAME = '5m'

function parseErrorMessage(status, body) {
  if (status === 503) {
    return body || 'Market data is temporarily unavailable. Check your Upstox token in .env.'
  }
  if (status === 400) {
    return body || 'Invalid request.'
  }
  if (status === 404) {
    return body || 'Symbol not found.'
  }
  if (status === 401 || status === 403) {
    return 'Upstox access token is invalid or expired. Update UPSTOX_ACCESS_TOKEN in .env.'
  }
  if (status >= 500) {
    return 'Backend error. Make sure the server is running on port 8080.'
  }
  return body || `Request failed (${status})`
}

async function handleResponse(response) {
  if (!response.ok) {
    const text = await response.text()
    throw new Error(parseErrorMessage(response.status, text))
  }
  return response.json()
}

/**
 * Compat shim: primary symbol from /api/market/symbol.
 * Prefer watchlist[0] after init (KD25) — this is only a fallback.
 */
export async function fetchSymbol() {
  const response = await fetch('/api/market/symbol')
  return handleResponse(response)
}

export async function fetchMarketStatus() {
  const response = await fetch('/api/market/status')
  return handleResponse(response)
}

/**
 * Supported timeframes. Falls back to hardcoded defaults when the backend
 * has no /api/market/timeframes endpoint.
 */
export async function fetchTimeframes() {
  try {
    const response = await fetch('/api/market/timeframes')
    if (!response.ok) {
      return { defaultTimeframe: DEFAULT_TIMEFRAME, supported: DEFAULT_TIMEFRAMES }
    }
    return response.json()
  } catch {
    return { defaultTimeframe: DEFAULT_TIMEFRAME, supported: DEFAULT_TIMEFRAMES }
  }
}

/**
 * Per-symbol candles: GET /api/symbols/{encoded}/candles
 * @param {{ symbolId: string, timeframe?: string, from?: number, to?: number }} opts
 */
export async function fetchCandles({ symbolId, timeframe, from, to } = {}) {
  if (!symbolId) {
    throw new Error('symbolId is required to fetch candles')
  }

  const params = new URLSearchParams()
  if (timeframe != null) params.set('timeframe', timeframe)
  if (from != null) params.set('from', String(from))
  if (to != null) params.set('to', String(to))

  const query = params.toString()
  const base = `/api/symbols/${encodeSymbolId(symbolId)}/candles`
  const url = query ? `${base}?${query}` : base
  const response = await fetch(url)
  return handleResponse(response)
}

export { DEFAULT_TIMEFRAMES, DEFAULT_TIMEFRAME }
