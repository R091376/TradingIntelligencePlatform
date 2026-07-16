package com.tip.market;

import com.tip.market.event.CandleClosedEvent;
import com.tip.market.event.CandleUpdatedEvent;
import com.tip.market.model.Candle;
import com.tip.market.model.Tick;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CandleEngine {

    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, SymbolState> stateByKey = new ConcurrentHashMap<>();

    public CandleEngine(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void seed(String instrumentKey, String timeframe, List<Candle> candles) {
        int intervalMinutes = TimeframeParser.parse(timeframe).intervalMinutes();
        SymbolState state = stateFor(instrumentKey, timeframe);

        synchronized (state) {
            state.closedCandles.clear();
            state.currentCandle = null;
            state.lastVtt = 0L;
            state.lastVttInitialized = false;

            if (candles.isEmpty()) {
                return;
            }

            long currentBoundary = CandleBoundaryUtils.floorToCandleStartEpochSecond(
                    Instant.now().toEpochMilli(),
                    intervalMinutes
            );
            Candle last = candles.get(candles.size() - 1);

            if (last.time() == currentBoundary) {
                state.closedCandles.addAll(candles.subList(0, candles.size() - 1));
                state.currentCandle = MutableCandle.from(last);
            } else {
                state.closedCandles.addAll(candles);
            }
        }
        // seed does not publish CandleClosedEvent (live-forward pattern eval)
    }

    public void processTick(Tick tick, String timeframe) {
        int intervalMinutes = TimeframeParser.parse(timeframe).intervalMinutes();
        SymbolState state = stateFor(tick.instrumentKey(), timeframe);

        List<Object> pendingEvents = new ArrayList<>(4);
        synchronized (state) {
            long candleStart = CandleBoundaryUtils.floorToCandleStartEpochSecond(
                    tick.timestampMs(),
                    intervalMinutes
            );
            long volumeDelta = computeVolumeDelta(state, tick.volumeTradedToday());

            if (state.currentCandle == null || state.currentCandle.time != candleStart) {
                if (state.currentCandle != null) {
                    Optional<Candle> closed = closeCurrentCandle(state);
                    closed.ifPresent(c -> {
                        pendingEvents.add(new CandleClosedEvent(tick.instrumentKey(), timeframe, c));
                        pendingEvents.add(new CandleUpdatedEvent(tick.instrumentKey(), timeframe, c, true));
                    });
                }
                state.currentCandle = MutableCandle.open(candleStart, tick.price(), volumeDelta);
            } else {
                state.currentCandle.update(tick.price(), volumeDelta);
            }

            pendingEvents.add(new CandleUpdatedEvent(
                    tick.instrumentKey(),
                    timeframe,
                    state.currentCandle.toCandle(),
                    false
            ));
        }

        // Publish outside the per-series lock so listeners may do I/O / read engine state.
        for (Object event : pendingEvents) {
            eventPublisher.publishEvent(event);
        }
    }

    public Optional<Candle> getCurrentCandle(String instrumentKey, String timeframe) {
        SymbolState state = stateByKey.get(stateKey(instrumentKey, timeframe));
        if (state == null) {
            return Optional.empty();
        }
        synchronized (state) {
            return state.currentCandle == null
                    ? Optional.empty()
                    : Optional.of(state.currentCandle.toCandle());
        }
    }

    /**
     * Closed bars only (no in-progress candle). Preferred lookback for indicators and patterns.
     */
    public List<Candle> getClosedCandles(String instrumentKey, String timeframe) {
        SymbolState state = stateByKey.get(stateKey(instrumentKey, timeframe));
        if (state == null) {
            return List.of();
        }
        synchronized (state) {
            return Collections.unmodifiableList(new ArrayList<>(state.closedCandles));
        }
    }

    /**
     * Closed bars plus current in-progress bar (if any). For chart REST/WS seeding only —
     * do not use for ATR/Donchian/pattern lookbacks ({@link #getClosedCandles}).
     */
    public List<Candle> getAllCandles(String instrumentKey, String timeframe) {
        SymbolState state = stateByKey.get(stateKey(instrumentKey, timeframe));
        if (state == null) {
            return List.of();
        }
        synchronized (state) {
            List<Candle> all = new ArrayList<>(state.closedCandles);
            if (state.currentCandle != null) {
                Candle current = state.currentCandle.toCandle();
                if (all.isEmpty()) {
                    all.add(current);
                } else {
                    long lastTime = all.get(all.size() - 1).time();
                    if (current.time() > lastTime) {
                        all.add(current);
                    } else if (current.time() == lastTime) {
                        // Live in-progress replaces matching seed bar
                        all.set(all.size() - 1, current);
                    }
                    // current.time() < lastTime: misaligned live bar — omit so clients
                    // never see non-monotonic series (LWC rejects out-of-order data).
                }
            }
            return Collections.unmodifiableList(all);
        }
    }

    /**
     * Remove all timeframe state for an instrument. Safe if none exists.
     * State key is {@code instrumentKey + "|" + timeframe}; prefix match on
     * {@code instrumentKey + "|"} is correct because timeframe has no {@code |}.
     */
    public void evict(String instrumentKey) {
        if (instrumentKey == null || instrumentKey.isBlank()) {
            return;
        }
        String prefix = instrumentKey + "|";
        stateByKey.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /**
     * Close every in-progress bar whose open time is strictly before the current
     * wall-clock bucket for its timeframe. Used when ticks stop (session end, quiet
     * periods) so patterns and charts still receive a final close.
     *
     * @param nowEpochMs wall clock in epoch millis
     * @return number of candles closed
     */
    public int closeStaleOpenCandles(long nowEpochMs) {
        int closedCount = 0;
        for (Map.Entry<String, SymbolState> entry : stateByKey.entrySet()) {
            String key = entry.getKey();
            int pipe = key.lastIndexOf('|');
            if (pipe <= 0 || pipe >= key.length() - 1) {
                continue;
            }
            String instrumentKey = key.substring(0, pipe);
            String timeframe = key.substring(pipe + 1);
            int intervalMinutes;
            try {
                intervalMinutes = TimeframeParser.parse(timeframe).intervalMinutes();
            } catch (RuntimeException ex) {
                continue;
            }
            long currentBoundary = CandleBoundaryUtils.floorToCandleStartEpochSecond(
                    nowEpochMs, intervalMinutes);

            List<Object> pendingEvents = new ArrayList<>(2);
            synchronized (entry.getValue()) {
                SymbolState state = entry.getValue();
                if (state.currentCandle == null) {
                    continue;
                }
                // Still the active bucket — leave open for live ticks.
                if (state.currentCandle.time >= currentBoundary) {
                    continue;
                }
                Optional<Candle> closed = closeCurrentCandle(state);
                closed.ifPresent(c -> {
                    pendingEvents.add(new CandleClosedEvent(instrumentKey, timeframe, c));
                    pendingEvents.add(new CandleUpdatedEvent(instrumentKey, timeframe, c, true));
                });
            }
            for (Object event : pendingEvents) {
                eventPublisher.publishEvent(event);
            }
            if (!pendingEvents.isEmpty()) {
                closedCount++;
            }
        }
        return closedCount;
    }

    /**
     * Mutates state only; caller publishes events outside the lock.
     */
    private Optional<Candle> closeCurrentCandle(SymbolState state) {
        Candle closed = state.currentCandle.toCandle();
        state.currentCandle = null;
        if (!state.closedCandles.isEmpty()) {
            long lastTime = state.closedCandles.get(state.closedCandles.size() - 1).time();
            if (closed.time() < lastTime) {
                // Drop misaligned live bar rather than corrupt series order
                return Optional.empty();
            }
            if (closed.time() == lastTime) {
                state.closedCandles.set(state.closedCandles.size() - 1, closed);
                return Optional.of(closed);
            }
        }
        state.closedCandles.add(closed);
        return Optional.of(closed);
    }

    private long computeVolumeDelta(SymbolState state, long volumeTradedToday) {
        if (volumeTradedToday <= 0) {
            return 0L;
        }
        if (!state.lastVttInitialized) {
            state.lastVtt = volumeTradedToday;
            state.lastVttInitialized = true;
            return 0L;
        }
        if (volumeTradedToday < state.lastVtt) {
            state.lastVtt = volumeTradedToday;
            return volumeTradedToday;
        }
        long delta = volumeTradedToday - state.lastVtt;
        state.lastVtt = volumeTradedToday;
        return delta;
    }

    private SymbolState stateFor(String instrumentKey, String timeframe) {
        return stateByKey.computeIfAbsent(stateKey(instrumentKey, timeframe), key -> new SymbolState());
    }

    private static String stateKey(String instrumentKey, String timeframe) {
        return instrumentKey + "|" + timeframe;
    }

    private static final class SymbolState {
        private final List<Candle> closedCandles = new ArrayList<>();
        private MutableCandle currentCandle;
        private long lastVtt;
        private boolean lastVttInitialized;
    }

    private static final class MutableCandle {
        private long time;
        private double open;
        private double high;
        private double low;
        private double close;
        private long volume;

        private static MutableCandle open(long time, double price, long volumeDelta) {
            MutableCandle candle = new MutableCandle();
            candle.time = time;
            candle.open = price;
            candle.high = price;
            candle.low = price;
            candle.close = price;
            candle.volume = volumeDelta;
            return candle;
        }

        private static MutableCandle from(Candle candle) {
            MutableCandle mutable = new MutableCandle();
            mutable.time = candle.time();
            mutable.open = candle.open();
            mutable.high = candle.high();
            mutable.low = candle.low();
            mutable.close = candle.close();
            mutable.volume = candle.volume();
            return mutable;
        }

        private void update(double price, long volumeDelta) {
            high = Math.max(high, price);
            low = Math.min(low, price);
            close = price;
            volume += volumeDelta;
        }

        private Candle toCandle() {
            return new Candle(time, open, high, low, close, volume);
        }
    }
}
