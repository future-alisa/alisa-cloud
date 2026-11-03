package com.alisa.user.provider;

import java.util.Set;
import java.util.Locale;
import com.alisa.identity.*;
import com.alisa.user.entity.User;
import com.alisa.user.mapper.UserMapper;
import com.alisa.user.service.PasswordService;
import org.springframework.stereotype.Component;

@Component
public final class LocalIdentityProvider implements IdentityProvider {
    public static final String ID = "local";
    public static final String ISSUER = "urn:alisa:local";
    private final UserMapper users;
    private final PasswordService passwords;
    public LocalIdentityProvider(UserMapper users, PasswordService passwords) {
        this.users = users;
        this.passwords = passwords;
    }
    @Override public String id() { return ID; }
    @Override public Set<String> methods() { return Set.of("password"); }

    @Override
    public VerifiedIdentity authenticate(IdentityCredentials credentials, IdentityAuthenticationContext context) {
        if (!"password".equals(credentials.method())) throw new AuthenticationRejectedException();
        String username = credentials.values().getOrDefault("username", "").trim();
        String email = credentials.values().getOrDefault("email", "").trim().toLowerCase(Locale.ROOT);
        User user = username.isEmpty() ? users.findUserByEmail(email) : users.findUserByName(username);
        String password = credentials.values().get("password");
        if (user == null || !passwords.matches(password, user.getPassword())) throw new AuthenticationRejectedException();
        if (!passwords.isEncoded(user.getPassword())) {
            int updated = users.updatePasswordIfMatches(user.getId(), user.getPassword(), passwords.encode(password));
            if (updated != 1) throw new AuthenticationRejectedException();
        }
        return new VerifiedIdentity(ISSUER, user.getId());
    }
}
