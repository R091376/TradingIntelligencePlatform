package com.tip.patterns.insidebar;

public record InsideBarConfig(
        int atrPeriod,
        double minMotherRangeAtrMult,
        double successAtrMult,
        int maxCandlesAfterDetect,
        /** How many bars after the inside bar a mother-range break still counts. */
        int maxBarsAfterInside,
        String detectorVersion
) {
    public static InsideBarConfig defaults() {
        return new InsideBarConfig(14, 0.5, 1.5, 20, 5, "inside-bar-v1");
    }
}
