package com.tip.config;

import com.tip.patterns.breakout.BreakoutConfig;
import com.tip.patterns.breakdown.BreakdownConfig;
import com.tip.patterns.model.ConfirmationMode;
import com.tip.patterns.consolidation.ConsolidationConfig;
import com.tip.patterns.engulfing.EngulfingConfig;
import com.tip.patterns.insidebar.InsideBarConfig;
import com.tip.patterns.pinbar.PinBarConfig;
import com.tip.patterns.structure.StructureConfig;
import com.tip.patterns.volumebreakout.VolumeBreakoutConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@ConfigurationProperties(prefix = "tip.pattern")
public class PatternProperties {

    private boolean enabled = true;
    private int atrPeriod = 14;
    private int statsMinSampleSize = 20;
    private Breakout breakout = new Breakout();
    private Breakdown breakdown = new Breakdown();
    private Pinbar pinbar = new Pinbar();
    private Engulfing engulfing = new Engulfing();
    private InsideBar insideBar = new InsideBar();
    private Structure structure = new Structure();
    private Consolidation consolidation = new Consolidation();
    private VolumeBreakout volumeBreakout = new VolumeBreakout();
    private Expiry expiry = new Expiry();
    private Ws ws = new Ws();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getAtrPeriod() {
        return atrPeriod;
    }

    public void setAtrPeriod(int atrPeriod) {
        this.atrPeriod = atrPeriod;
    }

    public int getStatsMinSampleSize() {
        return statsMinSampleSize;
    }

    public void setStatsMinSampleSize(int statsMinSampleSize) {
        this.statsMinSampleSize = statsMinSampleSize;
    }

    public Breakout getBreakout() {
        return breakout;
    }

    public void setBreakout(Breakout breakout) {
        this.breakout = breakout != null ? breakout : new Breakout();
    }

    public Breakdown getBreakdown() {
        return breakdown;
    }

    public void setBreakdown(Breakdown breakdown) {
        this.breakdown = breakdown != null ? breakdown : new Breakdown();
    }

    public Pinbar getPinbar() {
        return pinbar;
    }

    public void setPinbar(Pinbar pinbar) {
        this.pinbar = pinbar != null ? pinbar : new Pinbar();
    }

    public Engulfing getEngulfing() {
        return engulfing;
    }

    public void setEngulfing(Engulfing engulfing) {
        this.engulfing = engulfing != null ? engulfing : new Engulfing();
    }

    public InsideBar getInsideBar() {
        return insideBar;
    }

    public void setInsideBar(InsideBar insideBar) {
        this.insideBar = insideBar != null ? insideBar : new InsideBar();
    }

    public Structure getStructure() {
        return structure;
    }

    public void setStructure(Structure structure) {
        this.structure = structure != null ? structure : new Structure();
    }

    public Consolidation getConsolidation() {
        return consolidation;
    }

    public void setConsolidation(Consolidation consolidation) {
        this.consolidation = consolidation != null ? consolidation : new Consolidation();
    }

    public VolumeBreakout getVolumeBreakout() {
        return volumeBreakout;
    }

    public void setVolumeBreakout(VolumeBreakout volumeBreakout) {
        this.volumeBreakout = volumeBreakout != null ? volumeBreakout : new VolumeBreakout();
    }

    public Expiry getExpiry() {
        return expiry;
    }

    public void setExpiry(Expiry expiry) {
        this.expiry = expiry != null ? expiry : new Expiry();
    }

    public Ws getWs() {
        return ws;
    }

    public void setWs(Ws ws) {
        this.ws = ws != null ? ws : new Ws();
    }

    public BreakoutConfig toBreakoutConfig() {
        return new BreakoutConfig(
                breakout.lookbackCandles,
                atrPeriod,
                breakout.volumeAvgPeriod,
                ConfirmationMode.fromConfig(breakout.confirmationMode),
                breakout.volumeMultiplier,
                breakout.retestAtrMult,
                breakout.strengthenAtrMult,
                breakout.successRr,
                breakout.successAtrMultWithoutRetest,
                breakout.detectorVersion
        );
    }

