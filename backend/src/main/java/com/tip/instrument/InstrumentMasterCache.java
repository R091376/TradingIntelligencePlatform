package com.tip.instrument;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tip.config.InstrumentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

/**
 * Downloads, caches, and indexes the Upstox NSE instrument master.
 * <p>
 * Retains only {@code NSE_EQ} rows with type {@code EQ}/{@code BE} and {@code NSE_INDEX}.
 * FO and other segments are dropped after parse.
 */
@Component
public class InstrumentMasterCache {

    private static final Logger log = LoggerFactory.getLogger(InstrumentMasterCache.class);

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String CACHE_FILE_NAME = "NSE.json.gz";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(60);

    private static final String SEGMENT_NSE_EQ = "NSE_EQ";
    private static final String SEGMENT_NSE_INDEX = "NSE_INDEX";
    private static final String TYPE_EQ = "EQ";
    private static final String TYPE_BE = "BE";

    private final InstrumentProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private final Object lock = new Object();
    private final AtomicBoolean loaded = new AtomicBoolean(false);

    private volatile Map<String, ResolvedInstrument> byInstrumentKey = Map.of();
    private volatile Map<String, ResolvedInstrument> eqByTradingSymbol = Map.of();
    private volatile Map<String, ResolvedInstrument> indexByTradingSymbol = Map.of();
    private volatile Map<String, ResolvedInstrument> indexByName = Map.of();

