package com.tip.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "tip.users")
public class UserProperties {

    /** Default paper cash (INR) for new users and full resets. */
    private BigDecimal defaultSeedCash = new BigDecimal("100000.00");

    private String adminUsername = "admin";

    /** Override via TIP_ADMIN_PASSWORD. Change after first login in production. */
    private String adminPassword = "admin";

    private String adminDisplayName = "Administrator";

    public BigDecimal getDefaultSeedCash() {
        return defaultSeedCash;
    }

    public void setDefaultSeedCash(BigDecimal defaultSeedCash) {
        this.defaultSeedCash = defaultSeedCash != null
                ? defaultSeedCash
                : new BigDecimal("100000.00");
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername != null && !adminUsername.isBlank()
                ? adminUsername.trim()
                : "admin";
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword != null ? adminPassword : "admin";
    }

    public String getAdminDisplayName() {
        return adminDisplayName;
    }

    public void setAdminDisplayName(String adminDisplayName) {
        this.adminDisplayName = adminDisplayName;
    }
}
