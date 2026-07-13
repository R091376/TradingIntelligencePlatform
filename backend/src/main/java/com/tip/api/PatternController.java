package com.tip.api;

import com.tip.api.dto.PatternInstanceDto;
import com.tip.api.dto.PatternListResponse;
import com.tip.config.PatternProperties;
import com.tip.journal.PatternJournal;
import com.tip.patterns.ActiveInstanceStore;
import com.tip.patterns.PatternFeatureGuard;
import com.tip.patterns.model.ActivePattern;
import com.tip.watchlist.WatchlistRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/symbols")
public class PatternController {

    private final PatternFeatureGuard featureGuard;
    private final PatternJournal patternJournal;
    private final ActiveInstanceStore activeInstanceStore;
    private final PatternProperties patternProperties;
    private final WatchlistRepository watchlistRepository;

    public PatternController(
            PatternFeatureGuard featureGuard,
            PatternJournal patternJournal,
            ActiveInstanceStore activeInstanceStore,
            PatternProperties patternProperties,
            WatchlistRepository watchlistRepository
    ) {
        this.featureGuard = featureGuard;
        this.patternJournal = patternJournal;
        this.activeInstanceStore = activeInstanceStore;
        this.patternProperties = patternProperties;
        this.watchlistRepository = watchlistRepository;
    }

    /**
     * @param status {@code active} (default) | {@code closed} | {@code all}
     */
    @GetMapping("/{symbolId:.+}/patterns")
    public PatternListResponse listPatterns(
            @PathVariable("symbolId") String symbolId,
            @RequestParam(defaultValue = "active") String status,
            @RequestParam(required = false) String timeframe
    ) {
        requirePatternsEnabled();
        requireOnWatchlist(symbolId);

        String filter = status == null ? "active" : status.trim().toLowerCase(Locale.ROOT);
        if (!filter.equals("active") && !filter.equals("closed") && !filter.equals("all")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "status must be active, closed, or all"
            );
        }

        List<ActivePattern> patterns = switch (filter) {
            case "active" -> loadActive(symbolId, timeframe);
            case "closed" -> patternJournal.findRecent(symbolId, "closed", 100);
            default -> patternJournal.findRecent(symbolId, "all", 100);
        };

        if ("closed".equals(filter) && timeframe != null && !timeframe.isBlank()) {
            patterns = patterns.stream()
                    .filter(p -> timeframe.equals(p.timeframe()))
                    .toList();
        }
        if ("all".equals(filter) && timeframe != null && !timeframe.isBlank()) {
            patterns = patterns.stream()
                    .filter(p -> timeframe.equals(p.timeframe()))
                    .toList();
        }

        return new PatternListResponse(
                symbolId,
                filter,
                patterns.stream().map(this::toDto).toList()
        );
    }

    @GetMapping("/{symbolId:.+}/statistics")
    public Map<String, Object> statistics(
            @PathVariable("symbolId") String symbolId,
            @RequestParam(defaultValue = "breakout") String patternType,
            @RequestParam(required = false) String timeframe
    ) {
        requirePatternsEnabled();
        requireOnWatchlist(symbolId);
        if (timeframe == null || timeframe.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "timeframe is required");
        }

        return patternJournal.findStatistics(symbolId, patternType.toLowerCase(Locale.ROOT), timeframe)
                .map(s -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    if (s.sampleSize() < patternProperties.getStatsMinSampleSize()) {
                        body.put("status", "insufficient_history");
                        body.put("sampleSize", s.sampleSize());
                        body.put("minSampleSize", patternProperties.getStatsMinSampleSize());
                        return body;
                    }
                    body.put("status", "ok");
                    body.put("symbolId", s.symbolId());
                    body.put("patternType", s.patternType());
                    body.put("timeframe", s.timeframe());
                    body.put("sampleSize", s.sampleSize());
                    body.put("successCount", s.successCount());
                    body.put("failCount", s.failCount());
                    body.put("expiredCount", s.expiredCount());
                    body.put("successRate", s.successRate());
                    body.put("resolvedSuccessRate", s.resolvedSuccessRate());
                    body.put("resolvedSampleSize", s.resolvedSampleSize());
                    body.put("avgMoveR", s.avgMoveR());
                    body.put("avgDurationCandles", s.avgDurationCandles());
                    body.put("avgMfeR", s.avgMfeR());
                    body.put("avgMaeR", s.avgMaeR());
                    body.put("moveSampleSize", s.moveSampleSize());
                    body.put("mfeSampleSize", s.mfeSampleSize());
                    body.put("maeSampleSize", s.maeSampleSize());
                    body.put("updatedAt", s.updatedAt() != null ? s.updatedAt().toString() : null);
                    return body;
                })
                .orElseGet(() -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("status", "insufficient_history");
                    body.put("sampleSize", 0);
                    body.put("minSampleSize", patternProperties.getStatsMinSampleSize());
                    return body;
                });
    }

    private List<ActivePattern> loadActive(String symbolId, String timeframe) {
        if (timeframe != null && !timeframe.isBlank()) {
            List<ActivePattern> patterns = activeInstanceStore.getOpen(symbolId, timeframe);
            if (patterns.isEmpty() && patternJournal.isActive()) {
                patterns = patternJournal.findOpen(symbolId, timeframe);
            }
            return patterns;
        }
        List<ActivePattern> patterns = new ArrayList<>(activeInstanceStore.getOpenForSymbol(symbolId));
        if (patterns.isEmpty() && patternJournal.isActive()) {
            patterns = patternJournal.findOpenForSymbol(symbolId);
        }
        return patterns;
    }

    private void requirePatternsEnabled() {
        if (!featureGuard.isFullyEnabled()) {
            throw new PatternsDisabledException();
        }
    }

    private void requireOnWatchlist(String symbolId) {
        if (!watchlistRepository.containsSymbolId(symbolId)
                && watchlistRepository.findBySymbolId(symbolId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Symbol not on watchlist: " + symbolId);
        }
    }

    private PatternInstanceDto toDto(ActivePattern p) {
        Map<String, Boolean> flags = Map.of(
                "confirmed", p.flagConfirmed(),
                "retested", p.flagRetested(),
                "strengthened", p.flagStrengthened()
        );
        return new PatternInstanceDto(
                p.id().toString(),
                p.symbolId(),
                p.patternType().wireValue(),
                p.timeframe(),
                p.direction().wireValue(),
                p.status().wireValue(),
                flags,
                p.referenceLevel(),
                p.atrAtDetect(),
                p.entryPrice(),
                p.stopLevel(),
                p.targetLevel(),
                p.retestFloor(),
                p.detectCandleTime(),
                p.detectedAt() != null ? p.detectedAt().toString() : null,
                p.confirmedAt() != null ? p.confirmedAt().toString() : null,
                p.detectorVersion(),
                p.confirmationModeUsed().wireValue()
        );
    }
}
