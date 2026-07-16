package com.tip.user;

import com.tip.config.UserProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Admin-managed users and paper cash. Paper orders come later; ledger is ready.
 * Disabled under {@code memory} profile (no JPA).
 */
@Service
@Profile("!memory")
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9._-]{3,64}$");
    private static final int MIN_PASSWORD = 4;
    private static final int MAX_PASSWORD = 128;

    private final AppUserRepository users;
    private final CashLedgerRepository ledger;
    private final PasswordEncoder passwordEncoder;
    private final UserProperties userProperties;

    public UserService(
            AppUserRepository users,
            CashLedgerRepository ledger,
            PasswordEncoder passwordEncoder,
            UserProperties userProperties
    ) {
        this.users = users;
        this.ledger = ledger;
        this.passwordEncoder = passwordEncoder;
        this.userProperties = userProperties;
    }

    @Transactional(readOnly = true)
    public List<AppUserEntity> listUsers() {
        return users.findAllByOrderByUsernameAsc();
    }

    @Transactional(readOnly = true)
    public AppUserEntity requireById(UUID id) {
        return users.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @Transactional(readOnly = true)
    public AppUserEntity requireByUsername(String username) {
        return users.findByUsernameIgnoreCase(username).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @Transactional
    public AppUserEntity createUser(
            String username,
            String password,
            UserRole role,
            String displayName,
            BigDecimal seedCash
    ) {
        String un = normalizeUsername(username);
        if (users.existsByUsernameIgnoreCase(un)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists: " + un);
        }
        validatePassword(password);
        UserRole r = role != null ? role : UserRole.USER;
        BigDecimal cash = money(seedCash != null ? seedCash : userProperties.getDefaultSeedCash());

        Instant now = Instant.now();
        AppUserEntity user = new AppUserEntity(
                UUID.randomUUID(),
                un,
                passwordEncoder.encode(password),
                r,
                blankToNull(displayName),
                cash,
                true,
                true,
                now,
                now
        );
        users.save(user);
        appendLedger(user.getId(), CashLedgerType.SEED, cash, cash, "Initial seed cash");
        log.info("Created user {} role={} cash={}", un, r, cash);
        return user;
    }

    @Transactional
    public AppUserEntity updateUser(
            UUID id,
            String displayName,
            UserRole role,
            Boolean tradingEnabled,
            Boolean active
    ) {
        AppUserEntity user = requireById(id);
        if (displayName != null) {
            user.setDisplayName(blankToNull(displayName));
        }
        if (role != null && role != user.getRole()) {
            if (user.getRole() == UserRole.ADMIN && role != UserRole.ADMIN) {
                ensureNotLastAdmin(user.getId());
            }
            user.setRole(role);
        }
        if (tradingEnabled != null) {
            user.setTradingEnabled(tradingEnabled);
        }
        if (active != null) {
            if (!active && user.getRole() == UserRole.ADMIN) {
                ensureNotLastAdmin(user.getId());
            }
            user.setActive(active);
        }
        user.setUpdatedAt(Instant.now());
        return users.save(user);
    }

    @Transactional
    public void setPassword(UUID id, String newPassword) {
        validatePassword(newPassword);
        AppUserEntity user = requireById(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(Instant.now());
        users.save(user);
        log.info("Password updated for user {}", user.getUsername());
    }

    /**
     * Set cash to default seed (or provided amount) and record RESET ledger entry.
     * Does not clear positions yet (paper trading later).
     */
    @Transactional
    public AppUserEntity resetAccount(UUID id, BigDecimal toAmount) {
        AppUserEntity user = requireById(id);
        BigDecimal target = money(toAmount != null ? toAmount : userProperties.getDefaultSeedCash());
        BigDecimal delta = target.subtract(user.getCashBalance());
        user.setCashBalance(target);
        user.setUpdatedAt(Instant.now());
        users.save(user);
        appendLedger(user.getId(), CashLedgerType.RESET, delta, target,
                "Admin reset cash to " + target);
        log.info("Reset cash for {} to {}", user.getUsername(), target);
        return user;
    }

    /** Top-up or set seed without wiping future positions semantics (cash only). */
    @Transactional
    public AppUserEntity seedCash(UUID id, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be positive");
        }
        AppUserEntity user = requireById(id);
        BigDecimal add = money(amount);
        BigDecimal next = user.getCashBalance().add(add);
        user.setCashBalance(next);
        user.setUpdatedAt(Instant.now());
        users.save(user);
        appendLedger(user.getId(), CashLedgerType.SEED, add, next, "Admin seed/top-up");
        log.info("Seeded {} with +{} (balance={})", user.getUsername(), add, next);
        return user;
    }

    @Transactional
    public AppUserEntity setTradingEnabled(UUID id, boolean enabled) {
        AppUserEntity user = requireById(id);
        user.setTradingEnabled(enabled);
        user.setUpdatedAt(Instant.now());
        return users.save(user);
    }

    @Transactional
    public void ensureAdminSeeded() {
        if (users.countByRole(UserRole.ADMIN) > 0) {
            return;
        }
        String un = userProperties.getAdminUsername();
        if (users.existsByUsernameIgnoreCase(un)) {
            AppUserEntity existing = users.findByUsernameIgnoreCase(un).orElseThrow();
            existing.setRole(UserRole.ADMIN);
            existing.setActive(true);
            existing.setUpdatedAt(Instant.now());
            users.save(existing);
            log.info("Promoted existing user {} to ADMIN", un);
            return;
        }
        createUser(
                un,
                userProperties.getAdminPassword(),
                UserRole.ADMIN,
                userProperties.getAdminDisplayName(),
                userProperties.getDefaultSeedCash()
        );
        log.info("Bootstrapped default ADMIN user '{}'", un);
    }

    private void ensureNotLastAdmin(UUID excludeId) {
        long admins = users.countByRole(UserRole.ADMIN);
        AppUserEntity self = users.findById(excludeId).orElse(null);
        if (self != null && self.getRole() == UserRole.ADMIN && admins <= 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Cannot demote or deactivate the last admin");
        }
    }

    private void appendLedger(
            UUID userId,
            CashLedgerType type,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String note
    ) {
        ledger.save(new CashLedgerEntity(
                UUID.randomUUID(),
                userId,
                type,
                amount,
                balanceAfter,
                note,
                Instant.now()
        ));
    }

    private static String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username is required");
        }
        String un = username.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(un).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "username must be 3–64 chars: letters, digits, . _ -");
        }
        return un;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "password must be " + MIN_PASSWORD + "–" + MAX_PASSWORD + " characters");
        }
    }

    private static BigDecimal money(BigDecimal v) {
        if (v == null || v.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cash amount must be >= 0");
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