    public BreakdownConfig toBreakdownConfig() {
        return new BreakdownConfig(
                breakdown.lookbackCandles,
                atrPeriod,
                breakdown.volumeAvgPeriod,
                ConfirmationMode.fromConfig(breakdown.confirmationMode),
                breakdown.volumeMultiplier,
                breakdown.retestAtrMult,
                breakdown.strengthenAtrMult,
                breakdown.successRr,
                breakdown.successAtrMultWithoutRetest,
                breakdown.detectorVersion
        );
    }

    public PinBarConfig toPinBarConfig() {
        return new PinBarConfig(
                atrPeriod,
                pinbar.shadowBodyMult,
                pinbar.maxBodyRangeRatio,
                pinbar.maxOppositeWickRangeRatio,
                pinbar.minRangeAtrMult,
                pinbar.successAtrMult,
                pinbar.requireTrendContext,
                pinbar.trendLookback,
                pinbar.maxCandlesAfterDetect,
                pinbar.detectorVersion
        );
    }

    public EngulfingConfig toEngulfingConfig() {
        return new EngulfingConfig(
                atrPeriod,
                engulfing.minRangeAtrMult,
                engulfing.successAtrMult,
                engulfing.maxCandlesAfterDetect,
                engulfing.detectorVersion
        );
    }

    public InsideBarConfig toInsideBarConfig() {
        return new InsideBarConfig(
                atrPeriod,
                insideBar.minMotherRangeAtrMult,
                insideBar.successAtrMult,
                insideBar.maxCandlesAfterDetect,
                insideBar.maxBarsAfterInside,
                insideBar.detectorVersion
        );
    }

    public StructureConfig toStructureConfig() {
        return new StructureConfig(
                atrPeriod,
                structure.fractalWidth,
                structure.successAtrMult,
                structure.maxCandlesAfterDetect,
                structure.detectorVersion
        );
    }

    public ConsolidationConfig toConsolidationConfig() {
        return new ConsolidationConfig(
                atrPeriod,
                consolidation.windowCandles,
                consolidation.rangeAtrMult,
                consolidation.maxDurationCandles,
                consolidation.tightenRatio,
                consolidation.detectorVersion
        );
    }

    public VolumeBreakoutConfig toVolumeBreakoutConfig() {
        return new VolumeBreakoutConfig(
                atrPeriod,
                volumeBreakout.volumeAvgPeriod,
                volumeBreakout.volumeMultiplier,
                volumeBreakout.minRangeAtrMult,
                volumeBreakout.successAtrMult,
                volumeBreakout.maxCandlesAfterDetect,
                volumeBreakout.detectorVersion
        );
    }

    public boolean isSessionCloseTimeframe(String timeframe) {
        return expiry.sessionCloseTimeframes().contains(timeframe);
    }

    /**
     * Survives overnight / process restart via hydrate (not session_end expire).
     * {@code 1h} uses 4h-style max-sessions + max-candles; {@code 1d} max-candles only.
     */
    public boolean isMultiDayTimeframe(String timeframe) {
        return "1h".equals(timeframe) || "4h".equals(timeframe) || "1d".equals(timeframe);
    }

    public int maxSessionsFor(String timeframe) {
        if ("1h".equals(timeframe)) {
            return expiry.getMaxSessions1h();
        }
        if ("4h".equals(timeframe)) {
            return expiry.getMaxSessions4h();
        }
        return Integer.MAX_VALUE;
    }

    public int maxCandlesFor(String timeframe) {
        if ("1h".equals(timeframe)) {
            return expiry.getMaxCandles1h();
        }
        if ("4h".equals(timeframe)) {
            return expiry.getMaxCandles4h();
        }
        if ("1d".equals(timeframe)) {
            return expiry.getMaxCandles1d();
        }
        return Integer.MAX_VALUE;
    }

    /** TFs that bump sessions_seen on market close (1h, 4h). */
    public boolean tracksSessionsOnClose(String timeframe) {
        return "1h".equals(timeframe) || "4h".equals(timeframe);
    }

