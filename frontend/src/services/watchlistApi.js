/**
 * Watchlist REST client.
 * symbolId is the Upstox instrument_key (e.g. "NSE_INDEX|Nifty 50") and must be
 * path-encoded when used in URL segments.
 */

export function encodeSymbolId(symbolId) {
  return encodeURIComponent(symbolId)
}

const cred = { credentials: 'include' }

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
  if (status === 401) {
    return 'Not signed in. Please log in again.'
  }
  if (status === 403) {
    return 'Only admins can add or remove watchlist symbols.'
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
  const response = await fetch('/api/watchlist', cred)
  return handleResponse(response)
}

/**
 * POST /api/watchlist — blocking add (ADMIN only).
 * @param {{ symbol?: string, instrumentKey?: string } | string} input
 */
export async function addSymbol(input) {
  let body
  if (typeof input === 'string') {
    body = { symbol: input }
  } else if (input && typeof input === 'object') {
    body = {}
    if (input.instrumentKey) body.instrumentKey = input.instrumentKey
    if (input.symbol) body.symbol = input.symbol
  } else {
    body = {}
  }

  const response = await fetch('/api/watchlist', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  return handleResponse(response)
}

/**
 * DELETE /api/watchlist/{symbolId} (ADMIN only).
 * @param {string} symbolId instrument key
 */
export async function removeSymbol(symbolId) {
  const response = await fetch(`/api/watchlist/${encodeSymbolId(symbolId)}`, {
    method: 'DELETE',
    credentials: 'include',
  })
  return handleResponse(response)
}
