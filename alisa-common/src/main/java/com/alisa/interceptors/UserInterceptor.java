package com.alisa.interceptors;

import com.alisa.model.UserContext;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

public class UserInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 1. 从 Header 拿到网关传过来的加密串
        String base64User = request.getHeader("X-User-Header");

        if (StringUtils.hasText(base64User)) {
            // 2. 解码并反序列化
            UserContext user = UserContext.fromBase64Json(base64User);

            // 3. 存入当前线程，供后续 Service 使用
            if (user != null) {
                UserContext.set(user);
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        // 4. 非常关键：请求结束必须清理 ThreadLocal，否则在线程池环境下会导致数据错乱或内存泄漏
        UserContext.remove();
    }
}