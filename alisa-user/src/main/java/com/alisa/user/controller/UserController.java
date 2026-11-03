package com.alisa.user.controller;

import java.util.Map;
import com.alisa.util.Result;
import com.alisa.user.dto.ChangePasswordRequest;
import com.alisa.user.dto.user.UserDto;
import com.alisa.user.service.IdentityService;
import com.alisa.user.vo.UserVo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
public class UserController {
    private final IdentityService identity;
    public UserController(IdentityService identity) { this.identity = identity; }

    @PostMapping("/signin")
    public Result<String> signin(@RequestBody UserDto request) { return new Result<>(identity.signin(request)); }

    @PostMapping("/signup")
    public Result<Boolean> signup(@RequestBody UserDto request) { return new Result<>(identity.signup(request)); }

    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendResetCode(@RequestBody Map<String, String> request) {
        identity.sendResetCode(request.get("email"));
        return ResponseEntity.ok(Map.of("success", true, "message", "若邮箱已注册，验证码将发送到该邮箱"));
    }

    @PostMapping("/change-password")
    public Result<Boolean> changePassword(@RequestBody ChangePasswordRequest request) {
        return new Result<>(identity.changePassword(request));
    }

    @PostMapping("/change-password-email")
    public Result<Boolean> changePasswordByEmail(@RequestBody ChangePasswordRequest request) {
        return new Result<>(identity.resetPassword(request));
    }

    @GetMapping("/profile")
    public Result<UserVo> profile(@RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String username) {
        return new Result<>(identity.profile(authorization, username));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() { return ResponseEntity.ok("UP"); }
}
