package com.tip.api;

import com.tip.api.dto.InstrumentSearchHitDto;
import com.tip.instrument.InstrumentMasterCache;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Instrument master query endpoints (autocomplete / typeahead).
 */
@RestController
@RequestMapping("/api/instruments")
public class InstrumentController {

    private static final int DEFAULT_LIMIT = 15;
    private static final int MAX_LIMIT = 50;

    private final InstrumentMasterCache instrumentMasterCache;

    public InstrumentController(InstrumentMasterCache instrumentMasterCache) {
        this.instrumentMasterCache = instrumentMasterCache;
    }

    /**
     * Typeahead search over NSE EQ + INDEX master rows.
     * Query length under 2 returns an empty list (no 400).
     */
    @GetMapping("/search")
    public List<InstrumentSearchHitDto> search(
            @RequestParam(name = "q", required = false, defaultValue = "") String q,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        int lim = limit == null ? DEFAULT_LIMIT : Math.min(MAX_LIMIT, Math.max(1, limit));
        return instrumentMasterCache.search(q, lim).stream()
                .map(InstrumentSearchHitDto::from)
                .toList();
    }
}
