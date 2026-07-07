package com.tip.api;

import com.tip.api.dto.CandleDto;
import com.tip.api.dto.SymbolInfoResponse;
import com.tip.api.dto.MarketStatusResponse;
import com.tip.config.MarketProperties;
import com.tip.market.BootstrapStatus;
import com.tip.market.CandleEngine;
import com.tip.market.MarketStatusService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final CandleEngine candleEngine;
    private final MarketProperties marketProperties;
    private final MarketStatusService marketStatusService;

    public MarketController(
            CandleEngine candleEngine,
            MarketProperties marketProperties,
            MarketStatusService marketStatusService
    ) {
        this.candleEngine = candleEngine;
        this.marketProperties = marketProperties;
        this.marketStatusService = marketStatusService;
    }

    @GetMapping("/symbol")
    public SymbolInfoResponse getSymbol() {
        return new SymbolInfoResponse(
                marketProperties.defaultSymbol(),
                marketProperties.defaultInstrumentKey(),
                marketProperties.defaultTimeframe()
        );
    }

    @GetMapping("/status")
    public MarketStatusResponse getStatus() {
        return new MarketStatusResponse(
                marketStatusService.getMarketPhase(),
                marketStatusService.getBootstrapStatus(),
                marketStatusService.getBootstrapError(),
                marketStatusService.getLastSeededAt() != null
                        ? marketStatusService.getLastSeededAt().toString()
                        : null,
                marketStatusService.isLiveFeedConnected(),
                marketStatusService.getSeededCandleCount()
        );
    }

    @GetMapping("/candles")
    public List<CandleDto> getCandles(
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to
    ) {
        if (marketStatusService.getBootstrapStatus() == BootstrapStatus.FAILED) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    marketStatusService.getBootstrapError()
            );
        }

        String instrumentKey = marketProperties.defaultInstrumentKey();
        String timeframe = marketProperties.defaultTimeframe();

        return candleEngine.getAllCandles(instrumentKey, timeframe).stream()
                .filter(candle -> from == null || candle.time() >= from)
                .filter(candle -> to == null || candle.time() <= to)
                .map(CandleDto::from)
                .toList();
    }
}