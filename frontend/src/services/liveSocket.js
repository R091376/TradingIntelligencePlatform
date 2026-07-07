export function createLiveSocket({ timeframe, onMessage, onStatus }) {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const ws = new WebSocket(`${protocol}//${window.location.host}/ws/live`)

  ws.onopen = () => {
    onStatus('connected')
    ws.send(JSON.stringify({ type: 'subscribe', timeframe }))
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
    close() {
      ws.close()
    },
  }
}