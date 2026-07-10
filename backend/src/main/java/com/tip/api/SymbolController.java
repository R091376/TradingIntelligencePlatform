package com.tip.api;

import com.tip.api.dto.CandleDto;
import com.tip.config.MarketProperties;
import com.tip.market.CandleEngine;
import com.tip.watchlist.SymbolBootstrapStatus;
import com.tip.watchlist.WatchlistEntry;
import com.tip.watchlist.WatchlistRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

/**
 * Per-symbol candles API (KD21 matrix).
 */
@RestController
@RequestMapping("/api/symbols")
public class SymbolController {

    private final CandleEngine candleEngine;
    private final WatchlistRepository watchlistRepository;
    private final MarketProperties marketProperties;

    public SymbolController(
            CandleEngine candleEngine,
            WatchlistRepository watchlistRepository,
            MarketProperties marketProperties
    ) {
        this.candleEngine = candleEngine;
        this.watchlistRepository = watchlistRepository;
        this.marketProperties = marketProperties;
    }

    /**
     * GET candles for a watchlist symbol.
     * <ul>
     *   <li>not on watchlist / REMOVING → 404</li>
     *   <li>FAILED → 503</li>
     *   <li>PENDING → 200 []</li>
     *   <li>READY → 200 candles (possibly empty for unseeded TF)</li>
     *   <li>unsupported timeframe → 400</li>
     * </ul>
     */
    @GetMapping("/{symbolId:.+}/candles")
    public List<CandleDto> getCandles(
            @PathVariable("symbolId") String symbolId,
            @RequestParam(required = false) String timeframe,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to
    ) {
        String tf = timeframe != null && !timeframe.isBlank()
                ? timeframe
                : marketProperties.defaultTimeframe();

        if (!marketProperties.isSupportedTimeframe(tf)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported timeframe: " + tf + ". Supported: " + marketProperties.supportedTimeframes()
            );
        }

        Optional<WatchlistEntry> opt = watchlistRepository.findBySymbolId(symbolId);
        if (opt.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Symbol not on watchlist: " + symbolId
            );
        }

        WatchlistEntry entry = opt.get();
        if (entry.bootstrapStatus() == SymbolBootstrapStatus.REMOVING || !entry.active()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Symbol not on watchlist: " + symbolId
            );
        }

        if (entry.bootstrapStatus() == SymbolBootstrapStatus.FAILED) {
            String msg = entry.bootstrapError() != null
                    ? entry.bootstrapError()
                    : "Bootstrap failed for symbol: " + symbolId;
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, msg);
        }

        if (entry.bootstrapStatus() == SymbolBootstrapStatus.PENDING) {
            return List.of();
        }

        // READY (or any other non-FAILED public status)
        return candleEngine.getAllCandles(symbolId, tf).stream()
                .filter(candle -> from == null || candle.time() >= from)
                .filter(candle -> to == null || candle.time() <= to)
                .map(CandleDto::from)
                .toList();
    }
}
