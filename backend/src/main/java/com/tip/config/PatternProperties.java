package com.tip.config;

import com.tip.patterns.breakout.BreakoutConfig;
import com.tip.patterns.breakdown.BreakdownConfig;
import com.tip.patterns.model.ConfirmationMode;
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
