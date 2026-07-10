package com.tip.api;

import com.tip.config.MarketProperties;
import com.tip.market.BootstrapStatus;
import com.tip.market.CandleEngine;
import com.tip.market.MarketStatusService;
import com.tip.market.model.Candle;
import com.tip.watchlist.SymbolBootstrapStatus;
import com.tip.watchlist.WatchlistEntry;
import com.tip.watchlist.WatchlistRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketController.class)
class MarketControllerTest {

    private static final String NIFTY_KEY = "NSE_INDEX|Nifty 50";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CandleEngine candleEngine;

    @MockBean
    private MarketProperties marketProperties;

    @MockBean
    private MarketStatusService marketStatusService;

    @MockBean
    private WatchlistRepository watchlistRepository;

    @Test
    void getSymbolReturnsPrimaryWhenPresent() throws Exception {
        when(watchlistRepository.findPrimary()).thenReturn(Optional.of(primaryEntry()));
        when(marketProperties.defaultTimeframe()).thenReturn("5m");

        mockMvc.perform(get("/api/market/symbol"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("Nifty 50"))
                .andExpect(jsonPath("$.instrumentKey").value(NIFTY_KEY))
                .andExpect(jsonPath("$.timeframe").value("5m"));
    }

    @Test
    void getSymbolFallsBackToConfigDefault() throws Exception {
        when(watchlistRepository.findPrimary()).thenReturn(Optional.empty());
        when(marketProperties.defaultSymbol()).thenReturn("Nifty 50");
        when(marketProperties.defaultInstrumentKey()).thenReturn(NIFTY_KEY);
        when(marketProperties.defaultTimeframe()).thenReturn("5m");

        mockMvc.perform(get("/api/market/symbol"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("Nifty 50"))
                .andExpect(jsonPath("$.instrumentKey").value(NIFTY_KEY))
                .andExpect(jsonPath("$.timeframe").value("5m"));
    }

    @Test
    void getCandlesReturnsPrimaryCandles() throws Exception {
        when(marketStatusService.getBootstrapStatus()).thenReturn(BootstrapStatus.READY);
        when(watchlistRepository.findPrimary()).thenReturn(Optional.of(primaryEntry()));
        when(marketProperties.defaultTimeframe()).thenReturn("5m");
        when(candleEngine.getAllCandles(NIFTY_KEY, "5m")).thenReturn(List.of(
                new Candle(1000L, 10.0, 11.0, 9.5, 10.5, 100L)
        ));

        mockMvc.perform(get("/api/market/candles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].time").value(1000))
                .andExpect(jsonPath("$[0].close").value(10.5))
                .andExpect(jsonPath("$[0].volume").value(100));
    }

    @Test
    void getCandlesFallsBackToDefaultInstrumentKey() throws Exception {
        when(marketStatusService.getBootstrapStatus()).thenReturn(BootstrapStatus.READY);
        when(watchlistRepository.findPrimary()).thenReturn(Optional.empty());
        when(marketProperties.defaultInstrumentKey()).thenReturn(NIFTY_KEY);
        when(marketProperties.defaultTimeframe()).thenReturn("5m");
        when(candleEngine.getAllCandles(NIFTY_KEY, "5m")).thenReturn(List.of(
                new Candle(2000L, 1.0, 1.0, 1.0, 1.0, 0L)
        ));

        mockMvc.perform(get("/api/market/candles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].time").value(2000));
    }

    private static WatchlistEntry primaryEntry() {
        return new WatchlistEntry(
                NIFTY_KEY,
                "Nifty 50",
                "NSE",
                "NSE_INDEX",
                "INDEX",
                "Nifty 50",
                Instant.parse("2026-07-10T00:00:00Z"),
                true,
                SymbolBootstrapStatus.READY,
                null
        );
    }
}
