package com.alisa.user.oauth;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

@RestControllerAdvice(basePackageClasses=OAuthPages.class)
public class OAuthPageErrors {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> invalid(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(java.util.Map.of("message","应用信息无效，请检查回调地址和功能范围"));
    }
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    ResponseEntity<?> unavailable() {
        return ResponseEntity.status(503).body(java.util.Map.of("message","授权存储暂不可用"));
    }
}
