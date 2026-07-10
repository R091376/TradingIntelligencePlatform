package com.tip.api.dto;

import com.tip.instrument.ResolvedInstrument;

/**
 * One row in {@code GET /api/instruments/search} results.
 */
public record InstrumentSearchHitDto(
        String instrumentKey,
        String tradingSymbol,
        String displayName,
        String exchange,
        String segment,
        String instrumentType
) {
    public static InstrumentSearchHitDto from(ResolvedInstrument r) {
        return new InstrumentSearchHitDto(
                r.instrumentKey(),
                r.tradingSymbol(),
                r.displayName(),
                r.exchange(),
                r.segment(),
                r.instrumentType()
        );
    }
}
