package com.tip.watchlist;

import java.time.Instant;

/**
 * Domain record for a watchlist symbol.
 * <p>
 * {@code symbolId} is the Upstox {@code instrument_key} and is the primary key
 * (e.g. {@code NSE_INDEX|Nifty 50}, {@code NSE_EQ|INE002A01018}).
 */
public record WatchlistEntry(
        String symbolId,
        String tradingSymbol,
        String exchange,
        String segment,
        String instrumentType,
        String displayName,
        Instant addedAt,
        boolean active,
        SymbolBootstrapStatus bootstrapStatus,
        String bootstrapError
) {
}
