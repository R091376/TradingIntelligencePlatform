/**
 * Live candle WebSocket client with auto-reconnect.
 * Always sends both symbolId and timeframe on subscribe.
 */
export function createLiveSocket({ symbolId, timeframe, onMessage, onStatus }) {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const url = `${protocol}//${window.location.host}/ws/live`

  let currentSymbolId = symbolId
  let currentTimeframe = timeframe
  let ws = null
  let closedIntentionally = false
  let reconnectTimer = null
  let attempt = 0

  const BASE_DELAY_MS = 1000
  const MAX_DELAY_MS = 30000

  function clearReconnectTimer() {
    if (reconnectTimer != null) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  function sendSubscribe(nextSymbolId, nextTimeframe) {
    if (!ws || ws.readyState !== WebSocket.OPEN) return
    if (!nextSymbolId) return
    ws.send(
      JSON.stringify({
        type: 'subscribe',
        symbolId: nextSymbolId,
        timeframe: nextTimeframe,
      }),
    )
  }

  function scheduleReconnect() {
    if (closedIntentionally) return
    clearReconnectTimer()
    const delay = Math.min(BASE_DELAY_MS * 2 ** attempt, MAX_DELAY_MS)
    attempt += 1
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      connect()
    }, delay)
  }

  function connect() {
    if (closedIntentionally) return

    clearReconnectTimer()
    try {
      ws = new WebSocket(url)
    } catch {
      onStatus('error')
      scheduleReconnect()
      return
    }

    onStatus('connecting')

    ws.onopen = () => {
      attempt = 0
      onStatus('connected')
      sendSubscribe(currentSymbolId, currentTimeframe)
    }

    ws.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data)
        if (message.type === 'error') {
          onStatus('error', message.message)
          return
        }
        onMessage(message)
      } catch {
        onStatus('error')
      }
    }

    ws.onerror = () => {
      // onclose will fire after; avoid double reconnect
      onStatus('error')
    }

    ws.onclose = () => {
      ws = null
      if (closedIntentionally) {
        onStatus('disconnected')
        return
      }
      onStatus('disconnected')
      scheduleReconnect()
    }
  }

  connect()

  return {
    /**
     * Re-subscribe to a symbol + timeframe stream.
     * Queued until the socket is OPEN (including mid-reconnect).
     * @param {string} nextSymbolId
     * @param {string} nextTimeframe
     */
    subscribe(nextSymbolId, nextTimeframe) {
      currentSymbolId = nextSymbolId
      currentTimeframe = nextTimeframe
      sendSubscribe(currentSymbolId, currentTimeframe)
    },
    close() {
      closedIntentionally = true
      clearReconnectTimer()
      if (ws) {
        try {
          ws.close()
        } catch {
          // ignore
        }
        ws = null
      }
    },
  }
}
