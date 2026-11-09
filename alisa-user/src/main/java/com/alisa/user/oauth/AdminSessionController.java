package com.alisa.user.oauth;

import com.alisa.user.mapper.UserMapper;
import com.alisa.user.service.AccessTokenService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.security.Principal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/oauth/admin")
@ConditionalOnProperty(prefix = "alisa.oauth", name = "enabled", havingValue = "true")
public class AdminSessionController {
    private final UserMapper users;
    private final AccessTokenService tokens;

    public AdminSessionController(UserMapper users, AccessTokenService tokens) {
        this.users = users;
        this.tokens = tokens;
    }

    public record SessionChallenge(String header, String csrf) {}
    public record AdminSession(String username, String token) {}

    @GetMapping("/session")
    public ResponseEntity<SessionChallenge> challenge(CsrfToken csrf) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new SessionChallenge(csrf.getHeaderName(), csrf.getToken()));
    }

    @PostMapping("/session")
    public ResponseEntity<AdminSession> session(Principal principal) {
        var user = users.findUserByID(principal.getName());
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new AdminSession(user.getUsername(), tokens.issue(user)));
    }

    @GetMapping("/continue")
    public ResponseEntity<Void> continueToAdmin(HttpServletRequest request) {
        String context = request.getContextPath();
        if (!context.equals("/admin/auth")) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return ResponseEntity.status(HttpStatus.SEE_OTHER).cacheControl(CacheControl.noStore())
                .location(URI.create("/admin/")).build();
    }
}
