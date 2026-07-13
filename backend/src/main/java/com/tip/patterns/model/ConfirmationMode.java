package com.tip.patterns.model;

import java.util.Locale;

/**
 * Configured or effective confirmation mode stored on the instance.
 */
public enum ConfirmationMode {
    CLOSE,
    VOLUME,
    BOTH,
    /** Volume unusable — fall back to close-only confirmation. */
    CLOSE_FALLBACK;

    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ConfirmationMode fromConfig(String raw) {
        if (raw == null || raw.isBlank()) {
            return BOTH;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "close" -> CLOSE;
            case "volume" -> VOLUME;
            case "both" -> BOTH;
            case "close_fallback" -> CLOSE_FALLBACK;
            default -> throw new IllegalArgumentException("Unknown confirmation mode: " + raw);
        };
    }
}
