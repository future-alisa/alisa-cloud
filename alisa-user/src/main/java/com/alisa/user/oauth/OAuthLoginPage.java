package com.alisa.user.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

/** Passwords are submitted only to the authorization server's CSRF-protected login. */
@RestController
@ConditionalOnProperty(prefix="alisa.oauth",name="enabled",havingValue="true")
public class OAuthLoginPage {
    @org.springframework.beans.factory.annotation.Value("${alisa.oauth.password-reset-url:/admin/reset-password}")
    private String passwordResetUrl;
    private final OAuthApplications apps;

    public OAuthLoginPage(OAuthApplications apps) {
        this.apps = apps;
    }

    private String callbackSources(HttpServletRequest request, HttpServletResponse response) {
        var saved = new HttpSessionRequestCache().getRequest(request, response);
        if (saved == null || !"GET".equals(saved.getMethod())
                || !OAuthBrowserPaths.path(request, "/oauth2/authorize").equals(URI.create(saved.getRedirectUrl()).getRawPath())) {
            return "";
        }
        var clientIds = saved.getParameterValues("client_id");
        if (clientIds == null || clientIds.length != 1) return "";
        var client = apps.findByClientId(clientIds[0]);
        if (client == null) return "";
        var redirects = saved.getParameterValues("redirect_uri");
        if (redirects == null && client.getRedirectUris().size() == 1)
            return OAuthBrowserPaths.formCallbackSources(client.getRedirectUris());
        if (redirects == null || redirects.length != 1 || !client.getRedirectUris().contains(redirects[0])) return "";
        return OAuthBrowserPaths.formCallbackSources(Set.of(redirects[0]));
    }

    @GetMapping("/login")
    public ResponseEntity<String> login(HttpServletRequest request, HttpServletResponse response) {
        var csrf=(CsrfToken)request.getAttribute(CsrfToken.class.getName());
        String message=request.getParameter("error")!=null?"<div class='notice error' role='alert'>账号或密码不正确，请重新输入。</div>":
            request.getParameter("expired")!=null?"<div class='notice' role='alert'>登录页面已过期，请重新输入账号和密码。</div>":
            request.getParameter("logout")!=null?"<div class='notice' role='status'>你已退出 Alisa 账号。</div>":"";
        String nonce=java.util.UUID.randomUUID().toString();
        String body="""
            <!doctype html><html lang='zh-CN'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>
            <title>登录 Alisa 账号</title><style>
            *{box-sizing:border-box}body{margin:0;min-height:100vh;display:grid;place-items:center;background:#f5f7fc;color:#202b40;font:14px -apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC',sans-serif;padding:24px}
            main{width:100%;max-width:440px;background:white;padding:36px;border:1px solid #e9edf3;border-radius:20px;box-shadow:0 20px 70px #2536600b}.brand{display:flex;gap:10px;align-items:center;font-size:29px;font-weight:750;letter-spacing:-1px}.brand span{display:grid;place-items:center;background:#5267e8;color:white;width:38px;height:40px;border-radius:11px;font-size:22px}.eyebrow{font-size:11px;color:#5267e8;font-weight:600;margin:30px 0 8px}h1{font-size:25px;margin:0 0 10px;letter-spacing:-.5px}.intro{font-size:12px;color:#8a93a4;line-height:1.8;margin:0 0 27px}label{display:block;font-size:12px;color:#657189;margin:17px 0 8px}input{width:100%;border:1px solid #dde3ee;border-radius:9px;padding:13px;font:inherit;color:#202b40}input:focus{outline:3px solid #e8edff;border-color:#8899ed}button{width:100%;background:#5267e8;color:white;border:0;border-radius:9px;padding:14px;font:inherit;font-weight:600;cursor:pointer;margin-top:24px}button:hover{background:#445ad7}button:focus-visible{outline:3px solid #a8b6ff;outline-offset:3px}.foot{border-top:1px solid #e9edf3;padding-top:21px;margin:28px 0 0;text-align:center;font-size:11px;color:#8a93a4;line-height:1.8}.notice{background:#f0f4ff;border:1px solid #e2e8fa;border-radius:8px;font-size:12px;padding:12px;line-height:1.7}.error{background:#fff0ed;border-color:#f6d5ce;color:#b36555}@media(max-width:480px){main{padding:28px 24px}}
            </style></head><body><main><div class='brand'><span>A</span>alisa.</div><p class='eyebrow'>ALISA ACCOUNT</p><h1>欢迎回来</h1><p class='intro'>登录你的 Alisa 账号，继续访问应用与服务。</p>
            """+message+"<form method='post' action='"+HtmlUtils.htmlEscape(OAuthBrowserPaths.path("/login"))+"'>"+
            "<input type='hidden' name='"+HtmlUtils.htmlEscape(csrf.getParameterName())+"' value='"+HtmlUtils.htmlEscape(csrf.getToken())+"'>"+
            "<label for='username'>用户名</label><input id='username' name='username' autocomplete='username' placeholder='输入 Alisa 用户名' required autofocus maxlength='100'>"+
            "<label for='password'>密码</label><input id='password' name='password' type='password' autocomplete='current-password' placeholder='输入密码' required>"+
            "<button type='submit'>登录并继续 →</button></form><p><a href='"+HtmlUtils.htmlEscape(passwordResetUrl)+"'>忘记密码？</a></p><p class='foot'>由 Alisa 统一账号提供身份验证<br>应用权限将在下一步确认</p></main><script nonce='"+nonce+"'>"+
            """
            const form = document.querySelector('form');
            const button = form.querySelector('button');
            form.addEventListener('submit', event => {
                if (button.disabled) {
                    event.preventDefault();
                    return;
                }
                button.disabled = true;
                button.textContent = '正在登录…';
            });
            window.addEventListener('pageshow', () => {
                button.disabled = false;
                button.textContent = '登录并继续 →';
            });
            </script></body></html>
            """;
        return ResponseEntity.ok().contentType(new MediaType("text","html",StandardCharsets.UTF_8)).cacheControl(CacheControl.noStore())
            .header("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'; script-src 'nonce-" + nonce + "'; form-action 'self'" + callbackSources(request, response) + "; frame-ancestors 'none'; base-uri 'none'")
            .header("Referrer-Policy","no-referrer").body(body);
    }
}
