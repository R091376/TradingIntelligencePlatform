package com.tip.instrument;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tip.config.InstrumentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstrumentMasterCacheTest {

    private static final Path FIXTURE = Paths.get("src/test/resources/instruments/nse-seed-fixture.json");

    @TempDir
    Path tempDir;

    private InstrumentMasterCache cache;

    @BeforeEach
    void setUp() throws Exception {
        InstrumentProperties props = new InstrumentProperties(
                "https://example.invalid/NSE.json.gz",
                tempDir.toString(),
                false
        );
        cache = new InstrumentMasterCache(props, new ObjectMapper());
        assertTrue(Files.isRegularFile(FIXTURE), "fixture missing: " + FIXTURE.toAbsolutePath());
        cache.loadFromFile(FIXTURE);
    }

    @Test
    void resolve_nifty50_caseAndWhitespaceVariants() {
        for (String input : new String[]{
                "Nifty 50",
                "NIFTY 50",
                "nifty 50",
                "  Nifty   50  ",
                "\tnifty  50\n"
        }) {
            ResolvedInstrument resolved = cache.resolve(input);
            assertEquals("NSE_INDEX|Nifty 50", resolved.instrumentKey(), "input=" + input);
            assertEquals("NSE_INDEX", resolved.segment());
            assertEquals("INDEX", resolved.instrumentType());
        }
    }

    @Test
    void resolve_nifty_tradingSymbol() {
        // BOD trading_symbol for Nifty 50 index is "NIFTY"
        ResolvedInstrument resolved = cache.resolve("NIFTY");
        assertEquals("NSE_INDEX|Nifty 50", resolved.instrumentKey());
    }

    @Test
    void resolve_reliance_caseAndWhitespace() {
        for (String input : new String[]{"RELIANCE", "reliance", "  Reliance  "}) {
            ResolvedInstrument resolved = cache.resolve(input);
            assertEquals("NSE_EQ|INE002A01018", resolved.instrumentKey(), "input=" + input);
            assertEquals("RELIANCE", resolved.tradingSymbol());
            assertEquals("NSE_EQ", resolved.segment());
            assertEquals("EQ", resolved.instrumentType());
            assertEquals("INE002A01018", resolved.isin());
        }
    }

    @Test
    void resolve_seedEquities() {
        assertEquals("NSE_EQ|INE467B01029", cache.resolve("TCS").instrumentKey());
        assertEquals("NSE_EQ|INE040A01034", cache.resolve("HDFCBANK").instrumentKey());
        assertEquals("NSE_EQ|INE009A01021", cache.resolve("INFY").instrumentKey());
        assertEquals("NSE_EQ|INE090A01021", cache.resolve("ICICIBANK").instrumentKey());
        assertEquals("NSE_EQ|INE030A01027", cache.resolve("HINDUNILVR").instrumentKey());
        assertEquals("NSE_EQ|INE154A01025", cache.resolve("ITC").instrumentKey());
        assertEquals("NSE_EQ|INE062A01020", cache.resolve("SBIN").instrumentKey());
        assertEquals("NSE_EQ|INE397D01024", cache.resolve("BHARTIARTL").instrumentKey());
    }

    @Test
    void resolve_prefersEqOverBe() {
        ResolvedInstrument reliance = cache.resolve("RELIANCE");
        assertEquals("EQ", reliance.instrumentType());
        assertEquals("NSE_EQ|INE002A01018", reliance.instrumentKey());
    }

    @Test
    void resolve_doesNotIndexFoRows() {
        // FO row uses trading_symbol "NIFTY 25JUL FUT" and name "Nifty 50" — FO must be dropped.
        // Nifty 50 still resolves via INDEX name, not FO.
        assertEquals("NSE_INDEX|Nifty 50", cache.resolve("Nifty 50").instrumentKey());
        assertThrows(InstrumentNotFoundException.class, () -> cache.resolve("NIFTY 25JUL FUT"));
    }

    @Test
    void resolve_unknownThrows() {
        InstrumentNotFoundException ex = assertThrows(
                InstrumentNotFoundException.class,
                () -> cache.resolve("NOT_A_REAL_SYMBOL_XYZ")
        );
        assertTrue(ex.getMessage().contains("NOT_A_REAL_SYMBOL_XYZ"));
        assertEquals("NOT_A_REAL_SYMBOL_XYZ", ex.getTradingSymbol());
    }

    @Test
    void ensureLoaded_cdnFailureLeavesEmptyCacheNoCrash() {
        Path emptyCache = tempDir.resolve("empty-cdn");
        // Connection refused on discard port — fails fast, no crash.
        InstrumentProperties props = new InstrumentProperties(
                "http://127.0.0.1:9/NSE.json.gz",
                emptyCache.toString(),
                true
        );
        InstrumentMasterCache failing = new InstrumentMasterCache(
                props,
                new ObjectMapper(),
                HttpClient.newHttpClient()
        );
        failing.ensureLoaded();
        assertTrue(failing.isLoaded());
        assertEquals(0, failing.size());
        assertThrows(InstrumentNotFoundException.class, () -> failing.resolve("RELIANCE"));
    }

    @Test
    void normalize_collapsesWhitespaceAndUppercases() {
        assertEquals("NIFTY 50", InstrumentMasterCache.normalize("  nifty   50  "));
        assertEquals("RELIANCE", InstrumentMasterCache.normalize("reliance"));
        assertEquals("", InstrumentMasterCache.normalize("   "));
        assertEquals("", InstrumentMasterCache.normalize(null));
    }
}
