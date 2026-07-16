package com.tip.api;

import com.tip.api.dto.CashAmountRequest;
import com.tip.api.dto.CreateUserRequest;
import com.tip.api.dto.SetPasswordRequest;
import com.tip.api.dto.UpdateUserRequest;
import com.tip.api.dto.UserDto;
import com.tip.user.UserRole;
import com.tip.user.UserService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin-only user management: create users, passwords, seed cash, reset, disable trading.
 */
@RestController
@RequestMapping("/api/admin/users")
@Profile("!memory")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserDto> list() {
        return userService.listUsers().stream().map(UserDto::from).toList();
    }

    @GetMapping("/{id}")
    public UserDto get(@PathVariable("id") UUID id) {
        return UserDto.from(userService.requireById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@RequestBody CreateUserRequest body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body required");
        }
        UserRole role = body.role() != null ? body.role() : UserRole.USER;
        return UserDto.from(userService.createUser(
                body.username(),
                body.password(),
                role,
                body.displayName(),
                body.seedCash()
        ));
    }

    @PutMapping("/{id}")
    public UserDto update(@PathVariable("id") UUID id, @RequestBody UpdateUserRequest body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body required");
        }
        return UserDto.from(userService.updateUser(
                id,
                body.displayName(),
                body.role(),
                body.tradingEnabled(),
                body.active()
        ));
    }

    @PostMapping("/{id}/password")
    public Map<String, String> setPassword(
            @PathVariable("id") UUID id,
            @RequestBody SetPasswordRequest body
    ) {
        if (body == null || body.password() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password required");
        }
        userService.setPassword(id, body.password());
        return Map.of("status", "ok");
    }

    @PostMapping("/{id}/seed-cash")
    public UserDto seedCash(@PathVariable("id") UUID id, @RequestBody CashAmountRequest body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount required");
        }
        return UserDto.from(userService.seedCash(id, body.amount()));
    }

    @PostMapping("/{id}/reset")
    public UserDto reset(@PathVariable("id") UUID id, @RequestBody(required = false) CashAmountRequest body) {
        return UserDto.from(userService.resetAccount(id, body != null ? body.amount() : null));
    }

    @PostMapping("/{id}/trading")
    public UserDto setTrading(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, Boolean> body
    ) {
        if (body == null || !body.containsKey("enabled")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "enabled required");
        }
        return UserDto.from(userService.setTradingEnabled(id, Boolean.TRUE.equals(body.get("enabled"))));
    }
}
