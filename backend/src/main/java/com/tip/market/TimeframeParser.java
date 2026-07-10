package com.tip.market;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeframeParser {

    private static final Pattern PATTERN = Pattern.compile("^(\\d+)([mhd])$");

    private TimeframeParser() {
    }

    /**
     * @param unit     Upstox unit: {@code minutes}, {@code hours}, {@code days}
     * @param interval Upstox interval integer for that unit (e.g. 15 with minutes, 4 with hours)
     */
    public record TimeframeSpec(String unit, int interval) {
        /**
         * Candle length in minutes for in-memory boundary flooring / live engine.
         * Distinct from {@link #interval()}, which is the raw Upstox path parameter.
         */
        public int intervalMinutes() {
            return switch (unit) {
                case "minutes" -> interval;
                case "hours" -> Math.multiplyExact(interval, 60);
                case "days" -> Math.multiplyExact(interval, 24 * 60);
                default -> throw new IllegalArgumentException("Unsupported unit: " + unit);
            };
        }
    }

    public static TimeframeSpec parse(String timeframe) {
        Matcher matcher = PATTERN.matcher(timeframe);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unsupported timeframe: " + timeframe);
        }

        int interval = Integer.parseInt(matcher.group(1));
        String suffix = matcher.group(2);

        String unit = switch (suffix) {
            case "m" -> "minutes";
            case "h" -> "hours";
            case "d" -> "days";
            default -> throw new IllegalArgumentException("Unsupported timeframe suffix: " + suffix);
        };

        return new TimeframeSpec(unit, interval);
    }
}