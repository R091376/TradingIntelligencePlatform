export default function TimeframeSelector({ timeframes, value, onChange, disabled }) {
  if (!timeframes?.length) {
    return null
  }

  return (
    <div className="timeframe-selector" role="group" aria-label="Timeframe">
      {timeframes.map((tf) => (
        <button
          key={tf}
          type="button"
          className={value === tf ? 'active' : ''}
          disabled={disabled}
          onClick={() => onChange(tf)}
        >
          {tf}
        </button>
      ))}
    </div>
  )
}
