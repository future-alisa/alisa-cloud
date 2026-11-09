package com.alisa.user.oauth;

import java.security.Principal;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

@RestController
@RequestMapping("/oauth")
@ConditionalOnProperty(prefix="alisa.oauth",name="enabled",havingValue="true")
public class OAuthPages {
    private boolean tenantMode;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void tenantMode(com.alisa.user.tenant.TenantDirectory tenants) {tenantMode=true;}
    private final OAuthApplications apps;
    private final OAuthProperties properties;
    private final OAuth2AuthorizationConsentService consents;
    public OAuthPages(OAuthApplications apps,OAuthProperties properties,OAuth2AuthorizationConsentService consents) {
        this.apps=apps; this.properties=properties; this.consents=consents;
    }
    private String escape(String s) { return HtmlUtils.htmlEscape(Objects.toString(s,"")); }
    private String input(String name,String value) { return "<input type='hidden' name='"+escape(name)+"' value='"+escape(value)+"'>"; }
    private String csrf(HttpServletRequest request) { var token=(CsrfToken)request.getAttribute(CsrfToken.class.getName()); return input(token.getParameterName(),token.getToken()); }
    private ResponseEntity<String> page(String title,String content) {
        return ResponseEntity.ok().contentType(new MediaType("text","html",java.nio.charset.StandardCharsets.UTF_8)).cacheControl(CacheControl.noStore())
            .header("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'")
            .body(OAuthBrowserPaths.html("<!doctype html><html lang='zh-CN'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>"+escape(title)+"</title><style>body{font:16px system-ui;max-width:760px;margin:40px auto;padding:16px;color:#243346}section{padding:20px;border:1px solid #ddd;border-radius:12px;margin:16px 0}label{display:block;margin:12px 0}input,select,button{font:inherit;padding:8px}button{cursor:pointer}code{overflow-wrap:anywhere}a{color:#2563ab}</style><h1>"+escape(title)+"</h1><nav><a href='/oauth/apps'>我的应用</a> · <a href='/oauth/connections'>已授权应用</a></nav>"+content+"</html>"));
    }
    @GetMapping("/apps")
    public ResponseEntity<String> applications(Principal user,HttpServletRequest request) {
        if(tenantMode) return ResponseEntity.status(303).location(java.net.URI.create(OAuthBrowserPaths.path("/oauth/tenants"))).body("");
        StringBuilder html=new StringBuilder();
        for(var app:apps.owned(user.getName())) {
            html.append("<section><h2>").append(escape(app.name())).append("</h2><code>").append(escape(app.clientId())).append("</code><p>").append(escape(app.applicationId())).append(app.enabled()?" · 已启用":" · 已停用").append("</p>");
            if(app.enabled()) html.append("<form method='post' action='/oauth/apps/disable'>").append(csrf(request)).append(input("clientId",app.clientId())).append("<button>停用并撤销全部授权</button></form>");
            html.append("</section>");
        }
        for(String app:properties.getApplications().keySet()) {
            html.append("<section><h2>注册 ").append(escape(app)).append(" 接入应用</h2><form method='post' action='/oauth/apps'>").append(csrf(request)).append(input("applicationId",app))
                .append("<label>应用名称 <input required name='name' maxlength='100'></label><label>回调地址 <input required name='redirectUri' type='url'></label><label><input type='checkbox' name='publicClient' value='true'>公开客户端（移动端或浏览器应用，无密钥）</label><p>允许申请的功能：</p>");
            properties.catalog(app).forEach((scope,label)->html.append("<label><input type='checkbox' name='scope' value='").append(escape(scope)).append("'> ").append(escape(label)).append(" <code>").append(escape(scope)).append("</code></label>"));
            html.append("<button>注册应用</button></form></section>");
        }
        return page("应用管理",html.toString());
    }
    @PostMapping("/apps")
    public ResponseEntity<String> create(Principal user,@RequestParam String applicationId,@RequestParam String name,@RequestParam String redirectUri,
            @RequestParam(name="scope",defaultValue="") Set<String> scopes,@RequestParam(defaultValue="false") boolean publicClient) {
        if(tenantMode) throw new ResponseStatusException(HttpStatus.CONFLICT,"请在租户内登记应用");
        var app=apps.create(user.getName(),applicationId,name,Set.of(redirectUri),scopes,publicClient);
        return page("应用已创建","<section><p>Client ID：<code>"+escape(app.clientId())+"</code></p>"+(app.clientSecret()==null?"<p>公开客户端，必须使用 PKCE S256。</p>":"<p>请立即保存密钥，仅显示一次：<code>"+escape(app.clientSecret())+"</code></p>")+"</section>");
    }
    @PostMapping("/apps/disable")
    public ResponseEntity<Void> disable(Principal user,@RequestParam String clientId) {
        apps.disable(user.getName(),clientId); return ResponseEntity.status(303).location(java.net.URI.create(OAuthBrowserPaths.path("/oauth/apps"))).build();
    }
    @GetMapping("/connections")
    public ResponseEntity<String> connections(Principal user,HttpServletRequest request) {
        StringBuilder html=new StringBuilder("<p>撤销后，该应用的访问 token 和刷新 token 将立即失效。</p>");
        for(var app:apps.connections(user.getName())) html.append("<section><h2>").append(escape(app.name())).append("</h2><p>").append(escape(app.authorities())).append("</p><form method='post' action='/oauth/connections/revoke'>").append(csrf(request)).append(input("clientId",app.clientId())).append("<button>撤销授权</button></form></section>");
        return page("已授权应用",html.toString());
    }
    @PostMapping("/connections/revoke")
    public ResponseEntity<Void> revoke(Principal user,@RequestParam String clientId) {
        apps.revoke(user.getName(),clientId); return ResponseEntity.status(303).location(java.net.URI.create(OAuthBrowserPaths.path("/oauth/connections"))).build();
    }
    @GetMapping("/consent")
    public ResponseEntity<String> consent(Principal user,HttpServletRequest request,@RequestParam("client_id") String clientId,
            @RequestParam String scope,@RequestParam String state) {
        var client=apps.findByClientId(clientId);
        if(client==null || apps.resource(clientId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var requested=new LinkedHashSet<>(Arrays.asList(scope.split(" +")));
        if(!client.getScopes().containsAll(requested)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var catalog=properties.catalog(apps.applicationId(clientId));
        var prior=consents.findById(client.getId(),user.getName());
        StringBuilder html=new StringBuilder("<section><h2>").append(escape(client.getClientName())).append(" 请求访问你的账号</h2><p>请选择允许的功能。应用仍受你的数据访问权限限制。</p><form method='post' action='/oauth2/authorize'>")
            .append(csrf(request)).append(input("client_id",clientId)).append(input("state",state));
        for(String s:requested) {
            if(!catalog.containsKey(s)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            boolean approved=prior!=null && prior.getScopes().contains(s);
            html.append("<label><input type='checkbox' name='scope' value='").append(escape(s)).append("' ").append(approved?"checked disabled":"").append("> ").append(escape(catalog.get(s))).append(approved?"（此前已授权，可在已授权应用中撤销）":"").append("</label>");
        }
        html.append("<button>同意所选功能</button></form><form method='post' action='/oauth2/authorize'>").append(csrf(request)).append(input("client_id",clientId)).append(input("state",state)).append("<button>拒绝</button></form></section>");
        return page("授权确认",html.toString());
    }
}
