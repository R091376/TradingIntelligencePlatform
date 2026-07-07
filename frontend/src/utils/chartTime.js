const NSE_TIMEZONE = 'Asia/Kolkata'

const nsePartsFormatter = new Intl.DateTimeFormat('en-US', {
  timeZone: NSE_TIMEZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
})

function partValue(parts, type) {
  return Number(parts.find((part) => part.type === type)?.value ?? 0)
}

/**
 * Lightweight Charts has no native timezone support — it always displays UTC
 * components. Shift each bar timestamp so UTC wall-clock matches IST.
 *
 * Uses formatToParts instead of the docs' toLocaleString round-trip, which
 * breaks when the browser is already in the target timezone.
 *
 * @see https://tradingview.github.io/lightweight-charts/docs/time-zones
 */
export function utcToNseChartTime(utcSeconds) {
  const parts = nsePartsFormatter.formatToParts(new Date(utcSeconds * 1000))
  return Math.floor(
    Date.UTC(
      partValue(parts, 'year'),
      partValue(parts, 'month') - 1,
      partValue(parts, 'day'),
      partValue(parts, 'hour'),
      partValue(parts, 'minute'),
      partValue(parts, 'second'),
    ) / 1000,
  )
}