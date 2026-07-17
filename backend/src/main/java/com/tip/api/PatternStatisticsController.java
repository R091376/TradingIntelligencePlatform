package com.tip.api;

import com.tip.config.PatternProperties;
import com.tip.journal.PatternJournal;
import com.tip.patterns.PatternFeatureGuard;
import com.tip.watchlist.WatchlistEntry;
import com.tip.watchlist.WatchlistRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Research ranking API over pre-aggregated {@code pattern_statistics} rows
 * for the active watchlist. Authenticated users (all roles).
 */
@RestController
@RequestMapping("/api/pattern-statistics")
public class PatternStatisticsController {

    private final PatternFeatureGuard featureGuard;
    private final PatternJournal patternJournal;
    private final PatternProperties patternProperties;
    private final WatchlistRepository watchlistRepository;

    public PatternStatisticsController(
            PatternFeatureGuard featureGuard,
            PatternJournal patternJournal,
            PatternProperties patternProperties,
            WatchlistRepository watchlistRepository
    ) {
        this.featureGuard = featureGuard;
        this.patternJournal = patternJournal;
        this.patternProperties = patternProperties;
        this.watchlistRepository = watchlistRepository;
    }

    /**
     * Bulk statistics for ranking.
     *
     * @param patternType optional filter (e.g. breakout)
     * @param timeframe   optional filter (e.g. 5m)
     */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) String patternType,
            @RequestParam(required = false) String timeframe
    ) {
        if (!featureGuard.isFullyEnabled()) {
            throw new PatternsDisabledException();
        }

        List<WatchlistEntry> active = watchlistRepository.findAllActive();
        List<String> symbolIds = active.stream().map(WatchlistEntry::symbolId).toList();
        Map<String, WatchlistEntry> byId = active.stream()
                .collect(Collectors.toMap(WatchlistEntry::symbolId, e -> e, (a, b) -> a, LinkedHashMap::new));

        String type = blankToNull(patternType);
        if (type != null) {
            type = type.toLowerCase(Locale.ROOT);
        }
        String tf = blankToNull(timeframe);

        int minSample = patternProperties.getStatsMinSampleSize();
        List<PatternJournal.PatternStatisticsSnapshot> rows =
                patternJournal.findStatisticsForSymbols(symbolIds, type, tf);

        List<Map<String, Object>> items = new ArrayList<>(rows.size());
        for (PatternJournal.PatternStatisticsSnapshot s : rows) {
            items.add(toItem(s, minSample, byId.get(s.symbolId())));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("minSampleSize", minSample);
        body.put("count", items.size());
        body.put("items", items);
        return body;
    }

    /**
     * Counts always; rates/means only when {@code sampleSize >= minSampleSize}.
     */
    static Map<String, Object> toItem(
            PatternJournal.PatternStatisticsSnapshot s,
            int minSampleSize,
            WatchlistEntry entry
    ) {
        Map<String, Object> item = new LinkedHashMap<>();
        boolean ready = s.sampleSize() >= minSampleSize;
        item.put("status", ready ? "ok" : "insufficient_history");
        item.put("symbolId", s.symbolId());
        if (entry != null) {
            item.put("tradingSymbol", entry.tradingSymbol());
            item.put("displayName", entry.displayName());
        } else {
            item.put("tradingSymbol", null);
            item.put("displayName", null);
        }
        item.put("patternType", s.patternType());
        item.put("timeframe", s.timeframe());
        item.put("sampleSize", s.sampleSize());
        item.put("successCount", s.successCount());
        item.put("failCount", s.failCount());
        item.put("expiredCount", s.expiredCount());
        item.put("minSampleSize", minSampleSize);

        // Counts always; rates/means only when ready (UI shows "—" below gate).
        item.put("resolvedSampleSize", s.resolvedSampleSize());
        item.put("moveSampleSize", s.moveSampleSize());
        item.put("mfeSampleSize", s.mfeSampleSize());
        item.put("maeSampleSize", s.maeSampleSize());
        item.put("updatedAt", s.updatedAt() != null ? s.updatedAt().toString() : null);

        if (ready) {
            item.put("successRate", s.successRate());
            item.put("resolvedSuccessRate", s.resolvedSuccessRate());
            item.put("avgMoveR", s.avgMoveR());
            item.put("avgDurationCandles", s.avgDurationCandles());
            item.put("avgMfeR", s.avgMfeR());
            item.put("avgMaeR", s.avgMaeR());
        } else {
            item.put("successRate", null);
            item.put("resolvedSuccessRate", null);
            item.put("avgMoveR", null);
            item.put("avgDurationCandles", null);
            item.put("avgMfeR", null);
            item.put("avgMaeR", null);
        }
        return item;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
