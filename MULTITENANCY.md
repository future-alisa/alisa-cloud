# 多租户、后台应用与管理员审批

本功能仍在现有 alisa-user 中运行，没有增加独立服务。默认关闭，不自动建表、不修改线上数据、不自动重启服务。

## 已实现的行为

- 租户创建、改名、停用/恢复；停用保留数据和审计，不提供不可逆物理删除。
- 一个账号可以加入多个租户。OWNER 为唯一所有者，支持事务内转移所有权；ADMIN 管理成员、应用和资源权限；MEMBER 使用明确授予的业务权限；READER 即使被误授写权限也不能写入。
- 定向邀请已注册用户：仅目标账号可接受，凭据保存 SHA-256 摘要、有效期两天、只能使用一次，管理员可撤销。不会代发邮件。
- 租户内注册委托应用或后台应用，初始状态 PENDING。租户管理员可批准请求范围的子集，也可拒绝；审批变化撤销已有授权码、token 和同意记录。
- 后台应用使用标准 client_credentials，令牌身份为 APPLICATION，包含 tenant_id、application_id 和 scope，没有 userId。它不冒充创建者，不能调用用户或租户管理接口。
- 后台应用的有效权限为“管理员批准的 scope ∩ 租户内资源授权”。仅批准 scope 不会自动获得整租户数据。USER 与 APPLICATION 的授权主体分开保存。
- 租户 OWNER/ADMIN 对本租户业务具有管理权限；人类用户仍遵守业务数据归属。普通成员需要明确资源授权；委托 token 另外受用户同意范围限制。后台应用可在明确授权范围内跨用户访问本租户数据。
- 租户资源服务器凭据只能内省本租户、同业务的 token。由平台管理员签发的全局资源服务器凭据可以验证同业务多个租户的 token，供共享业务服务使用。
- 密钥轮换、应用停用、用户撤销和租户停用会撤销相应 OAuth token。租户会话使用租户/成员版本校验；停用后恢复租户、移除后重新邀请成员，也不会复活旧租户会话。
- 追加审计：租户、成员、邀请、角色、应用、审批、密钥轮换、业务权限授予/撤销及资源授权判断。不提供审计修改/删除接口，密钥、邀请明文凭据和 token 不进入审计。列表使用稳定游标分页。

## 管理页面

启用后访问 `/oauth/tenants`，使用现有本地账号登录。可以创建租户、邀请成员、转移所有权、登记应用、勾选审批范围、配置业务资源权限和查看审计。`/oauth/apps` 自动转到租户管理，防止绕过租户应用登记。浏览器操作有会话和 CSRF 防护。

SmallOKR App 登录后会检测 `/api/v1/user/tenancy`：启用时先选择空间，再加载业务页面；支持创建空间、刷新列表和切换空间。切换会卸载旧业务页面并清空其上下文。关闭功能或连接旧版服务器时保留原有单租户行为。权限不足返回 403 时不会再把用户强制退出登录。

## 安装与部署顺序

1. 备份数据库，在用户服务数据库中先安装 OAuth 表和资源授权表（见 OAUTH.md），再手动执行 `alisa-user/src/main/resources/sql/tenant.sql`。这是一次性增量迁移，不要重复执行。
2. 在目标服务数据库执行 SmallOKR 的 `sql/tenant_target.sql`，在待办服务数据库执行 `sql/tenant_todo.sql`。目标、待办保存 tenant_id，阶段成果和重复规则通过父对象继承租户，不重复保存可能不一致的租户字段。
3. 用户服务导入 `examples/multitenant-smallokr.yml`，设置外部 HTTPS `OAUTH_ISSUER` 和平台管理员 ID。样例复用 oauth-smallokr.yml 的业务 scope 目录，并把 SmallOKR 的授权提供者设为 `tenant`。内置旧 `local` 提供者不隔离租户，因此租户 token 不允许使用它；其他自定义提供者必须使用请求 context.tenantId 隔离策略。
4. 共享业务服务使用平台管理员创建的资源服务器验证凭据，配置 OAuth 内省/PDP 地址。三个 SmallOKR 服务设置 `SMALLOKR_TENANCY_ENABLED=true`，必须已完成第 2 步数据库迁移。保持 JWT_SECRET 一致，并部署已更新的公共库和服务。新模式拒绝没有租户身份的旧业务 token；客户端会先交换租户 token。
5. 部署网关已有的 OAuth 路由与 delegated-ready 标记，反向代理同时转发 `/oauth/**`、`/oauth2/**`、`/login`、`/logout`。网关不相信调用方身份头，业务服务从已验证 token 获取租户，忽略自定义租户头。
6. 部署新客户端。先在测试环境验证两租户流程，再切换生产。用户服务与业务服务的租户开关必须协调启用，不能把租户 token 发给没有隔离能力的旧后端。

目标/待办服务启动时会核对租户字段与隔离开关：已有 tenant_id 字段却关闭隔离、或开启隔离但尚未迁移，都会拒绝启动，避免配置错误造成无租户过滤的数据访问。迁移完成后不能直接切回旧模式；回滚须按数据迁移方案处理。

