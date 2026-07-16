package com.tip.patterns.pinbar;

import com.tip.market.model.Candle;

/**
 * Nison-style pin-bar geometry (pure). See {@code docs/patterns/hammer-shooting-star.md}.
 */
public final class PinBarGeometry {

    private PinBarGeometry() {
    }

    public enum Kind {
        HAMMER,
        SHOOTING_STAR
    }

    public record Metrics(
            double body,
            double range,
            double upper,
            double lower,
            double mid
    ) {
    }

    public static Metrics metrics(Candle c) {
        double body = Math.abs(c.close() - c.open());
        double range = c.high() - c.low();
        double upper = c.high() - Math.max(c.open(), c.close());
        double lower = Math.min(c.open(), c.close()) - c.low();
        double mid = (c.open() + c.close()) / 2.0;
        return new Metrics(body, range, upper, lower, mid);
    }

    /**
     * @return which pin kind matches, or empty if neither
     */
    public static java.util.Optional<Kind> classify(Candle signal, double atr, PinBarConfig config) {
        if (signal == null || !(atr > 0) || config == null) {
            return java.util.Optional.empty();
        }
        Metrics m = metrics(signal);
        if (!(m.range() >= config.minRangeAtrMult() * atr)) {
            return java.util.Optional.empty();
        }
        if (!(m.range() > 0)) {
            return java.util.Optional.empty();
        }

        boolean smallBody = m.body() <= config.maxBodyRangeRatio() * m.range();
        // Near-doji: allow tiny body if dominant wick is large fraction of range
        boolean dojiPin = m.body() <= 1e-12
                || (m.body() / m.range() <= 0.05);

        if (isHammerShape(m, config, smallBody, dojiPin)) {
            return java.util.Optional.of(Kind.HAMMER);
        }
        if (isShootingStarShape(m, config, smallBody, dojiPin)) {
            return java.util.Optional.of(Kind.SHOOTING_STAR);
        }
        return java.util.Optional.empty();
    }

    private static boolean isHammerShape(Metrics m, PinBarConfig config, boolean smallBody, boolean dojiPin) {
        if (!(m.lower() >= config.shadowBodyMult() * Math.max(m.body(), 1e-12) || (dojiPin && m.lower() >= 0.6 * m.range()))) {
            return false;
        }
        if (!(m.upper() <= config.maxOppositeWickRangeRatio() * m.range())) {
            return false;
        }
        return smallBody || dojiPin;
    }

    private static boolean isShootingStarShape(Metrics m, PinBarConfig config, boolean smallBody, boolean dojiPin) {
        if (!(m.upper() >= config.shadowBodyMult() * Math.max(m.body(), 1e-12) || (dojiPin && m.upper() >= 0.6 * m.range()))) {
            return false;
        }
        if (!(m.lower() <= config.maxOppositeWickRangeRatio() * m.range())) {
            return false;
        }
        return smallBody || dojiPin;
    }
}