    @Autowired
    public InstrumentMasterCache(InstrumentProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    /** Package-visible for tests that inject a custom {@link HttpClient}. */
    InstrumentMasterCache(InstrumentProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    /**
     * Best-effort load of the instrument master. Never throws on CDN/IO failure —
     * leaves indexes empty so seed paths can fall back to {@code seed-instrument-keys}.
     */
    public void ensureLoaded() {
        if (loaded.get()) {
            return;
        }
        synchronized (lock) {
            if (loaded.get()) {
                return;
            }
            try {
                Path cacheFile = cacheFilePath();
                boolean needDownload = properties.refreshOnStartup()
                        || !Files.isRegularFile(cacheFile)
                        || isStale(cacheFile);

                if (needDownload) {
                    downloadBestEffort(cacheFile);
                }

                if (Files.isRegularFile(cacheFile)) {
                    try {
                        loadFromGzipFile(cacheFile);
                        log.info("Instrument master loaded: eq={}, index={}, keys={}",
                                eqByTradingSymbol.size(),
                                indexByTradingSymbol.size(),
                                byInstrumentKey.size());
                    } catch (Exception e) {
                        log.error("Failed to parse instrument master cache at {}: {}",
                                cacheFile, e.toString());
                        clearIndexes();
                    }
                } else {
                    log.warn("Instrument master unavailable (no cache file at {}); indexes empty",
                            cacheFile);
                    clearIndexes();
                }
            } catch (Exception e) {
                log.error("Instrument master ensureLoaded failed (continuing with empty cache): {}",
                        e.toString());
                clearIndexes();
            } finally {
                loaded.set(true);
            }
        }
    }

    /**
     * Resolve a user trading-symbol string against in-memory indexes.
     * <ol>
     *   <li>NSE_EQ by trading_symbol (prefer EQ over BE)</li>
     *   <li>NSE_INDEX by trading_symbol</li>
     *   <li>NSE_INDEX by name</li>
     * </ol>
     *
     * @throws InstrumentNotFoundException if no match
     */
    public ResolvedInstrument resolve(String tradingSymbol) {
        ensureLoaded();
        String normalized = normalize(tradingSymbol);
        if (normalized.isEmpty()) {
            throw new InstrumentNotFoundException(tradingSymbol == null ? "" : tradingSymbol);
        }

        ResolvedInstrument eq = eqByTradingSymbol.get(normalized);
        if (eq != null) {
            return eq;
        }
        ResolvedInstrument byTs = indexByTradingSymbol.get(normalized);
        if (byTs != null) {
            return byTs;
        }
        ResolvedInstrument byName = indexByName.get(normalized);
        if (byName != null) {
            return byName;
        }
        throw new InstrumentNotFoundException(tradingSymbol);
    }

    /**
     * Lookup by Upstox {@code instrument_key} (e.g. {@code NSE_EQ|INE002A01018}).
     *
     * @return empty if key is blank or not in the master
     */
    public Optional<ResolvedInstrument> findByInstrumentKey(String instrumentKey) {
        ensureLoaded();
        if (instrumentKey == null || instrumentKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byInstrumentKey.get(instrumentKey.trim()));
    }

    /**
     * Typeahead search over indexed EQ + INDEX instruments.
     * <p>
     * Ranking (best first): exact trading symbol → trading-symbol prefix → trading-symbol
     * contains → display-name prefix → display-name contains. {@code BE} rows are skipped
     * (EQ preferred). Query length under 2 returns empty. {@code limit} is clamped to 1..50.
     */
    public List<ResolvedInstrument> search(String query, int limit) {
        ensureLoaded();
        String q = normalize(query);
        if (q.length() < 2) {
            return List.of();
        }
        int lim = Math.min(50, Math.max(1, limit));

        List<ScoredInstrument> scored = new ArrayList<>();
        for (ResolvedInstrument instrument : byInstrumentKey.values()) {
            if (TYPE_BE.equals(instrument.instrumentType())) {
                continue;
            }
            int score = scoreMatch(q, instrument);
            if (score >= 0) {
                scored.add(new ScoredInstrument(instrument, score));
            }
        }

        scored.sort(Comparator
                .comparingInt(ScoredInstrument::score)
                .thenComparing(s -> s.instrument().tradingSymbol(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(s -> s.instrument().instrumentKey()));

        List<ResolvedInstrument> out = new ArrayList<>(Math.min(lim, scored.size()));
        for (int i = 0; i < scored.size() && out.size() < lim; i++) {
            out.add(scored.get(i).instrument());
        }
        return List.copyOf(out);
    }

    /**
     * @return match rank (lower better), or {@code -1} if no match
     */
    static int scoreMatch(String normalizedQuery, ResolvedInstrument instrument) {
        String ts = normalize(instrument.tradingSymbol());
        String dn = normalize(instrument.displayName());

        if (!ts.isEmpty()) {
            if (ts.equals(normalizedQuery)) {
                return 0;
            }
            if (ts.startsWith(normalizedQuery)) {
                return 1;
            }
            if (ts.contains(normalizedQuery)) {
                return 2;
            }
        }
        if (!dn.isEmpty()) {
            if (dn.equals(normalizedQuery)) {
                return 3;
            }
            if (dn.startsWith(normalizedQuery)) {
                return 4;
            }
            if (dn.contains(normalizedQuery)) {
                return 5;
            }
        }
        return -1;
    }

    /** Whether {@link #ensureLoaded()} has completed (successfully or with empty indexes). */
    public boolean isLoaded() {
        return loaded.get();
    }

    public int size() {
        return byInstrumentKey.size();
    }

    private record ScoredInstrument(ResolvedInstrument instrument, int score) {
    }

    /**
     * Load indexes from a plain JSON array file (test fixture) or a {@code .gz} file.
     * Marks the cache as loaded.
     */
    public void loadFromFile(Path path) throws IOException {
        synchronized (lock) {
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".gz")) {
                loadFromGzipFile(path);
            } else {
                try (InputStream in = Files.newInputStream(path)) {
                    parseAndIndex(in);
                }
            }
            loaded.set(true);
        }
    }

    private void loadFromGzipFile(Path path) throws IOException {
        try (InputStream fileIn = Files.newInputStream(path);
             InputStream gzipIn = new GZIPInputStream(fileIn)) {
            parseAndIndex(gzipIn);
        }
    }

    private void parseAndIndex(InputStream in) throws IOException {
        JsonNode root = objectMapper.readTree(in);
        if (root == null || !root.isArray()) {
            throw new IOException("Instrument master root must be a JSON array");
        }

        Map<String, ResolvedInstrument> byKey = new HashMap<>();
        Map<String, ResolvedInstrument> eqByTs = new HashMap<>();
        Map<String, ResolvedInstrument> indexByTs = new HashMap<>();
        Map<String, ResolvedInstrument> indexByNm = new HashMap<>();

        for (JsonNode node : root) {
            if (node == null || !node.isObject()) {
                continue;
            }
            String segment = text(node, "segment");
            if (SEGMENT_NSE_EQ.equals(segment)) {
                indexEquity(node, byKey, eqByTs);
            } else if (SEGMENT_NSE_INDEX.equals(segment)) {
                indexIndex(node, byKey, indexByTs, indexByNm);
            }
            // NSE_FO and all other segments intentionally dropped
        }

        byInstrumentKey = Map.copyOf(byKey);
        eqByTradingSymbol = Map.copyOf(eqByTs);
        indexByTradingSymbol = Map.copyOf(indexByTs);
        indexByName = Map.copyOf(indexByNm);
    }

    private static void indexEquity(
            JsonNode node,
            Map<String, ResolvedInstrument> byKey,
            Map<String, ResolvedInstrument> eqByTs
    ) {
        String instrumentType = text(node, "instrument_type");
        if (!TYPE_EQ.equals(instrumentType) && !TYPE_BE.equals(instrumentType)) {
            return;
        }
        String instrumentKey = text(node, "instrument_key");
        String tradingSymbol = text(node, "trading_symbol");
        if (instrumentKey == null || tradingSymbol == null) {
            return;
        }

        ResolvedInstrument resolved = new ResolvedInstrument(
                instrumentKey,
                tradingSymbol,
                text(node, "exchange"),
                SEGMENT_NSE_EQ,
                instrumentType,
                displayName(node, tradingSymbol),
                text(node, "isin")
        );

        byKey.put(instrumentKey, resolved);

        String tsKey = normalize(tradingSymbol);
        ResolvedInstrument existing = eqByTs.get(tsKey);
        if (preferIncomingEq(existing, instrumentType)) {
            eqByTs.put(tsKey, resolved);
        }
    }

    private static void indexIndex(
            JsonNode node,
            Map<String, ResolvedInstrument> byKey,
            Map<String, ResolvedInstrument> indexByTs,
            Map<String, ResolvedInstrument> indexByNm
    ) {
        String instrumentKey = text(node, "instrument_key");
        if (instrumentKey == null) {
            return;
        }
        String tradingSymbol = text(node, "trading_symbol");
        String name = text(node, "name");
        String instrumentType = text(node, "instrument_type");
        if (instrumentType == null) {
            instrumentType = "INDEX";
        }

        ResolvedInstrument resolved = new ResolvedInstrument(
                instrumentKey,
                tradingSymbol != null ? tradingSymbol : (name != null ? name : instrumentKey),
                text(node, "exchange"),
                SEGMENT_NSE_INDEX,
                instrumentType,
                displayName(node, tradingSymbol != null ? tradingSymbol : name),
                text(node, "isin")
        );

        byKey.put(instrumentKey, resolved);
        if (tradingSymbol != null) {
            indexByTs.putIfAbsent(normalize(tradingSymbol), resolved);
        }
        if (name != null) {
            indexByNm.putIfAbsent(normalize(name), resolved);
        }
    }

    private void downloadBestEffort(Path cacheFile) {
        String url = properties.masterUrl();
        if (url == null || url.isBlank()) {
            log.warn("tip.instruments.master-url is blank; skipping download");
            return;
        }
        Path temp = null;
        try {
            Path parent = cacheFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            temp = cacheFile.resolveSibling(CACHE_FILE_NAME + ".tmp");
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(HTTP_TIMEOUT)
                    .GET()
                    .header("Accept-Encoding", "identity")
                    .build();
            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                log.warn("Instrument master download HTTP {} from {}", status, url);
                try (InputStream ignored = response.body()) {
                    // drain
                }
                Files.deleteIfExists(temp);
                return;
            }
            try (InputStream body = response.body()) {
                Files.copy(body, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            // Validate gzip + JSON array before replacing a previously good cache.
            if (!isValidGzipJsonArray(temp)) {
                log.warn("Downloaded instrument master failed gzip/JSON validation from {}; "
                        + "keeping previous cache if present", url);
                Files.deleteIfExists(temp);
                return;
            }
            try {
                Files.move(temp, cacheFile,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(temp, cacheFile, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = null; // successfully promoted
            log.info("Instrument master downloaded to {}", cacheFile);
        } catch (Exception e) {
            log.warn("Instrument master download failed from {}: {}", url, e.toString());
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            }
        }
    }

    /**
     * Cheap structural check: file is gzip-compressed and the decompressed content
     * begins with a JSON array. Avoids promoting HTTP 200 garbage over a prior good cache.
     */
    private boolean isValidGzipJsonArray(Path path) {
        try (InputStream fileIn = Files.newInputStream(path);
             InputStream gzipIn = new GZIPInputStream(fileIn);
             JsonParser parser = objectMapper.getFactory().createParser(gzipIn)) {
            JsonToken token = parser.nextToken();
            return token == JsonToken.START_ARRAY;
        } catch (Exception e) {
            log.debug("Instrument master validation failed for {}: {}", path, e.toString());
            return false;
        }
    }

    private Path cacheFilePath() {
        String dir = properties.cacheDir();
        if (dir == null || dir.isBlank()) {
            dir = System.getProperty("java.io.tmpdir") + "/tip-instruments";
        }
        return Path.of(dir).resolve(CACHE_FILE_NAME);
    }

    private static boolean isStale(Path cacheFile) {
        try {
            Instant modified = Files.getLastModifiedTime(cacheFile).toInstant();
            LocalDate fileDay = modified.atZone(IST).toLocalDate();
            LocalDate todayIst = LocalDate.now(IST);
            return !fileDay.equals(todayIst);
        } catch (IOException e) {
            return true;
        }
    }

    private void clearIndexes() {
        byInstrumentKey = Map.of();
        eqByTradingSymbol = Map.of();
        indexByTradingSymbol = Map.of();
        indexByName = Map.of();
    }

    static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(trimmed.length());
        boolean prevSpace = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!prevSpace) {
                    sb.append(' ');
                    prevSpace = true;
                }
            } else {
                sb.append(c);
                prevSpace = false;
            }
        }
        return sb.toString().toUpperCase(Locale.ROOT);
    }

    private static String text(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        String v = child.asText(null);
        if (v == null) {
            return null;
        }
        v = v.trim();
        return v.isEmpty() ? null : v;
    }

    private static String displayName(JsonNode node, String fallback) {
        String shortName = text(node, "short_name");
        if (shortName != null) {
            return shortName;
        }
        String name = text(node, "name");
        if (name != null) {
            return name;
        }
        return fallback != null ? fallback : "";
    }

    private static boolean preferIncomingEq(ResolvedInstrument existing, String incomingType) {
        if (existing == null) {
            return true;
        }
        // Prefer EQ over BE when both map to the same trading_symbol.
        return TYPE_EQ.equals(incomingType) && TYPE_BE.equals(existing.instrumentType());
    }
}
