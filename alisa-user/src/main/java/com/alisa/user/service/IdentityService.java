package com.alisa.user.service;

import java.util.Locale;
import java.util.Map;
import com.alisa.identity.IdentityCredentials;
import com.alisa.identity.AuthenticationRejectedException;
import com.alisa.user.entity.User;
import com.alisa.user.mapper.UserMapper;
import com.alisa.user.dto.ChangePasswordRequest;
import com.alisa.user.dto.user.UserDto;
import com.alisa.user.vo.UserVo;
import com.alisa.util.UUIDTool;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IdentityService {
    private final UserMapper users;
    private final PasswordService passwords;
    private final AccessTokenService tokens;
    private final VerificationCodeService codes;
    private final ProviderLoginService login;
    public IdentityService(UserMapper users, PasswordService passwords, AccessTokenService tokens, VerificationCodeService codes, ProviderLoginService login) {
        this.users = users; this.passwords = passwords; this.tokens = tokens; this.codes = codes;
        this.login = login;
    }
    private String email(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    public String signin(UserDto request) {
        try {
            return login.signin("local", new IdentityCredentials("password", Map.of(
                    "username", request.getUsername() == null ? "" : request.getUsername(),
                    "email", request.getEmail() == null ? "" : request.getEmail(),
                    "password", request.getPassword() == null ? "" : request.getPassword())));
        } catch (AuthenticationRejectedException exception) {
            return "";
        }
    }
    public boolean signup(UserDto request) {
        if (!StringUtils.hasText(request.getUsername()) || request.getUsername().trim().length() > 45)
            throw new IllegalArgumentException("用户名不能为空且不能超过 45 个字符");
        String email = email(request.getEmail());
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || email.length() > 255)
            throw new IllegalArgumentException("邮箱格式无效");
        if (users.findUserByName(request.getUsername().trim()) != null || users.findUserByEmail(email) != null) return false;
        User user = new User();
        user.setId(UUIDTool.getUUID()); user.setUsername(request.getUsername().trim());
        user.setEmail(email); user.setPassword(passwords.encode(request.getPassword()));
        return users.saveUser(user);
    }
    public void sendResetCode(String address) {
        // The response never reveals whether an account exists.
        String email = email(address);
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || email.length() > 255)
            throw new IllegalArgumentException("邮箱格式无效");
        if (users.findUserByEmail(email) != null) codes.send(email);
    }
    public boolean changePassword(ChangePasswordRequest request) {
        if (StringUtils.hasText(request.getCode())) return resetPassword(request);
        if (!StringUtils.hasText(request.getUsername())) return false;
        User user = users.findUserByName(request.getUsername().trim());
        if (user == null || !passwords.matches(request.getCurrentPassword(), user.getPassword())) return false;
        return users.updatePasswordIfMatches(user.getId(), user.getPassword(), passwords.encode(request.getNewPassword())) == 1;
    }
    public boolean resetPassword(ChangePasswordRequest request) {
        String encoded = passwords.encode(request.getNewPassword());
        String email = email(request.getEmail());
        User user = users.findUserByEmail(email);
        if (user == null || !codes.consume(email, request.getCode())) return false;
        return users.updatePasswordIfMatches(user.getId(), user.getPassword(), encoded) == 1;
    }
    public UserVo profile(String authorization, String requestedUsername) {
        String id = tokens.requireFirstPartyUserId(authorization);
        User user = users.findUserByID(id);
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号不存在");
        if (StringUtils.hasText(requestedUsername) && !requestedUsername.equals(user.getUsername()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能查看当前账号资料");
        UserVo profile = new UserVo();
        profile.setUsername(user.getUsername()); profile.setEmail(user.getEmail()); profile.setDescription("");
        return profile;
    }
}
