package com.tip.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * When watchlist store is {@code memory}, disable JDBC/JPA/Flyway auto-configuration
 * so the app starts without PostgreSQL.
 * <p>
 * Enable with either:
 * <ul>
 *   <li>{@code TIP_WATCHLIST_STORE=memory} / {@code --tip.watchlist.store=memory}</li>
 *   <li>{@code --spring.profiles.active=memory} (also loads {@code application-memory.yml})</li>
 * </ul>
 * Profile {@code memory} already sets excludes in YAML; this processor covers the
 * property-only path and forces {@code tip.watchlist.store=memory} when the profile is active.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class WatchlistStoreEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String STORE_PROP = "tip.watchlist.store";
    private static final String EXCLUDE_PROP = "spring.autoconfigure.exclude";

    private static final String[] DB_EXCLUDES = {
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration",
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
            "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
            "org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration"
    };

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!isMemoryMode(environment)) {
            return;
        }

        Map<String, Object> props = new LinkedHashMap<>();
        props.put(STORE_PROP, "memory");
        props.put("spring.flyway.enabled", "false");
        props.put("spring.jpa.hibernate.ddl-auto", "none");

        // Merge excludes as a comma-separated list (Boot binds this to String[])
        String merged = mergeExcludes(environment.getProperty(EXCLUDE_PROP), DB_EXCLUDES);
        props.put(EXCLUDE_PROP, merged);

        environment.getPropertySources().addFirst(
                new MapPropertySource("tipWatchlistMemoryStore", props));
    }

    private static boolean isMemoryMode(ConfigurableEnvironment environment) {
        if (environment.acceptsProfiles(Profiles.of("memory"))) {
            return true;
        }
        String active = environment.getProperty("spring.profiles.active", "");
        if (Arrays.stream(active.split(","))
                .map(String::trim)
                .anyMatch(p -> p.equalsIgnoreCase("memory"))) {
            return true;
        }
        String store = environment.getProperty(STORE_PROP, "postgres");
        return "memory".equalsIgnoreCase(store.trim());
    }

    private static String mergeExcludes(String existing, String[] required) {
        StringBuilder sb = new StringBuilder();
        if (existing != null && !existing.isBlank()) {
            sb.append(existing.trim());
        }
        for (String ex : required) {
            if (existing != null && existing.contains(ex)) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(ex);
        }
        return sb.toString();
    }
}
