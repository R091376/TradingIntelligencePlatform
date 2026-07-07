package com.tip.api;

import com.tip.config.MarketProperties;
import com.tip.market.BootstrapStatus;
import com.tip.market.CandleEngine;
import com.tip.market.MarketStatusService;
import com.tip.market.model.Candle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketController.class)
@TestPropertySource(properties = {
        "tip.market.default-symbol=RELIANCE",
        "tip.market.default-instrument-key=NSE_EQ|INE002A01018",
        "tip.market.default-timeframe=5m"
})
class MarketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CandleEngine candleEngine;

    @MockBean
    private MarketProperties marketProperties;

    @MockBean
    private MarketStatusService marketStatusService;

    @Test
    void getSymbolReturnsConfiguredMetadata() throws Exception {
        when(marketProperties.defaultSymbol()).thenReturn("RELIANCE");
        when(marketProperties.defaultInstrumentKey()).thenReturn("NSE_EQ|INE002A01018");
        when(marketProperties.defaultTimeframe()).thenReturn("5m");

        mockMvc.perform(get("/api/market/symbol"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("RELIANCE"))
                .andExpect(jsonPath("$.instrumentKey").value("NSE_EQ|INE002A01018"))
                .andExpect(jsonPath("$.timeframe").value("5m"));
    }

    @Test
    void getCandlesReturnsSeededCandles() throws Exception {
        when(marketStatusService.getBootstrapStatus()).thenReturn(BootstrapStatus.READY);
        when(marketProperties.defaultInstrumentKey()).thenReturn("NSE_EQ|INE002A01018");
        when(marketProperties.defaultTimeframe()).thenReturn("5m");
        when(candleEngine.getAllCandles("NSE_EQ|INE002A01018", "5m")).thenReturn(List.of(
                new Candle(1000L, 10.0, 11.0, 9.5, 10.5, 100L)
        ));

        mockMvc.perform(get("/api/market/candles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].time").value(1000))
                .andExpect(jsonPath("$[0].close").value(10.5))
                .andExpect(jsonPath("$[0].volume").value(100));
    }
}