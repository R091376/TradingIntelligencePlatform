/**
 * Watchlist REST client.
 * symbolId is the Upstox instrument_key (e.g. "NSE_INDEX|Nifty 50") and must be
 * path-encoded when used in URL segments.
 */

export function encodeSymbolId(symbolId) {
  return encodeURIComponent(symbolId)
}

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
  if (status === 409) {
    return body || 'Symbol already on watchlist or watchlist is full.'
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
  if (response.status === 204) {
    return null
  }
  return response.json()
}

/** GET /api/watchlist — ordered public-active entries. */
export async function fetchWatchlist() {
  const response = await fetch('/api/watchlist')
  return handleResponse(response)
}

/**
 * POST /api/watchlist — blocking add by trading symbol.
 * May take up to ~120s while the server seeds candles.
 * @param {string} symbol trading symbol (e.g. "RELIANCE"), not instrument key
 */
export async function addSymbol(symbol) {
  const response = await fetch('/api/watchlist', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ symbol }),
  })
  return handleResponse(response)
}

/**
 * DELETE /api/watchlist/{symbolId}
 * @param {string} symbolId instrument key
 */
export async function removeSymbol(symbolId) {
  const response = await fetch(`/api/watchlist/${encodeSymbolId(symbolId)}`, {
    method: 'DELETE',
  })
  return handleResponse(response)
}
