package com.tip.patterns.structure;

import com.tip.market.model.Candle;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/** Bill Williams–style fractal swing points (strict). */
public final class SwingPoints {

    private SwingPoints() {
    }

    public record Pivot(int index, double price, boolean high) {
    }

    /**
     * Index of the pivot just confirmed by the last closed bar, if any.
     * Candidate pivot index = n - 1 - width.
     */
    public static OptionalInt confirmedPivotIndex(int n, int width) {
        if (width < 1 || n < 2 * width + 1) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(n - 1 - width);
    }

    public static boolean isPivotHigh(List<Candle> bars, int i, int width) {
        if (i < width || i + width >= bars.size()) {
            return false;
        }
        double h = bars.get(i).high();
        for (int j = i - width; j <= i + width; j++) {
            if (j == i) {
                continue;
            }
            if (!(h > bars.get(j).high())) {
                return false;
            }
        }
        return true;
    }

    public static boolean isPivotLow(List<Candle> bars, int i, int width) {
        if (i < width || i + width >= bars.size()) {
            return false;
        }
        double l = bars.get(i).low();
        for (int j = i - width; j <= i + width; j++) {
            if (j == i) {
                continue;
            }
            if (!(l < bars.get(j).low())) {
                return false;
            }
        }
        return true;
    }

    /** Prior pivot highs strictly before {@code beforeIndex}, chronological. */
    public static List<Pivot> pivotHighsBefore(List<Candle> bars, int beforeIndex, int width) {
        List<Pivot> out = new ArrayList<>();
        int lastConfirmable = bars.size() - 1 - width;
        for (int i = width; i < beforeIndex && i <= lastConfirmable; i++) {
            if (isPivotHigh(bars, i, width)) {
                out.add(new Pivot(i, bars.get(i).high(), true));
            }
        }
        return out;
    }

    public static List<Pivot> pivotLowsBefore(List<Candle> bars, int beforeIndex, int width) {
        List<Pivot> out = new ArrayList<>();
        int lastConfirmable = bars.size() - 1 - width;
        for (int i = width; i < beforeIndex && i <= lastConfirmable; i++) {
            if (isPivotLow(bars, i, width)) {
                out.add(new Pivot(i, bars.get(i).low(), false));
            }
        }
        return out;
    }
}
