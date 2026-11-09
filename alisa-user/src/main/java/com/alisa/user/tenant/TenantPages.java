package com.alisa.user.tenant;
import java.security.Principal;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import com.alisa.user.oauth.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.util.HtmlUtils;

@RestController @RequestMapping("/oauth/tenants")
@ConditionalOnProperty(prefix="alisa.tenancy",name="enabled",havingValue="true")
public class TenantPages {
    private final TenantDirectory tenants;private final OAuthApplications apps;private final OAuthProperties scopes;
    public TenantPages(TenantDirectory tenants,OAuthApplications apps,OAuthProperties scopes) {this.tenants=tenants;this.apps=apps;this.scopes=scopes;}
    private String e(String value) {return HtmlUtils.htmlEscape(Objects.toString(value,""));}
    private String csrf(HttpServletRequest request) {var c=(CsrfToken)request.getAttribute(CsrfToken.class.getName());return "<input type='hidden' name='"+e(c.getParameterName())+"' value='"+e(c.getToken())+"'>";}
    private String form(HttpServletRequest request,String action,String fields,String button) {return "<form method='post' action='"+e(action)+"'>"+csrf(request)+fields+"<button>"+e(button)+"</button></form>";}
    private String field(String label,String name) {return "<label>"+e(label)+" <input required name='"+e(name)+"'></label>";}
    private ResponseEntity<String> page(String title,String html) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(new MediaType("text","html",java.nio.charset.StandardCharsets.UTF_8))
            .header("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'")
            .body(OAuthBrowserPaths.html("<!doctype html><html lang='zh-CN'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>"+e(title)+"</title><style>body{font:16px system-ui;max-width:850px;margin:32px auto;padding:16px;color:#243346}section{border:1px solid #ddd;border-radius:12px;padding:18px;margin:16px 0}label{display:block;margin:10px 0}input,select,button{font:inherit;padding:7px}code{overflow-wrap:anywhere}a{color:#2563ab}</style><nav><a href='/oauth/tenants'>租户管理</a> · <a href='/oauth/apps'>我的应用</a> · <a href='/oauth/connections'>用户授权</a></nav><h1>"+e(title)+"</h1>"+html+"</html>"));
    }
    private ResponseEntity<Void> home(String tenant) {return ResponseEntity.status(303).location(java.net.URI.create(OAuthBrowserPaths.path("/oauth/tenants"+(tenant==null?"":"/"+tenant)))).build();}
    @GetMapping public ResponseEntity<String> list(Principal actor,HttpServletRequest request) {
        StringBuilder html=new StringBuilder();
        for(var t:tenants.list(actor.getName())) html.append("<section><a href='/oauth/tenants/").append(e(t.id())).append("'>").append(e(t.name())).append("</a><p><code>").append(e(t.id())).append("</code> · ").append(e(t.role())).append(t.enabled()?"":" · 已停用").append("</p></section>");
        html.append("<section><h2>创建租户</h2>").append(form(request,"/oauth/tenants",field("名称","name"),"创建")).append("</section><section><h2>接受邀请</h2>")
            .append(form(request,"/oauth/tenants/accept",field("租户 ID","tenant")+field("邀请 ID","invitationId")+field("邀请凭据","token"),"加入租户")).append("</section>");
        return page("租户管理",html.toString());
    }
    @PostMapping public ResponseEntity<Void> create(Principal actor,@RequestParam String name) {return home(tenants.create(actor.getName(),name));}
    @PostMapping("/accept") public ResponseEntity<Void> accept(Principal actor,@RequestParam String tenant,@RequestParam String invitationId,@RequestParam String token) {tenants.accept(tenant,actor.getName(),invitationId,token);return home(tenant);}
    @GetMapping("/{tenant}") public ResponseEntity<String> view(Principal actor,HttpServletRequest request,@PathVariable String tenant) {
        var t=tenants.list(actor.getName()).stream().filter(v->v.id().equals(tenant)).findFirst().orElseThrow(()->new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN));
        String base="/oauth/tenants/"+tenant;
        if(!t.enabled()) return page(t.name(),"<p>租户已停用，token 无法访问业务数据。</p>"+("OWNER".equals(t.role())?form(request,base+"/settings",field("名称","name")+"<input type='hidden' name='enabled' value='true'>","重新启用"):""));
        boolean admin=Set.of("OWNER","ADMIN").contains(t.role());StringBuilder html=new StringBuilder("<p>租户 ID：<code>").append(e(tenant)).append("</code> · ").append(e(t.role())).append("</p><section><h2>成员</h2>");
        for(var m:tenants.members(tenant,actor.getName())) html.append("<p>").append(e(m.userId())).append(" · ").append(e(m.role())).append("</p>");
        if(admin) html.append(form(request,base+"/invite",field("现有用户 ID","userId")+"<label>角色 <select name='role'><option>MEMBER</option><option>READER</option>"+("OWNER".equals(t.role())?"<option>ADMIN</option>":"")+"</select></label>","生成邀请"))
            .append(form(request,base+"/member-role",field("用户 ID","userId")+field("角色 MEMBER / READER / ADMIN","role"),"调整角色"));
        if(admin) for(var invitation:tenants.invitations(tenant,actor.getName())) html.append("<p>待接受：").append(e(invitation.userId())).append(" · ").append(e(invitation.role())).append("</p>").append(form(request,base+"/invitations/"+invitation.id()+"/cancel","","撤销邀请"));
        html.append(form(request,base+"/remove",field("用户 ID（退出时填自己）","userId"),"移除成员或退出"));
        if("OWNER".equals(t.role())) html.append(form(request,base+"/owner",field("新所有者用户 ID","userId"),"转移所有权"))
            .append(form(request,base+"/settings",field("名称","name")+"<label><input type='checkbox' name='enabled' value='true' checked>启用租户</label>","保存租户设置"));
        html.append("</section><section><h2>应用及审批</h2>");
        for(var app:apps.tenantClients(tenant,actor.getName())) {
            html.append("<section><h3>").append(e(app.name())).append("</h3><code>").append(e(app.clientId())).append("</code><p>").append(e(app.kind())).append(" · ").append(e(app.approval())).append(app.enabled()?"":" · 已停用").append("</p><p>已批准：").append(e(String.join(" ",app.scopes()))).append("</p>");
            if(admin && !app.kind().equals("resource")) {
                StringBuilder fields=new StringBuilder("<input type='hidden' name='approve' value='true'>");
                for(String s:app.requestedScopes()) fields.append("<label><input type='checkbox' name='scope' value='").append(e(s)).append("'>").append(e(s)).append("</label>");
                html.append(form(request,base+"/applications/"+app.clientId()+"/approval",fields.toString(),"批准所选范围"))
                    .append(form(request,base+"/applications/"+app.clientId()+"/approval","<input type='hidden' name='approve' value='false'>","拒绝并撤销授权"));
            }
            if(app.enabled()) html.append(form(request,base+"/applications/"+app.clientId()+"/disable","","停用并撤销 token"));
            if(admin && app.enabled()) html.append(form(request,base+"/applications/"+app.clientId()+"/secret","","轮换密钥并撤销 token"));
            html.append("</section>");
        }
        for(var application:scopes.getApplications().keySet()) {
            StringBuilder fields=new StringBuilder("<input type='hidden' name='applicationId' value='").append(e(application)).append("'>").append(field("应用名称","name"))
                .append("<label><input type='checkbox' name='machine' value='true'>后台应用（无用户登录）</label><label>回调地址（后台应用可留空） <input name='redirectUri'></label>");
            scopes.catalog(application).forEach((s,label)->fields.append("<label><input type='checkbox' name='scope' value='").append(e(s)).append("'>").append(e(label)).append("</label>"));
            html.append(form(request,base+"/applications",fields.toString(),"登记 "+application+" 应用并提交审批"));
        }
        html.append("</section>");
        if(admin) {
            html.append("<section><h2>业务资源授权</h2><p>审批应用范围后，还需明确授予应用或用户的资源权限；资源 ID * 表示本租户全部该类资源。</p>");
            for(var p:tenants.permissions(tenant,actor.getName())) html.append("<p>").append(e(p.toString())).append("</p>");
            String fields=field("业务 ID","applicationId")+"<label>主体 <select name='subjectType'><option>USER</option><option>APPLICATION</option></select></label>"+field("用户 ID 或 Client ID","subjectId")+field("资源类型","resourceType")+field("资源 ID 或 *","resourceId")+field("动作 read / create / update / delete","action");
            html.append(form(request,base+"/permissions",fields+"<label><input type='checkbox' name='grant' value='true' checked>授予（取消勾选为撤销）</label>","保存资源授权")).append("<a href='").append(e(base)).append("/audit'>查看审计记录</a></section>");
        }
        return page(t.name(),html.toString());
    }
    @PostMapping("/{tenant}/invite") public ResponseEntity<String> invite(Principal actor,@PathVariable String tenant,@RequestParam String userId,@RequestParam String role) {var i=tenants.invite(tenant,actor.getName(),userId,role);return page("邀请已创建","<p>有效期两天。请将这些信息交给被邀请人：</p><p>租户：<code>"+e(tenant)+"</code></p><p>邀请 ID：<code>"+e(i.id())+"</code></p><p>仅显示一次的凭据：<code>"+e(i.token())+"</code></p>");}
    @PostMapping("/{tenant}/member-role") public ResponseEntity<Void> role(Principal a,@PathVariable String tenant,@RequestParam String userId,@RequestParam String role) {tenants.changeRole(tenant,a.getName(),userId,role);return home(tenant);}
    @PostMapping("/{tenant}/invitations/{invitation}/cancel") public ResponseEntity<Void> cancel(Principal a,@PathVariable String tenant,@PathVariable String invitation) {tenants.cancelInvitation(tenant,a.getName(),invitation);return home(tenant);}
    @PostMapping("/{tenant}/remove") public ResponseEntity<Void> remove(Principal a,@PathVariable String tenant,@RequestParam String userId) {tenants.remove(tenant,a.getName(),userId);return home(null);}
    @PostMapping("/{tenant}/owner") public ResponseEntity<Void> owner(Principal a,@PathVariable String tenant,@RequestParam String userId) {tenants.transfer(tenant,a.getName(),userId);return home(tenant);}
    @PostMapping("/{tenant}/settings") public ResponseEntity<Void> settings(Principal a,@PathVariable String tenant,@RequestParam String name,@RequestParam(defaultValue="false") boolean enabled) {tenants.update(tenant,a.getName(),name,enabled);return home(tenant);}
    @PostMapping("/{tenant}/applications") public ResponseEntity<String> app(Principal a,@PathVariable String tenant,@RequestParam String applicationId,@RequestParam String name,@RequestParam(defaultValue="") String redirectUri,@RequestParam(name="scope",defaultValue="") Set<String> scope,@RequestParam(defaultValue="false") boolean machine) {var c=apps.createTenant(tenant,a.getName(),applicationId,name,redirectUri.isBlank()?Set.of():Set.of(redirectUri),scope,machine,false);return credentials(c);}
    private ResponseEntity<String> credentials(OAuthApplications.Created c) {return page("请保存应用凭据","<p>Client ID：<code>"+e(c.clientId())+"</code></p><p>密钥仅显示一次：<code>"+e(c.clientSecret())+"</code></p>");}
    @PostMapping("/{tenant}/applications/{client}/approval") public ResponseEntity<Void> approval(Principal a,@PathVariable String tenant,@PathVariable String client,@RequestParam(defaultValue="false") boolean approve,@RequestParam(name="scope",defaultValue="") Set<String> scope) {apps.approve(tenant,a.getName(),client,scope,approve);return home(tenant);}
    @PostMapping("/{tenant}/applications/{client}/disable") public ResponseEntity<Void> disable(Principal a,@PathVariable String tenant,@PathVariable String client) {apps.disableTenantClient(tenant,a.getName(),client);return home(tenant);}
    @PostMapping("/{tenant}/applications/{client}/secret") public ResponseEntity<String> rotate(Principal a,@PathVariable String tenant,@PathVariable String client) {return credentials(apps.rotate(tenant,a.getName(),client));}
    @PostMapping("/{tenant}/permissions") public ResponseEntity<Void> permission(Principal a,@PathVariable String tenant,@RequestParam String applicationId,@RequestParam String subjectType,@RequestParam String subjectId,@RequestParam String resourceType,@RequestParam String resourceId,@RequestParam String action,@RequestParam(defaultValue="false") boolean grant) {if(!scopes.catalog(applicationId).containsKey(applicationId+":"+resourceType+":"+action)) throw new IllegalArgumentException("Unknown scope");tenants.permission(tenant,a.getName(),new TenantDirectory.Permission(applicationId,subjectType,subjectId,resourceType,resourceId,action),grant);return home(tenant);}
    @GetMapping("/{tenant}/audit") public ResponseEntity<String> audit(Principal a,@PathVariable String tenant,@RequestParam(defaultValue="") String before) {var records=tenants.audits(tenant,a.getName(),50,before);StringBuilder html=new StringBuilder();for(var r:records) html.append("<section><p>").append(e(r.createdAt().toString())).append(" · ").append(e(r.eventType())).append("</p><p>操作者：").append(e(r.actorId())).append("</p><p>对象：").append(e(r.objectId())).append("</p></section>");if(records.size()==50) html.append("<a href='?before=").append(e(records.get(records.size()-1).id())).append("'>下一页</a>");return page("租户审计",html.toString());}
}
