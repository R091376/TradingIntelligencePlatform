package com.tip.instrument;

/**
 * Thrown when a trading symbol cannot be resolved against the instrument master.
 */
public class InstrumentNotFoundException extends RuntimeException {

    private final String tradingSymbol;

    public InstrumentNotFoundException(String tradingSymbol) {
        super("Unknown trading symbol: " + tradingSymbol);
        this.tradingSymbol = tradingSymbol;
    }

    public String getTradingSymbol() {
        return tradingSymbol;
    }
}
