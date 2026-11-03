# 统一提供者架构

## 边界与部署

认证与资源授权是两个扩展点，不把厂商名称作为核心分支：

- alisa-common 的 com.alisa.identity：认证凭证、可信认证上下文、已验证身份和 IdentityProvider 接口。
- alisa-user：注册提供者、账号绑定、统一内部用户 ID、签发内部令牌；默认 LocalIdentityProvider 保留旧登录兼容。
- alisa-authorization：普通 JAR，包含授权模型、AuthorizationProvider 接口、决策引擎、可选管理接口和本地 JDBC/RBAC 实现。
- alisa-user 在显式启用后承载授权 HTTP 接口。没有新增 alisa-auth 服务或 security-starter。
- 业务服务可直接引用 alisa-authorization 的 Java API，或通过可信服务入口查询权限；仍负责真实业务资源的归属和业务条件。

提供者模块通过 Spring @Bean 注册（独立依赖包可使用 Spring Boot AutoConfiguration.imports）。核心使用注册表与每应用配置选择提供者，不包含 GitHub/Google/Keycloak 等厂商 SDK 或分支。本轮只有可运行的本地实现；测试中的外部提供者只证明扩展契约，不能用于生产身份验证。

## 认证 SPI

IdentityProvider 提供 id()、methods()、authenticate(IdentityCredentials, IdentityAuthenticationContext)。

- credentials.method 是提供者声明支持的方法名；values 是不透明的证明材料。其 toString 会隐藏凭证。
- 实现者负责验证签名、issuer、audience、过期时间，以及适用的 OAuth state、nonce、PKCE 和授权码单次使用。
- context 来自服务端，区分 SIGN_IN 与 LINK；绑定时携带已验证的内部 userId。提供者必须把自己的流程状态与这个目的/用户绑定，不能信任 values 中自带的 userId。
- 验证成功返回 VerifiedIdentity(issuer, subject)，失败抛 AuthenticationRejectedException。不要把客户端直接提交的用户资料当作已验证身份。
- 本接口是验证证明与身份归一化的接口，不是假装所有 OAuth 流程都是密码登录。交互式授权跳转、回调和流程状态应由对应适配器使用成熟 OAuth2/OIDC 客户端实现，并提供其必要的流程入口。

注册一个新认证提供者后即可使用通用接口，无须修改 controller、registry 或账号绑定表。

### 账号关联

本地提供者的 subject 是现有 userId，仍使用原有账号、密码验证和旧明文密码升级逻辑。

其他提供者按 (providerId, issuer, subject) 查找内部 userId；从不按邮箱或用户名自动合并。新外部身份必须先登录已有账号，再提交提供者证明完成绑定。唯一键阻止同一外部身份被两个用户绑定，解绑 SQL 同时限定当前用户。

在启用任何外部提供者之前，手动执行 alisa-user/sql/external-identity-schema.sql。没有配置外部提供者时，旧本地登录不需要这个新表。不会自动初始化数据库。

### HTTP 接口

| 方法/路径 | 身份要求 | 功能 |
| --- | --- | --- |
| POST /api/v1/user/signin | 无 | 原客户端账号密码登录，失败仍返回空 token |
| GET /api/v1/user/providers | 无 | 已注册提供者和支持的方法；不返回密钥 |
| POST /api/v1/user/signin/{providerId} | 提供者证明 | 通用提供者登录 |
| GET /api/v1/user/identities | Bearer | 仅查看自己的绑定 |
| POST /api/v1/user/identities/{providerId} | Bearer + 提供者证明 | 绑定到当前账号 |
| DELETE /api/v1/user/identities/{providerId} | Bearer | 按 issuer/subject 解绑自己的身份 |

通用本地登录示例：

    {"method":"password","values":{"username":"alice","password":"实际密码"}}

其他提供者定义自己的 method 与证明材料。未注册的提供者、不支持的方法、无效证明均不会回退到本地密码验证。提供者故障返回 503，不会签发令牌。

## 授权 SPI

AuthorizationProvider 提供 id() 和 decide(AuthorizationRequest)。

请求包含 applicationId、subject(type/id)、resource(type/id)、action、context。context 仅用于可信服务侧事实；不能用客户端自报的 owner、tenant、role 等属性作为授权依据。当前公开 check 接口不会接收 context，也不会接受客户端指定的 subject。

