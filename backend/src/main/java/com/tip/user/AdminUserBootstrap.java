package com.tip.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Ensures at least one ADMIN exists (from tip.users.admin-* config).
 */
@Component
@Order(50)
@Profile("!memory")
public class AdminUserBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserBootstrap.class);

    private final UserService userService;

    public AdminUserBootstrap(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            userService.ensureAdminSeeded();
        } catch (Exception e) {
            log.error("Failed to bootstrap admin user: {}", e.toString());
            throw e;
        }
    }
}