    public static class Breakout {
        private int lookbackCandles = 20;
        private int volumeAvgPeriod = 20;
        private String confirmationMode = "both";
        private double volumeMultiplier = 1.5;
        private double retestAtrMult = 0.25;
        private double strengthenAtrMult = 1.0;
        private double successRr = 2.0;
        private double successAtrMultWithoutRetest = 2.0;
        private String detectorVersion = "breakout-v1";

        public int getLookbackCandles() {
            return lookbackCandles;
        }

        public void setLookbackCandles(int lookbackCandles) {
            this.lookbackCandles = lookbackCandles;
        }

        public int getVolumeAvgPeriod() {
            return volumeAvgPeriod;
        }

        public void setVolumeAvgPeriod(int volumeAvgPeriod) {
            this.volumeAvgPeriod = volumeAvgPeriod;
        }

        public String getConfirmationMode() {
            return confirmationMode;
        }

        public void setConfirmationMode(String confirmationMode) {
            this.confirmationMode = confirmationMode;
        }

        public double getVolumeMultiplier() {
            return volumeMultiplier;
        }

        public void setVolumeMultiplier(double volumeMultiplier) {
            this.volumeMultiplier = volumeMultiplier;
        }

        public double getRetestAtrMult() {
            return retestAtrMult;
        }

        public void setRetestAtrMult(double retestAtrMult) {
            this.retestAtrMult = retestAtrMult;
        }

        public double getStrengthenAtrMult() {
            return strengthenAtrMult;
        }

        public void setStrengthenAtrMult(double strengthenAtrMult) {
            this.strengthenAtrMult = strengthenAtrMult;
        }

        public double getSuccessRr() {
            return successRr;
        }

        public void setSuccessRr(double successRr) {
            this.successRr = successRr;
        }

        public double getSuccessAtrMultWithoutRetest() {
            return successAtrMultWithoutRetest;
        }

        public void setSuccessAtrMultWithoutRetest(double successAtrMultWithoutRetest) {
            this.successAtrMultWithoutRetest = successAtrMultWithoutRetest;
        }

        public String getDetectorVersion() {
            return detectorVersion;
        }

        public void setDetectorVersion(String detectorVersion) {
            this.detectorVersion = detectorVersion;
        }
    }

    /** Same knobs as {@link Breakout}; independent tuning and detector version. */
    public static class Breakdown {
        private int lookbackCandles = 20;
        private int volumeAvgPeriod = 20;
        private String confirmationMode = "both";
        private double volumeMultiplier = 1.5;
        private double retestAtrMult = 0.25;
        private double strengthenAtrMult = 1.0;
        private double successRr = 2.0;
        private double successAtrMultWithoutRetest = 2.0;
        private String detectorVersion = "breakdown-v1";

        public int getLookbackCandles() {
            return lookbackCandles;
        }

        public void setLookbackCandles(int lookbackCandles) {
            this.lookbackCandles = lookbackCandles;
        }

        public int getVolumeAvgPeriod() {
            return volumeAvgPeriod;
        }

        public void setVolumeAvgPeriod(int volumeAvgPeriod) {
            this.volumeAvgPeriod = volumeAvgPeriod;
        }

        public String getConfirmationMode() {
            return confirmationMode;
        }

        public void setConfirmationMode(String confirmationMode) {
            this.confirmationMode = confirmationMode;
        }

        public double getVolumeMultiplier() {
            return volumeMultiplier;
        }

        public void setVolumeMultiplier(double volumeMultiplier) {
            this.volumeMultiplier = volumeMultiplier;
        }

        public double getRetestAtrMult() {
            return retestAtrMult;
        }

        public void setRetestAtrMult(double retestAtrMult) {
            this.retestAtrMult = retestAtrMult;
        }

        public double getStrengthenAtrMult() {
            return strengthenAtrMult;
        }

        public void setStrengthenAtrMult(double strengthenAtrMult) {
            this.strengthenAtrMult = strengthenAtrMult;
        }

