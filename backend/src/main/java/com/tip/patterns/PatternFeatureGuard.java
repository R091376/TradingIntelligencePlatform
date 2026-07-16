package com.tip.patterns;

import com.tip.config.PatternProperties;
import com.tip.journal.PatternJournal;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Patterns run only when enabled and journal can persist (Postgres store).
 */
@Component
public class PatternFeatureGuard {

    private static final Logger log = LoggerFactory.getLogger(PatternFeatureGuard.class);

    private final PatternProperties patternProperties;
    private final PatternJournal patternJournal;
    private final String watchlistStore;

    public PatternFeatureGuard(
            PatternProperties patternProperties,
            PatternJournal patternJournal,
            @Value("${tip.watchlist.store:postgres}") String watchlistStore
    ) {
        this.patternProperties = patternProperties;
        this.patternJournal = patternJournal;
        this.watchlistStore = watchlistStore;
    }

    @PostConstruct
    void logEnablement() {
        boolean full = isFullyEnabled();
        log.info(
                "Pattern pipeline fullyEnabled={} (pattern.enabled={}, journal.active={}, store={})",
                full,
                patternProperties.isEnabled(),
                patternJournal.isActive(),
                watchlistStore
        );
    }

    public boolean isFullyEnabled() {
        return patternProperties.isEnabled()
                && patternJournal.isActive()
                && "postgres".equalsIgnoreCase(watchlistStore);
    }

    public boolean isEnabledFlag() {
        return patternProperties.isEnabled();
    }
}
