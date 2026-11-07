# 用户服务迁移与项目接入

## 模块边界

| 模块 | 职责 | 运行方式 |
| --- | --- | --- |
| alisa-common | 公共响应、JWT、请求身份上下文与基础工具 | 被业务服务引用的普通 JAR |
| alisa-gateway | 公共入口、验签、身份传递、可配置路由 | 独立进程 |
| alisa-user | 统一账号、登录凭证、当前账号资料、密码修改及邮箱找回 | 独立进程 |
| alisa-notification | 通用邮件发送接口和 SMTP 实现，可替换传输实现 | 普通 JAR，按需自动配置 |

SmallOKR 的目标、阶段成果、待办和行为记录仍归 SmallOKR。项目侧扩展用户资料、成员关系或业务权限时，以公共 userId 建立自己的关联，不向 alisa-user 塞入业务表。

后续统一提供者扩展已补充可选的 alisa-authorization 基础 RBAC 模块，详见 provider-architecture.md；默认仍禁用。没有实现租户隔离、OAuth/OIDC 协议服务器、SSO 跳转或刷新令牌。旧项目中未实现的角色、权限实体没有原样迁入。当前是共享账号体系；邮件 application-name 只是品牌文案，不是租户标识。

业务服务引用 alisa-common，不依赖 alisa-user 可执行 JAR。邮件接入方引用 alisa-notification 和 spring-boot-starter-mail；通知库没有预置用户或目标业务，且没有单独启动入口。

## 兼容约定

- Maven 模块/镜像：alisa-user；Java 包：com.alisa.user。
- Nacos 注册名仍为 user-service；默认端口仍为 8084。
- API 前缀仍为 /api/v1/user，普通成功响应仍为 code/message/data，code 为数字 200。
- signin 成功返回 JWT 字符串；账号不存在或密码错误返回空字符串，避免旧客户端把异常当作成功。
- JWT 继续使用 alisa-common 的 subject=username 和 userId claim，签名算法及 30 天有效期沿用原实现。网关与账号服务必须使用相同的 JWT_SECRET。
- /send 成功保留 success/message 结构；不向调用者透露邮箱是否已注册。
- /change-password 支持 username/currentPassword/newPassword，也兼容旧客户端提交的 email/code/newPassword；/change-password-email 路径保留。
- /profile 可不传 username；若传入，只能与当前 JWT 对应账号一致。直接经过 HTTPRoute 访问该接口也需要 Bearer token，伪造身份 Header 无效。
- 当前令牌是无状态 JWT，没有服务器端即时注销/撤销能力；客户端退出应丢弃令牌。

## 数据兼容与密码

继续使用现有 db_user.tb_user 及原 user_id，不复制用户、不生成新的关联 ID，不自动执行 SQL 或迁移数据库。

新注册和修改密码使用 BCrypt；旧明文密码可继续登录，成功后用“ID + 旧密码值”条件原子升级，避免覆盖同时发生的密码修改。BCrypt 输入按 UTF-8 限制 72 字节，旧账号若超过此范围须先走找回密码。

部署前运行 alisa-user/sql/existing-schema-check.sql：确认 user_password 至少能容纳 60 字符，并处理重复用户名、重复邮箱，再补齐缺少的唯一索引。仓库旧表定义为 VARCHAR(100)，能容纳 BCrypt；以实际库为准。已有数据库不要执行旧 SmallOKR 数据导出脚本（含 DROP 和账号记录）。

新环境可执行 alisa-user/sql/schema.sql；它不带任何种子账号或口令。

**回滚注意：** 旧 userservice 仅支持明文密码。账号升级为哈希后不能直接回滚到旧二进制；应回滚到仍支持混合密码验证的版本，或按数据库备份恢复策略回退。保留数据库备份，先在副本上验收再切流量。

## 本地构建与运行