提供者返回 PERMIT / DENY / INDETERMINATE，只有 PERMIT 的 allowed() 为 true。每个应用必须在配置中明确指定一个权威权限源；未知应用默认拒绝，重复提供者或引用不存在的提供者使启动失败。异常和 null 决策变成 INDETERMINATE，绝不回退其他提供者。

PolicyAdministration 是可选能力。外部或只读决策提供者不必实现角色管理；管理请求不能因此偷偷改成本地存储。

### 本地实现

LocalAuthorizationProvider 使用 AuthorizationPolicyStore；默认可用 JdbcAuthorizationPolicyStore，也可以注入自定义存储。

角色、授权、角色绑定均限定 applicationId。授权模型是“角色获得某资源类型/具体资源上的动作，主体绑定角色”。只有 resourceId 的字面值 * 表示该类型全部实例；类型和动作不支持模糊匹配。删除角色通过外键级联清理关联；撤销后下一次检查即生效，没有长时间 JWT 权限快照或隐式缓存。

本地存储只有基础 RBAC 和资源实例限定，不负责推断资源归属、组织继承、租户隔离或 ABAC 事实。外部提供者可以实现其他策略。

### 启用

默认禁用授权功能，避免已有实例因缺少新表而启动失败。需要本地授权时，先手动执行：

    alisa-authorization/src/main/resources/db/authorization-schema.sql

该 SQL 应执行到承载账号/授权服务的数据源中，不会修改 tb_user。然后通过外部配置文件启用，例如 examples/authorization/local.yml：

    alisa:
      authorization:
        enabled: true
        applications:
          my-project: local
        administrators:
          - 已有平台管理员的真实内部用户ID

管理员为空时，所有管理接口都拒绝。administrators 是当前版本的服务端平台管理员名单，不是所有应用中的普通 admin 角色；不能通过管理 API 或登录请求修改这份名单。

实际启动可设置 SPRING_CONFIG_ADDITIONAL_LOCATION 指向该配置文件。不要把示例管理员占位符作为真实授权。自定义外部实现使用相同配置，只需把应用对应的 provider id 改为已注册的提供者 id；纯外部模式不要求本地策略表。

### 授权 HTTP 接口

授权启用后，由 alisa-user 承载 /api/v1/authorization。

- POST /check：检查当前登录用户的权限。body 为 applicationId/resourceType/resourceId/action，subject 由 Bearer token 推导；缺少身份返回 401，提供者不可用返回 503。
- /applications/{app}/roles：GET 列出角色。
- /applications/{app}/roles/{role}：PUT 保存角色，DELETE 删除角色。
- /applications/{app}/roles/{role}/grants：GET/PUT/DELETE 查询、添加、撤销授权。
- /applications/{app}/roles/{role}/bindings：GET/PUT/DELETE 查询、添加、移除角色绑定。

除 /check 外都要求配置中的平台管理员身份。未配置应用不能管理；所选提供者不支持管理则返回 501。路由参数中的 app/role 决定操作范围，不能由请求 body 覆盖。

check 示例：

    {"applicationId":"my-project","resourceType":"document","resourceId":"42","action":"read"}

授权示例（PUT grants）：

    {"resourceType":"document","resourceId":"*","action":"read"}

角色绑定示例（PUT bindings）：

    {"subjectType":"USER","subjectId":"已有用户的内部ID"}

这些 API 不会自动保护任何业务接口。业务代码必须在执行操作前检查决定，并自行验证业务事实；前端菜单隐藏仅是展示。

## 适配外部服务的契约

认证适配器和授权适配器可以分别使用不同实现。外部账号的 issuer/subject 与平台内部 subject ID 必须显式映射。集成包可以依赖 alisa-common 的只读 ExternalIdentityDirectory 接口查询已绑定身份，无须依赖 alisa-user 可执行 JAR 或其 Mapper。没有绑定时返回空集合，不推测身份；一个用户存在多个外部身份时，适配器应按其可信配置明确选择，不能随意取第一个。

外部授权需要用户上下文、令牌交换或服务凭据时，由适配器实现其协议；不要把平台自签 JWT 直接当作其他系统发行的令牌。

当前没有内置第三方登录、授权码跳转、具体厂商授权适配器、服务账号令牌签发、管理 UI 或分布式策略同步。它们可以作为独立依赖扩展，核心不需要增加厂商判断。

新增提供者至少测试：成功与拒绝、错误 issuer/audience/过期证明、状态与重放校验、账号绑定冲突、跨应用隔离、外部服务故障、权限撤销。核心回归测试也验证任意测试提供者可以注册并完成相同流程。