        public double getSuccessRr() {
            return successRr;
        }

        public void setSuccessRr(double successRr) {
            this.successRr = successRr;
        }

        public double getSuccessAtrMultWithoutRetest() {
            return successAtrMultWithoutRetest;
        }

        public void setSuccessAtrMultWithoutRetest(double successAtrMultWithoutRetest) {
            this.successAtrMultWithoutRetest = successAtrMultWithoutRetest;
        }

        public String getDetectorVersion() {
            return detectorVersion;
        }

        public void setDetectorVersion(String detectorVersion) {
            this.detectorVersion = detectorVersion;
        }
    }

    /** Hammer / Shooting Star (shared pin-bar geometry). */
    public static class Pinbar {
        private double shadowBodyMult = 2.0;
        private double maxBodyRangeRatio = 0.35;
        private double maxOppositeWickRangeRatio = 0.25;
        private double minRangeAtrMult = 0.5;
        private double successAtrMult = 1.5;
        private boolean requireTrendContext = true;
        private int trendLookback = 10;
        private int maxCandlesAfterDetect = 20;
        private String detectorVersion = "pinbar-v1";

        public double getShadowBodyMult() {
            return shadowBodyMult;
        }

        public void setShadowBodyMult(double shadowBodyMult) {
            this.shadowBodyMult = shadowBodyMult;
        }

        public double getMaxBodyRangeRatio() {
            return maxBodyRangeRatio;
        }

        public void setMaxBodyRangeRatio(double maxBodyRangeRatio) {
            this.maxBodyRangeRatio = maxBodyRangeRatio;
        }

        public double getMaxOppositeWickRangeRatio() {
            return maxOppositeWickRangeRatio;
        }

        public void setMaxOppositeWickRangeRatio(double maxOppositeWickRangeRatio) {
            this.maxOppositeWickRangeRatio = maxOppositeWickRangeRatio;
        }

        public double getMinRangeAtrMult() {
            return minRangeAtrMult;
        }

        public void setMinRangeAtrMult(double minRangeAtrMult) {
            this.minRangeAtrMult = minRangeAtrMult;
        }

        public double getSuccessAtrMult() {
            return successAtrMult;
        }

        public void setSuccessAtrMult(double successAtrMult) {
            this.successAtrMult = successAtrMult;
        }

        public boolean isRequireTrendContext() {
            return requireTrendContext;
        }

        public void setRequireTrendContext(boolean requireTrendContext) {
            this.requireTrendContext = requireTrendContext;
        }

        public int getTrendLookback() {
            return trendLookback;
        }

        public void setTrendLookback(int trendLookback) {
            this.trendLookback = trendLookback;
        }

        public int getMaxCandlesAfterDetect() {
            return maxCandlesAfterDetect;
        }

        public void setMaxCandlesAfterDetect(int maxCandlesAfterDetect) {
            this.maxCandlesAfterDetect = maxCandlesAfterDetect;
        }

        public String getDetectorVersion() {
            return detectorVersion;
        }

        public void setDetectorVersion(String detectorVersion) {
            this.detectorVersion = detectorVersion;
        }
    }

    public static class Engulfing {
        private double minRangeAtrMult = 0.5;
        private double successAtrMult = 1.5;
        private int maxCandlesAfterDetect = 20;
        private String detectorVersion = "engulfing-v1";

        public double getMinRangeAtrMult() {
            return minRangeAtrMult;
        }

        public void setMinRangeAtrMult(double minRangeAtrMult) {
            this.minRangeAtrMult = minRangeAtrMult;
        }

        public double getSuccessAtrMult() {
            return successAtrMult;
        }

        public void setSuccessAtrMult(double successAtrMult) {
            this.successAtrMult = successAtrMult;
        }

        public int getMaxCandlesAfterDetect() {
            return maxCandlesAfterDetect;
        }

        public void setMaxCandlesAfterDetect(int maxCandlesAfterDetect) {
            this.maxCandlesAfterDetect = maxCandlesAfterDetect;
        }