现有业务数据的 tenant_id 默认为空，明确表示旧模式数据，不会自动归属于任意新租户。开启新模式前，需由你确认目标租户和各用户的成员资格，再编写数据归属迁移；本次没有移动现有数据。关闭业务租户模式时也禁止发送带租户身份的 token，避免静默降级。

## REST 管理接口

以下 `/api/v1/user/tenants` 接口只接受第一方用户 JWT，不接受第三方委托或后台应用 token；路径中的租户 ID 每次都验证成员/管理员权限。

| 方法和路径 | 行为 |
|---|---|
| GET /tenants | 当前账号的租户 |
| POST /tenants | 创建，body `{name}` |
| PUT /tenants/{tenant} | 所有者更新，body `{name,enabled}` |
| POST /tenants/{tenant}/token | 选择租户，返回租户 access_token |
| GET /tenants/{tenant}/members | 成员列表 |
| POST /tenants/{tenant}/invitations | 生成邀请，body `{userId,role}` |
| GET /tenants/{tenant}/invitations | 管理员查看邀请元数据（没有凭据） |
| DELETE /tenants/{tenant}/invitations/{id} | 撤销邀请 |
| POST /tenants/{tenant}/invitations/accept | 目标账号接受，body `{invitationId,token}` |
| PUT /tenants/{tenant}/members/{user} | 修改角色，body `{role}` |
| DELETE /tenants/{tenant}/members/{user} | 移除或退出；所有者须先转移所有权 |
| POST /tenants/{tenant}/owner | 转移所有权，body `{userId}` |
| GET/POST /tenants/{tenant}/applications | 应用列表/提交登记 |
| PUT /tenants/{tenant}/applications/{client}/approval | 审批，body `{approve,scopes}` |
| DELETE /tenants/{tenant}/applications/{client} | 停用及撤销 token |
| POST /tenants/{tenant}/applications/{client}/secret | 轮换密钥，明文仅返回一次 |
| POST /tenants/{tenant}/resource-clients | 租户级资源服务器凭据 |
| GET/PUT/DELETE /tenants/{tenant}/permissions | 查看/授予/撤销资源权限 |
| GET /tenants/{tenant}/audit?limit=50&before=... | 只读审计分页 |

上述表中的 /tenants 前缀实际为 /api/v1/user/tenants。应用登记 body：

```json
{"applicationId":"smallokr","name":"目标同步任务","machine":true,"publicClient":false,"redirectUris":[],"scopes":["smallokr:target:read"]}
```

管理员批准 scope 后，再授予资源权限：

```json
{"applicationId":"smallokr","subjectType":"APPLICATION","subjectId":"CLIENT_ID","resourceType":"target","resourceId":"*","action":"read"}
```

后台应用取得 token：

```text
POST /oauth2/token
Authorization: Basic BASE64(CLIENT_ID:CLIENT_SECRET)
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&scope=smallokr%3Atarget%3Aread
```

`resourceId=*` 仅表示该 token 所属租户的资源，不表示跨租户。列表接口需要该资源类型的通配读取权限；具体对象授权不自动扩大为列表读取。

## 验证

TenantFlowTest：审批前取 token 失败、部分审批、跨租户审批和内省隔离、后台身份、scope/PDP 双重限制、轮换密钥、邀请定向/一次性/撤销、所有权转移、成员移除、租户恢复不复活旧会话、委托同意的成员资格、管理页面 CSRF。

SmallOKR TenantMapperTest：使用真实 MyBatis XML 与 H2 MySQL 模式，验证同一用户在两租户的目标/成果/待办/重复规则读写隔离，及重复主键不能覆盖其他租户数据。TenantGate.test 验证客户端在选择空间前不加载业务页面、切换时清理旧页面。

仍需在部署环境验证真实 MySQL、HTTPS/私有 CA、反向代理、浏览器 Cookie 和 Android 网络访问。当前提供的是完整的租户管理和隔离基础能力，未增加计费、配额、跨地域部署等商业化 SaaS 能力。

## 组织目录与托管账号

「组织与用户」将开户和租户分配分成两个步骤。组织创建者是组织所有者，可以关联自己拥有且已启用的现有租户。每个租户最多关联一个组织，已有租户继续独立运行，直到所有者主动关联。

1. 所有者填写用户名和邮箱，创建待激活记录，将一次性链接交给本人。链接有效两天，可以撤销。系统保存链接凭据的摘要。
2. 用户打开链接，验证登记邮箱并自行设置密码。验证码使用独立 Redis 命名空间，复用现有邮件、频率限制和尝试次数限制。部署需配置邮件发送与 Redis。
3. 所有者将已激活账号分配到关联租户。首次分配授予普通成员角色，重复分配保持已有角色。应用权限继续由租户授权配置管理。
4. 已有 Alisa 账号可以按邮箱或用户 ID 发起租户邀请，继续使用现有邀请接受流程。

组织目录表纳入现有认证数据库迁移。上线时先用新 API 镜像运行数据库迁移 Job，再更新前后端。组织域名验证、组织级停用和管理员委派属于独立功能。
