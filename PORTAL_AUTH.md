# Portal OAuth 接入准备

与 alisa-portal 的 `feature/alisa-cloud-auth` 配套。门户客户端使用授权码 + PKCE，Go 后端使用不透明 token 内省及 AuthorizationController `/check` 校验。

## 此变更

- Kubernetes 同一公网域名下增加 `/auth` 协议入口，无需修改 Windows Caddy 的根路径规则。
- 只转发 OAuth、登录/注销、发现和权限判断路径到 alisa-user，不建立全量 `/auth/api/**` 绕过网关的路由。
- 显式重置受信任的 forwarded host/proto/port/prefix，移除调用方 Forwarded / X-Forwarded-Ssl。用户服务使用 framework forwarded headers，并将浏览器会话 Cookie 限于 /auth、Secure、HttpOnly、Lax。
- 修复 OAuth 与租户管理页的导航、表单和相对 Location 在路径前缀下跳错地址；根路径部署保持原样。
- 增加 portal scope：`portal:console:manage`。这表示整个门户实例的基础设施管理权限，不是任意身份租户的数据读取权限。

## 默认不启用生产 OAuth

`alisa-portal-oauth-config` 使用 `${OAUTH_ENABLED:false}` / `${AUTHORIZATION_ENABLED:false}`，平台管理员列表默认空。没有新增数据库写入、账号、权限或客户端凭据，也不会自动执行 SQL。

先备份真实 MySQL，检查 OAUTH.md 列出的 OAuth / 资源授权表。多租户只在 MULTITENANCY.md 所需表和提供者准备好时启用，不能用 local 提供者接收租户 token。

待账号 alisa 创建并验证登录后，将其真实内部 ID 放入私有 `alisa-portal-oauth-enablement` Secret 的 `PORTAL_BOOTSTRAP_ADMINISTRATOR`，不要将用户名误填为内部 ID。该 Secret 另包含 `OAUTH_ENABLED=true`、`AUTHORIZATION_ENABLED=true`；issuer 默认为 `https://alisa.vip.cpolar.cn/auth`。配置会保留已有 AUTHORIZATION_ADMINISTRATORS 并追加该账号，不替换现有管理员列表。多租户模式另设 `PORTAL_AUTHORIZATION_PROVIDER=tenant` 并在门户配置所属身份租户 ID。不得为启用此功能静默切换现有 tenancy 模式。

用管理员账号登记 applicationId=portal 的保密应用，精确回调 `https://alisa.vip.cpolar.cn/api/auth/callback`，scope 仅 `portal:console:manage`；再登记同业务的资源服务器内省凭据。创建门户管理角色并授予 console/*/manage，仅绑定确认的门户实例管理员。客户端与内省密钥只放入门户私有 Secret。

## 发布顺序与验收

1. 配套源码完成评审后分别构建 alisa-user 和门户的镜像，验证 digest，再按发布 PR 更新镜像引用。此源码 PR 没有更新镜像引用；不能仅合并源码就认为新 Java 代码已经运行。
2. 审核数据库备份/表结构后，再注入启用 Secret 并更新用户服务 Pod。只应用 ConfigMap 不足以刷新环境变量。
3. 验证真实 `/auth/login`、应用登记页、授权确认页、回调、内省、权限判断和撤销。不要输出真实 code/token/密钥到日志。
4. 将门户所需配置写入 `portal-auth` Secret 并发布门户。验证 alisa 成功进入管理台、普通用户/错误业务/错误租户失败，访客目录保持可用。

本地验证为 Maven OAuthFlowTest、OAuthBrowserPathsTest 及相关用户服务测试，数据库仅 H2。Kubernetes 清单通过 YAML、引用、路径映射与关闭默认值检查；实际 Gateway Accepted/ResolvedRefs 和 MySQL 联调仍在生产发布前完成。
