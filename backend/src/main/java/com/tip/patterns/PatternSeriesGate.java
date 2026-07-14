package com.tip.patterns;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Per {@code symbolId|timeframe} mutual exclusion for pattern evaluate / expire / remove.
 *
 * <p>Only one series key is ever held at a time (no nested multi-key locks) to avoid deadlocks.
 * Different series remain concurrent.
 */
@Component
public class PatternSeriesGate {

    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public static String key(String symbolId, String timeframe) {
        return symbolId + "|" + timeframe;
    }

    public void run(String symbolId, String timeframe, Runnable action) {
        Object lock = locks.computeIfAbsent(key(symbolId, timeframe), k -> new Object());
        synchronized (lock) {
            action.run();
        }
    }

    public <T> T call(String symbolId, String timeframe, Supplier<T> action) {
        Object lock = locks.computeIfAbsent(key(symbolId, timeframe), k -> new Object());
        synchronized (lock) {
            return action.get();
        }
    }

    /** Drop lock objects for a removed symbol (all timeframes). */
    public void clearSymbol(String symbolId) {
        if (symbolId == null) {
            return;
        }
        String prefix = symbolId + "|";
        locks.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
