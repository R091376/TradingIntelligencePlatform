package com.tip.market;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeframeParser {

    private static final Pattern PATTERN = Pattern.compile("^(\\d+)([mhd])$");

    private TimeframeParser() {
    }

    public record TimeframeSpec(String unit, int interval) {
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