1. 在 alisa-cloud 根目录复制 alisa-user/example.env 为 alisa-user/.env，配置现有 MySQL、Redis、Nacos、SMTP 和 JWT_SECRET。
2. 原 userservice 的环境变量名可继续使用；MAIL_STARTTLS、MAIL_SSL、MAIL_AUTH 可覆盖 SMTP 选项。默认 587 + STARTTLS；使用 465 时设置 MAIL_PORT=465、MAIL_SSL=true、MAIL_STARTTLS=false。
3. 执行 mvn clean install：先验证并安装公共库，再在 SmallOKR 构建其业务模块。
4. 从 alisa-cloud 根目录执行 java -jar packages/alisa-user-0.0.1-SNAPSHOT.jar。

不要与旧 userservice 同时使用同一 Nacos 服务名和同一数据库承接生产流量。开发验收可关闭注册，并使用副本数据库和隔离 Redis；确认后停旧实例、再切换服务和入口。

## 邮箱重置

使用 SecureRandom 六位验证码、默认五分钟有效、同一邮箱默认六十秒发送间隔。验证采用 Redis Lua 原子比较与消费，最多尝试五次，成功后不能重放。通知传输失败会清理本次验证码和发送限流键。

键前缀改为 alisa:user:reset:。切换前等旧验证码的五分钟有效期结束，避免在两套实现之间重置密码。用户服务不提供原始验证码读取 API。

## 网关业务路由

推荐使用 [Nacos 动态路由](gateway-dynamic-routes.md)，统一维护多个项目的业务路由并热更新。以下静态文件方式继续兼容；启用 Nacos 时不要重复加载静态业务路由。

公共网关的默认 application.yml / gateway-routes.yml 只配置 user-service。SmallOKR 使用自己仓库的 smallokr_server/gateway-routes.yml，其他项目也提供自己的完整路由列表。

独立启动网关时设置 SPRING_CONFIG_ADDITIONAL_LOCATION=file:/绝对路径/routes.yml；Compose 通过 ALISA_GATEWAY_ROUTES_FILE 选择宿主机文件并挂载到 /app/config/routes.yml，默认使用公共 gateway-routes.yml。例如在 alisa-cloud 根目录设置 ALISA_GATEWAY_ROUTES_FILE=../SmallOKR/smallokr_server/gateway-routes.yml 即可接入 SmallOKR。Spring 的路由列表覆盖而非追加，接入文件必须包含 user-service 路由。

examples/gateway/legacy-routes.yml 保留原网关全部业务路径，仅供现有 SmallOKR/community/transfer 部署切换时使用，不会自动加载。升级现有网关前必须选择并配置路由文件，否则只会暴露公共账号接口。

公开路径通过 alisa.gateway.auth.public-paths 配置。若覆盖列表，应显式包含需要保留的账号公开路径。网关会清除客户端自带的 X-User-Header / X-Authenticated，再从经过验签的 JWT 创建下游身份。

## Docker / Kubernetes 切换

- make build-user 或 mvn -pl alisa-user -am package 构建服务，Dockerfile 为 docker/alisa-user.Dockerfile。
- alisa-cloud Compose 管理网关和账号服务；SmallOKR Compose 只管理业务服务。
- 两个 Compose 使用预先创建的共享 my_network 网络；先执行 docker network create my_network。
- Compose 环境文件使用 alisa-user.env / alisa-gateway.env，不提交真实凭证。
- deploy/kubernetes/alisa-user.yaml 是待审阅的部署示例，需要已有 alisa 命名空间、正确镜像和名为 alisa-user-env 的 Secret。Service 对外 8080，容器 8084。
- deploy/kubernetes/user-route.yaml 对应现有 alisa.docker.local / traefik-gateway 示例，将 /api/v1/user 转发到 alisa-user:8080；应用前确认集群 Gateway 跨命名空间引用策略。

本轮只完成代码迁移、构建和测试，不自动推镜像、应用 Kubernetes 清单、修改生产路由或重启旧用户服务。
