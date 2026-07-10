package com.tip.instrument;

/**
 * Resolved Upstox instrument from the NSE master (EQ/BE equities and indexes only).
 */
public record ResolvedInstrument(
        String instrumentKey,
        String tradingSymbol,
        String exchange,
        String segment,
        String instrumentType,
        String displayName,
        String isin
) {
}