        public String getDetectorVersion() {
            return detectorVersion;
        }

        public void setDetectorVersion(String detectorVersion) {
            this.detectorVersion = detectorVersion;
        }
    }

    public static class InsideBar {
        private double minMotherRangeAtrMult = 0.5;
        private double successAtrMult = 1.5;
        private int maxCandlesAfterDetect = 20;
        private int maxBarsAfterInside = 5;
        private String detectorVersion = "inside-bar-v1";

        public double getMinMotherRangeAtrMult() {
            return minMotherRangeAtrMult;
        }

        public void setMinMotherRangeAtrMult(double minMotherRangeAtrMult) {
            this.minMotherRangeAtrMult = minMotherRangeAtrMult;
        }

        public double getSuccessAtrMult() {
            return successAtrMult;
        }

        public void setSuccessAtrMult(double successAtrMult) {
            this.successAtrMult = successAtrMult;
        }

        public int getMaxCandlesAfterDetect() {
            return maxCandlesAfterDetect;
        }

        public void setMaxCandlesAfterDetect(int maxCandlesAfterDetect) {
            this.maxCandlesAfterDetect = maxCandlesAfterDetect;
        }

        public int getMaxBarsAfterInside() {
            return maxBarsAfterInside;
        }

        public void setMaxBarsAfterInside(int maxBarsAfterInside) {
            this.maxBarsAfterInside = maxBarsAfterInside;
        }

        public String getDetectorVersion() {
            return detectorVersion;
        }

        public void setDetectorVersion(String detectorVersion) {
            this.detectorVersion = detectorVersion;
        }
    }

    public static class Structure {
        private int fractalWidth = 2;
        private double successAtrMult = 1.5;
        private int maxCandlesAfterDetect = 30;
        private String detectorVersion = "structure-v1";

        public int getFractalWidth() {
            return fractalWidth;
        }

        public void setFractalWidth(int fractalWidth) {
            this.fractalWidth = fractalWidth;
        }

        public double getSuccessAtrMult() {
            return successAtrMult;
        }

        public void setSuccessAtrMult(double successAtrMult) {
            this.successAtrMult = successAtrMult;
        }

        public int getMaxCandlesAfterDetect() {
            return maxCandlesAfterDetect;
        }

        public void setMaxCandlesAfterDetect(int maxCandlesAfterDetect) {
            this.maxCandlesAfterDetect = maxCandlesAfterDetect;
        }

        public String getDetectorVersion() {
            return detectorVersion;
        }

        public void setDetectorVersion(String detectorVersion) {
            this.detectorVersion = detectorVersion;
        }
    }

    public static class Consolidation {
        private int windowCandles = 10;
        private double rangeAtrMult = 1.5;
        private int maxDurationCandles = 30;
        private double tightenRatio = 0.85;
        private String detectorVersion = "consolidation-v1";

        public int getWindowCandles() {
            return windowCandles;
        }

        public void setWindowCandles(int windowCandles) {
            this.windowCandles = windowCandles;
        }

        public double getRangeAtrMult() {
            return rangeAtrMult;
        }

        public void setRangeAtrMult(double rangeAtrMult) {
            this.rangeAtrMult = rangeAtrMult;
        }

        public int getMaxDurationCandles() {
            return maxDurationCandles;
        }

        public void setMaxDurationCandles(int maxDurationCandles) {
            this.maxDurationCandles = maxDurationCandles;
        }

        public double getTightenRatio() {
            return tightenRatio;
        }

        public void setTightenRatio(double tightenRatio) {
            this.tightenRatio = tightenRatio;
        }

        public String getDetectorVersion() {
            return detectorVersion;
        }

        public void setDetectorVersion(String detectorVersion) {
            this.detectorVersion = detectorVersion;
        }
    }

    public static class VolumeBreakout {
        private int volumeAvgPeriod = 20;
        private double volumeMultiplier = 2.0;
        private double minRangeAtrMult = 0.5;
        private double successAtrMult = 1.5;
        private int maxCandlesAfterDetect = 20;
        private String detectorVersion = "volume-breakout-v1";

