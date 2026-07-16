package com.tip.patterns.structure;

public record StructureConfig(
        int atrPeriod,
        int fractalWidth,
        double successAtrMult,
        int maxCandlesAfterDetect,
        String detectorVersion
) {
    public static StructureConfig defaults() {
        return new StructureConfig(14, 2, 1.5, 30, "structure-v1");
    }
}
