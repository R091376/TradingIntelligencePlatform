/**
 * Instrument master search client (autocomplete).
 */

function parseErrorMessage(status, body) {
  if (status >= 500) {
    return 'Backend error while searching instruments.'
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
 * GET /api/instruments/search?q=&limit=
 * @param {string} q
 * @param {number} [limit=15]
 * @param {AbortSignal} [signal]
 * @returns {Promise<Array<{
 *   instrumentKey: string,
 *   tradingSymbol: string,
 *   displayName: string,
 *   exchange: string,
 *   segment: string,
 *   instrumentType: string
 * }>>}
 */
export async function searchInstruments(q, limit = 15, signal) {
  const params = new URLSearchParams()
  params.set('q', q ?? '')
  if (limit != null) params.set('limit', String(limit))
  const response = await fetch(`/api/instruments/search?${params.toString()}`, {
    signal,
  })
  return handleResponse(response)
}
