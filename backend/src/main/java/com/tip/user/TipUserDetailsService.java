package com.tip.user;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Profile("!memory")
public class TipUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public TipUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUserEntity entity = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        if (!entity.isActive()) {
            throw new UsernameNotFoundException("User disabled: " + username);
        }
        return User.builder()
                .username(entity.getUsername())
                .password(entity.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority(entity.getRole().springRole())))
                .disabled(!entity.isActive())
                .accountLocked(false)
                .build();
    }
}
