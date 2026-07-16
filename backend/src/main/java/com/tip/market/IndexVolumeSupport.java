package com.tip.market;

import com.upstox.feeder.MarketUpdateV3;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Index live feeds ({@code fullFeed.indexFF}) have no equity-style {@code vtt}.
 * Upstox still may populate {@code marketOHLC} with interval volumes.
 * <p>
 * Strategy (best practical, all INDEX instruments):
 * <ol>
 *   <li>Prefer {@code 1d} OHLC {@code vol} as session cumulative volume (acts like VTT).</li>
 *   <li>Else rebuild session volume from successive {@code I1} bar volumes.</li>
 *   <li>Else 0 (exchange/feed simply has no volume for that index).</li>
 * </ol>
 * CandleEngine keeps using VTT deltas — no equity path changes.
 */
public final class IndexVolumeSupport {

    private final Map<String, OneMinuteSessionTracker> trackersByKey = new ConcurrentHashMap<>();
    /** Instrument keys that already emitted a first-tick volume debug line. */
    private final Set<String> firstTickLogged = ConcurrentHashMap.newKeySet();

    /**
     * Resolve a cumulative "volume traded today" proxy for an index tick.
     */
    public long resolveVolumeTradedToday(String instrumentKey, MarketUpdateV3.IndexFullFeed indexFeed) {
        return resolve(instrumentKey, indexFeed).volumeTradedToday();
    }

    /**
     * Full resolve with diagnostics (day/I1 raw fields + source used).
     */
    public ResolveResult resolve(String instrumentKey, MarketUpdateV3.IndexFullFeed indexFeed) {
        if (indexFeed == null) {
            return ResolveResult.empty("null_feed");
        }
        MarketUpdateV3.MarketOHLC marketOHLC = indexFeed.getMarketOHLC();
        if (marketOHLC == null || marketOHLC.getOhlc() == null || marketOHLC.getOhlc().isEmpty()) {
            return ResolveResult.empty("no_ohlc");
        }

        List<MarketUpdateV3.OHLC> bars = marketOHLC.getOhlc();
        long dayVol = findIntervalVolume(bars, "1d");
        OneMinuteBar i1 = findOneMinuteBar(bars);
        long i1Vol = i1 != null ? i1.vol() : 0L;
        long i1Ts = i1 != null ? i1.tsMs() : 0L;
        int ohlcCount = bars.size();

        if (dayVol > 0) {
            return new ResolveResult(dayVol, dayVol, i1Vol, i1Ts, ohlcCount, "1d");
        }

        if (i1 == null) {
            return new ResolveResult(0L, dayVol, 0L, 0L, ohlcCount, "none");
        }

        long session = trackersByKey
                .computeIfAbsent(instrumentKey, k -> new OneMinuteSessionTracker())
                .onOneMinuteUpdate(i1.tsMs(), i1.vol());
        return new ResolveResult(session, dayVol, i1Vol, i1Ts, ohlcCount, "I1");
    }

    /**
     * @return true once per instrument key until {@link #clear} / {@link #clearAll}
     */
    public boolean shouldLogFirstTick(String instrumentKey) {
        if (instrumentKey == null || instrumentKey.isBlank()) {
            return false;
        }
        return firstTickLogged.add(instrumentKey);
    }

    public void clear(String instrumentKey) {
        if (instrumentKey != null) {
            trackersByKey.remove(instrumentKey);
            firstTickLogged.remove(instrumentKey);
        }
    }

    public void clearAll() {
        trackersByKey.clear();
        firstTickLogged.clear();
    }

    /**
     * @param volumeTradedToday value passed into {@link com.tip.market.model.Tick}
     * @param dayVol            raw {@code 1d} OHLC vol (0 if missing)
     * @param i1Vol             raw {@code I1} OHLC vol (0 if missing)
     * @param i1TsMs            I1 bar timestamp millis (0 if missing)
     * @param ohlcCount         number of OHLC rows on the feed
     * @param source            {@code 1d}, {@code I1}, {@code none}, {@code no_ohlc}, {@code null_feed}
     */
    public record ResolveResult(
            long volumeTradedToday,
            long dayVol,
            long i1Vol,
            long i1TsMs,
            int ohlcCount,
            String source
    ) {
        static ResolveResult empty(String source) {
            return new ResolveResult(0L, 0L, 0L, 0L, 0, source);
        }
    }

    static long findIntervalVolume(List<MarketUpdateV3.OHLC> bars, String interval) {
        if (bars == null || interval == null) {
            return 0L;
        }
        for (MarketUpdateV3.OHLC bar : bars) {
            if (bar == null || bar.getInterval() == null) {
                continue;
            }
            if (interval.equalsIgnoreCase(bar.getInterval())) {
                return Math.max(0L, bar.getVol());
            }
        }
        return 0L;
    }

    static OneMinuteBar findOneMinuteBar(List<MarketUpdateV3.OHLC> bars) {
        if (bars == null) {
            return null;
        }
        for (MarketUpdateV3.OHLC bar : bars) {
            if (bar == null || bar.getInterval() == null) {
                continue;
            }
            String interval = bar.getInterval();
            if ("I1".equalsIgnoreCase(interval) || "1m".equalsIgnoreCase(interval)) {
                return new OneMinuteBar(normalizeTsMs(bar.getTs()), Math.max(0L, bar.getVol()));
            }
        }
        return null;
    }

    /**
     * Upstox OHLC {@code ts} is epoch millis; tolerate seconds if a short value appears.
     */
    static long normalizeTsMs(long ts) {
        // ~ year 2001 in seconds vs millis
        if (ts > 0 && ts < 1_000_000_000_000L) {
            return ts * 1000L;
        }
        return ts;
    }

    /**
     * Rebuilds session cumulative volume from streaming 1-minute OHLC volumes.
     * When the I1 bucket advances, the previous bar's last seen volume is locked into the sum.
     */
    static final class OneMinuteSessionTracker {
        private long lastTsMs;
        private long lastVol;
        private long closedSum;
        private boolean initialized;

        synchronized long onOneMinuteUpdate(long tsMs, long vol) {
            if (tsMs <= 0) {
                return closedSum + Math.max(0L, lastVol);
            }
            long safeVol = Math.max(0L, vol);

            if (!initialized) {
                lastTsMs = tsMs;
                lastVol = safeVol;
                initialized = true;
                return closedSum + lastVol;
            }

            // Day / session reset: timestamp went backwards materially (e.g. new session)
            if (tsMs + 60_000L < lastTsMs) {
                closedSum = 0L;
                lastTsMs = tsMs;
                lastVol = safeVol;
                return closedSum + lastVol;
            }

            if (tsMs > lastTsMs) {
                closedSum += lastVol;
                lastTsMs = tsMs;
                lastVol = safeVol;
            } else if (tsMs == lastTsMs) {
                lastVol = safeVol;
            }
            // tsMs < lastTsMs but not a full reset: ignore stale bar
            return closedSum + lastVol;
        }
    }

    record OneMinuteBar(long tsMs, long vol) {
    }
}
