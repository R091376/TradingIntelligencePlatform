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
        int intervalMinutes = TimeframeParser.parse(timeframe).interval();
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
    }

    public void processTick(Tick tick, String timeframe) {
        int intervalMinutes = TimeframeParser.parse(timeframe).interval();
        SymbolState state = stateFor(tick.instrumentKey(), timeframe);

        synchronized (state) {
            long candleStart = CandleBoundaryUtils.floorToCandleStartEpochSecond(
                    tick.timestampMs(),
                    intervalMinutes
            );
            long volumeDelta = computeVolumeDelta(state, tick.volumeTradedToday());

            if (state.currentCandle == null || state.currentCandle.time != candleStart) {
                if (state.currentCandle != null) {
                    closeCurrentCandle(state, tick.instrumentKey(), timeframe);
                }
                state.currentCandle = MutableCandle.open(candleStart, tick.price(), volumeDelta);
            } else {
                state.currentCandle.update(tick.price(), volumeDelta);
            }

            publishUpdated(tick.instrumentKey(), timeframe, state.currentCandle.toCandle(), false);
        }
    }

    public List<Candle> getClosedCandles(String instrumentKey, String timeframe) {
        SymbolState state = stateByKey.get(stateKey(instrumentKey, timeframe));
        if (state == null) {
            return List.of();
        }
        synchronized (state) {
            return List.copyOf(state.closedCandles);
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

    public List<Candle> getAllCandles(String instrumentKey, String timeframe) {
        SymbolState state = stateByKey.get(stateKey(instrumentKey, timeframe));
        if (state == null) {
            return List.of();
        }
        synchronized (state) {
            List<Candle> all = new ArrayList<>(state.closedCandles);
            if (state.currentCandle != null) {
                all.add(state.currentCandle.toCandle());
            }
            return Collections.unmodifiableList(all);
        }
    }

    private void closeCurrentCandle(SymbolState state, String instrumentKey, String timeframe) {
        Candle closed = state.currentCandle.toCandle();
        state.closedCandles.add(closed);
        state.currentCandle = null;
        eventPublisher.publishEvent(new CandleClosedEvent(instrumentKey, timeframe, closed));
        publishUpdated(instrumentKey, timeframe, closed, true);
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

    private void publishUpdated(String instrumentKey, String timeframe, Candle candle, boolean isFinal) {
        eventPublisher.publishEvent(new CandleUpdatedEvent(instrumentKey, timeframe, candle, isFinal));
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