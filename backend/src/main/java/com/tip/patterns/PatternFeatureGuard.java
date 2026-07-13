package com.tip.patterns;

import com.tip.config.PatternProperties;
import com.tip.journal.PatternJournal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Patterns run only when enabled and journal can persist (Postgres store).
 */
@Component
public class PatternFeatureGuard {

    private final PatternProperties patternProperties;
    private final PatternJournal patternJournal;
    private final String watchlistStore;

    public PatternFeatureGuard(
            PatternProperties patternProperties,
            PatternJournal patternJournal,
            @Value("${tip.watchlist.store:memory}") String watchlistStore
    ) {
        this.patternProperties = patternProperties;
        this.patternJournal = patternJournal;
        this.watchlistStore = watchlistStore;
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
