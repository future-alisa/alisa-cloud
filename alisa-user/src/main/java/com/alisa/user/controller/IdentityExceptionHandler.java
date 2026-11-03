package com.alisa.user.controller;

import com.alisa.util.Result;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.mail.MailException;
import com.alisa.identity.AuthenticationRejectedException;
import org.springframework.dao.DataAccessException;

@RestControllerAdvice(basePackageClasses = UserController.class)
public class IdentityExceptionHandler {
    @ExceptionHandler(AuthenticationRejectedException.class)
    public ResponseEntity<Result<Boolean>> authentication(AuthenticationRejectedException exception) {
        return response(401, "身份验证失败");
    }
    private ResponseEntity<Result<Boolean>> response(int status, String message) {
        Result<Boolean> result = new Result<>(false);
        result.setCode(status);
        result.setMessage(message);
        return ResponseEntity.status(status).body(result);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Boolean>> invalid(IllegalArgumentException exception) {
        return response(400, exception.getMessage());
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<Boolean>> status(ResponseStatusException exception) {
        return response(exception.getStatusCode().value(), exception.getReason());
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Boolean>> duplicate(DataIntegrityViolationException exception) {
        return response(409, "账号或邮箱已存在");
    }
    @ExceptionHandler(MailException.class)
    public ResponseEntity<Result<Boolean>> mail(MailException exception) {
        return response(503, "邮件服务暂不可用，请稍后重试");
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<Boolean>> storage(DataAccessException exception) {
        return response(503, "存储服务暂不可用，请稍后重试");
    }
}
