/**
 * Live candle WebSocket client.
 * Always sends both symbolId and timeframe on subscribe.
 */
export function createLiveSocket({ symbolId, timeframe, onMessage, onStatus }) {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const ws = new WebSocket(`${protocol}//${window.location.host}/ws/live`)
  let currentSymbolId = symbolId
  let currentTimeframe = timeframe

  function sendSubscribe(nextSymbolId, nextTimeframe) {
    if (ws.readyState !== WebSocket.OPEN) return
    if (!nextSymbolId) return
    ws.send(
      JSON.stringify({
        type: 'subscribe',
        symbolId: nextSymbolId,
        timeframe: nextTimeframe,
      }),
    )
  }

  ws.onopen = () => {
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

  ws.onerror = () => onStatus('error')

  ws.onclose = () => onStatus('disconnected')

  onStatus('connecting')

  return {
    /**
     * Re-subscribe to a symbol + timeframe stream.
     * @param {string} nextSymbolId
     * @param {string} nextTimeframe
     */
    subscribe(nextSymbolId, nextTimeframe) {
      currentSymbolId = nextSymbolId
      currentTimeframe = nextTimeframe
      sendSubscribe(currentSymbolId, currentTimeframe)
    },
    close() {
      ws.close()
    },
  }
}
