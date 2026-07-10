package com.tip.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class WatchlistPropertiesBindingTest {

    @Autowired
    private WatchlistProperties watchlistProperties;

    @Autowired
    private InstrumentProperties instrumentProperties;

    @Autowired
    private MarketProperties marketProperties;

    @Test
    void watchlistProperties_bindSeedListAndCaps() {
        assertNotNull(watchlistProperties);
        assertEquals(10, watchlistProperties.seedSymbols().size());
        assertEquals("Nifty 50", watchlistProperties.seedSymbols().get(0));
        assertEquals("RELIANCE", watchlistProperties.seedSymbols().get(1));
        assertEquals("BHARTIARTL", watchlistProperties.seedSymbols().get(9));
        assertEquals("NSE_INDEX|Nifty 50",
                watchlistProperties.seedInstrumentKeys().get("Nifty 50"));
        assertEquals(40, watchlistProperties.softWarnSize());
        assertEquals(50, watchlistProperties.hardMaxSize());
    }

    @Test
    void instrumentProperties_bindMasterSettings() {
        assertNotNull(instrumentProperties);
        assertTrue(instrumentProperties.masterUrl().contains("NSE.json.gz"));
        assertNotNull(instrumentProperties.cacheDir());
        assertFalse(instrumentProperties.cacheDir().isBlank());
        assertTrue(instrumentProperties.refreshOnStartup());
    }

    @Test
    void marketDefaults_matchNifty50IndexSeed() {
        assertEquals("Nifty 50", marketProperties.defaultSymbol());
        assertEquals("NSE_INDEX|Nifty 50", marketProperties.defaultInstrumentKey());
    }
}
