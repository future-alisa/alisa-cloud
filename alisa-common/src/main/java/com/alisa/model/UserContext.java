package com.alisa.model;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;

@Data
public class UserContext {
    private String userId;
    private String username;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ThreadLocal<UserContext> THREAD_LOCAL = new ThreadLocal<>();

    public static void set(UserContext user) {
        THREAD_LOCAL.set(user);
    }

    public static UserContext get() {
        return THREAD_LOCAL.get();
    }

    public static void remove() {
        THREAD_LOCAL.remove();
    }

    public String toBase64Json() {
        // 1. 手动拼装 JSON (保持轻量，不依赖 Jackson)
        String userIdStr = (this.userId == null) ? "" : this.userId;
        String json = "{\"userId\":\"" + userIdStr + "\",\"username\":\"" + this.username + "\"}";

        // 2. Base64 编码
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    public static UserContext fromBase64Json(String base64User) {
        try {
            if (base64User == null || base64User.isEmpty()) {
                return null;
            }
            // 1. Base64 解码
            byte[] bytes = Base64.getDecoder().decode(base64User);
            String json = new String(bytes, StandardCharsets.UTF_8);

            // 2. JSON 反序列化
            return objectMapper.readValue(json, UserContext.class);
        } catch (Exception e) {
            // 解析失败返回 null，或者记录日志
            return null;
        }
    }
}
