# 第三方应用授权

多租户、后台应用权限、管理员审批和审计已增加，见 [MULTITENANCY.md](MULTITENANCY.md)。下面记录默认单租户委托模式，新增模式使用独立开关。

在现有 `alisa-user` 内嵌 Spring Authorization Server，未增加独立服务。身份提供者负责证明用户身份；OAuth 应用代表外部调用方；资源授权提供者负责用户本身的权限。三者独立扩展。

## 功能

- `/oauth/apps`：登录现有本地账号，登记应用、精确回调地址、允许申请的功能范围；客户端密钥仅创建时显示，数据库保存 BCrypt 摘要。停用应用会删除全部 token 和同意记录。
- `/oauth/consent`：显示应用名称和功能说明，用户可只同意部分范围。已有同意范围可在“已授权应用”中撤销后重新选择。
- `/oauth/connections`：查看自己的授权并撤销。访问 token 为不透明引用，每次验证查询持久化状态；撤销同时删除刷新 token，无验证结果缓存。
- `/oauth2/authorize`、`/oauth2/token`：授权码 + 强制 PKCE S256。公开客户端没有密钥；保密客户端使用 `client_secret_basic`，支持轮换刷新 token。不提供密码模式、隐式模式和第三方机器身份授权。
- `/oauth2/introspect`：仅平台管理员登记的同业务资源服务器可调用。第三方应用不能查询其他用户 token，也不能取得机器 token。
- `/oauth2/revoke`：标准客户端 token 撤销端点。

范围格式为 `业务ID:资源类型:动作`，例如 `smallokr:target:read`。平台声明目录，应用注册选择目录子集，用户再次选择同意子集。第三方有效权限为“应用允许范围 ∩ 用户同意范围 ∩ 用户资源权限 ∩ 数据归属”。未知范围、跨业务、缺失权限或授权服务故障均拒绝。权限目录位于部署配置，公共模块不包含 SmallOKR 业务定义。

## 启用（当前默认关闭）

1. 备份数据库，手动执行 `alisa-user/src/main/resources/sql/oauth.sql` 以及 `alisa-authorization/src/main/resources/db/authorization-schema.sql`。不会在启动时自动建表。OAuth SQL 来自 SAS 1.0.4 原始 schema，MySQL 的 blob 容量需按实际授权属性大小评估。
2. 用户服务导入 `examples/oauth-smallokr.yml`；配置 `OAUTH_ISSUER` 为浏览器和服务都可访问的 HTTPS origin，例如 `https://alisa.docker.local`，不得使用上游内部地址。反向代理必须把 `/oauth2/**`、`/oauth/**`、`/login`、`/logout`、`/.well-known/**` 一并路由至用户服务，并正确传递受信任的代理头、Cookie 和 HTTPS。生产设置 Secure session Cookie；HTTP 只可用 `alisa.oauth.allow-loopback-redirects=true` 做本机开发。
3. `AUTHORIZATION_ADMINISTRATORS` 为平台管理员的内部用户 ID。使用已有 `/api/v1/authorization/applications/smallokr/roles/...` API 配置用户角色和资源授权。例如角色授予 `resourceType=target,resourceId=*,action=read` 后，绑定 `subjectType=user,subjectId=<用户ID>`。OAuth 同意不会自动扩大用户角色。
4. 管理员用第一方登录 token 请求 `POST /api/v1/user/oauth/resource-clients`，JSON `{"applicationId":"smallokr","name":"SmallOKR API"}`。保存返回的 `clientId/clientSecret`，这是服务端验证凭据，绝不能放入 React Native 或第三方客户端。该端点不接受第三方委托 token。
5. SmallOKR 三个业务服务配置：`JWT_SECRET` 与用户服务一致；`OAUTH_ISSUER` 同上；`OAUTH_INTROSPECTION_URI=https://.../oauth2/introspect`；`OAUTH_AUTHORIZATION_URI=https://.../api/v1/authorization/check`；`OAUTH_RESOURCE_CLIENT_ID/SECRET` 使用第 4 步凭据。私有 CA 需要加入每个 Java 进程信任库。开发时 HTTP 端点仅支持显式 `OAUTH_ALLOW_LOOPBACK=true` 的 loopback 地址。
6. 先部署验证 Bearer 的业务服务，再部署带 `delegated-ready: true` 路由的网关。该标记表示后端会自行验证 token；旧版本后端不得使用。网关会清除调用方身份头，不会为未经验证的不透明 token 伪造用户身份。
7. 浏览器访问 `/oauth/apps` 注册接入应用。页面使用独立登录会话及 CSRF 防护，现有 App Bearer 登录 API 仍使用原有协议。第一方 token 保留原有功能，补强数据归属；第三方 token 必须经过 scope 和权限提供者检查。

## 第三方调用示例

生成随机 `state` 和 43–128 字符 `code_verifier`，计算 `BASE64URL(SHA256(code_verifier))`：

```text
GET /oauth2/authorize?response_type=code&client_id=CLIENT_ID&redirect_uri=ENCODED_CALLBACK&scope=smallokr%3Atarget%3Aread&state=RANDOM_STATE&code_challenge=CHALLENGE&code_challenge_method=S256
```

回调必须验证 state，再用一次性 code 换 token：

```text
POST /oauth2/token
Authorization: Basic BASE64(CLIENT_ID:CLIENT_SECRET)
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&code=CODE&redirect_uri=ENCODED_CALLBACK&code_verifier=VERIFIER
```

公开客户端不发送 Basic，改为表单 `client_id`。拿到 `access_token` 后：

```text
GET /target-service/api/v1/target/get
Authorization: Bearer ACCESS_TOKEN
```

仅授予读取时，目标删除返回 403；阶段成果和待办读取也需分别授权。查找/修改/删除对象先验证数据归属，不能借客户端传入 userId 访问其他用户数据。Trace 当前转发目标读取，因此使用 target:read。

## 验证与限制

OAuthFlowTest 使用 H2 和真实 Spring Security 过滤链验证授权码、部分同意、PKCE、内省隔离、撤销、应用归属和 CSRF；没有连接线上数据库。部署后应补充真实 HTTPS、网关、MySQL、浏览器会话和实际业务数据的联调。

本仓库 Boot 3.0.2 / Security 6.0.x 对应 SAS 1.0.4；沿用兼容依赖避免本次跨版本升级。面向公网生产前，应统一升级至仍受维护的 Spring 平台并做安全审计、登录限流、MFA 和密钥托管。当前登录页面接入已有本地身份提供者，外部身份提供者仍通过现有身份绑定 SPI 扩展，不会自动把 GitHub 等身份作为业务权限。
