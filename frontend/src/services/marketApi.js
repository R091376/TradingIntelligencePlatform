function parseErrorMessage(status, body) {
  if (status === 503) {
    return body || 'Market data is temporarily unavailable. Check your Upstox token in .env.'
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

export async function fetchSymbol() {
  const response = await fetch('/api/market/symbol')
  return handleResponse(response)
}

export async function fetchMarketStatus() {
  const response = await fetch('/api/market/status')
  return handleResponse(response)
}

export async function fetchCandles({ from, to } = {}) {
  const params = new URLSearchParams()
  if (from != null) params.set('from', String(from))
  if (to != null) params.set('to', String(to))

  const query = params.toString()
  const url = query ? `/api/market/candles?${query}` : '/api/market/candles'
  const response = await fetch(url)
  return handleResponse(response)
}