        public int getVolumeAvgPeriod() {
            return volumeAvgPeriod;
        }

        public void setVolumeAvgPeriod(int volumeAvgPeriod) {
            this.volumeAvgPeriod = volumeAvgPeriod;
        }

        public double getVolumeMultiplier() {
            return volumeMultiplier;
        }

        public void setVolumeMultiplier(double volumeMultiplier) {
            this.volumeMultiplier = volumeMultiplier;
        }

        public double getMinRangeAtrMult() {
            return minRangeAtrMult;
        }

        public void setMinRangeAtrMult(double minRangeAtrMult) {
            this.minRangeAtrMult = minRangeAtrMult;
        }

        public double getSuccessAtrMult() {
            return successAtrMult;
        }

        public void setSuccessAtrMult(double successAtrMult) {
            this.successAtrMult = successAtrMult;
        }

        public int getMaxCandlesAfterDetect() {
            return maxCandlesAfterDetect;
        }

        public void setMaxCandlesAfterDetect(int maxCandlesAfterDetect) {
            this.maxCandlesAfterDetect = maxCandlesAfterDetect;
        }

        public String getDetectorVersion() {
            return detectorVersion;
        }

        public void setDetectorVersion(String detectorVersion) {
            this.detectorVersion = detectorVersion;
        }
    }

    public static class Expiry {
        /** Hard-expire open instances at NSE session close (not hydrated across restarts). */
        private List<String> sessionCloseTimeframes = List.of("1m", "5m", "15m");
        private int maxSessions1h = 5;
        private int maxCandles1h = 60;
        private int maxSessions4h = 5;
        private int maxCandles4h = 60;
        private int maxCandles1d = 30;

        public List<String> getSessionCloseTimeframes() {
            return sessionCloseTimeframes;
        }

        public void setSessionCloseTimeframes(List<String> sessionCloseTimeframes) {
            this.sessionCloseTimeframes = sessionCloseTimeframes != null && !sessionCloseTimeframes.isEmpty()
                    ? List.copyOf(sessionCloseTimeframes)
                    : List.of("1m", "5m", "15m");
        }

        public Set<String> sessionCloseTimeframes() {
            return Set.copyOf(sessionCloseTimeframes);
        }

        public int getMaxSessions1h() {
            return maxSessions1h;
        }

        public void setMaxSessions1h(int maxSessions1h) {
            this.maxSessions1h = maxSessions1h;
        }

        public int getMaxCandles1h() {
            return maxCandles1h;
        }

        public void setMaxCandles1h(int maxCandles1h) {
            this.maxCandles1h = maxCandles1h;
        }

        public int getMaxSessions4h() {
            return maxSessions4h;
        }

        public void setMaxSessions4h(int maxSessions4h) {
            this.maxSessions4h = maxSessions4h;
        }

        public int getMaxCandles4h() {
            return maxCandles4h;
        }

        public void setMaxCandles4h(int maxCandles4h) {
            this.maxCandles4h = maxCandles4h;
        }

        public int getMaxCandles1d() {
            return maxCandles1d;
        }

        public void setMaxCandles1d(int maxCandles1d) {
            this.maxCandles1d = maxCandles1d;
        }
    }

    public static class Ws {
        private List<String> broadcastStages = new ArrayList<>(List.of(
                "detected", "confirmed", "retested", "strengthened",
                "succeeded", "failed", "expired"
        ));

        public List<String> getBroadcastStages() {
            return broadcastStages;
        }

        public void setBroadcastStages(List<String> broadcastStages) {
            this.broadcastStages = broadcastStages != null
                    ? new ArrayList<>(broadcastStages)
                    : new ArrayList<>();
        }

        public boolean shouldBroadcast(String stageWire) {
            if (stageWire == null) {
                return false;
            }
            String key = stageWire.toLowerCase(Locale.ROOT);
            return broadcastStages.stream().anyMatch(s -> s.equalsIgnoreCase(key));
        }
    }
}
