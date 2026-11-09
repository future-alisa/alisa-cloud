package com.alisa.user.tenant;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
@RestControllerAdvice(basePackageClasses={TenantController.class, OrganizationController.class})
public class TenantErrors {
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<?> invalid() {return ResponseEntity.badRequest().body(java.util.Map.of("message","租户或权限参数无效"));}
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class) ResponseEntity<?> status(org.springframework.web.server.ResponseStatusException e) {return ResponseEntity.status(e.getStatusCode()).body(java.util.Map.of("message",java.util.Objects.toString(e.getReason(),"请求被拒绝")));}
    @ExceptionHandler(org.springframework.dao.DataAccessException.class) ResponseEntity<?> storage() {return ResponseEntity.status(503).body(java.util.Map.of("message","租户存储暂不可用"));}
}
