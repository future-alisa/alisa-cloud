# alisa-cloud

基于 Java 17、Spring Boot 和 Spring Cloud Alibaba 的公共基础能力，供多个 Java 业务项目复用。

## 模块

- alisa-common：公共响应、JWT 与用户上下文，供业务服务依赖。
- alisa-gateway：网关、验签和身份传递；业务路由由接入项目配置。
- alisa-user：统一账号、登录、密码修改与邮箱找回，注册名保留 user-service。
- alisa-authorization：提供者中立的资源授权模块，包含默认本地 RBAC/JDBC 实现；按配置由 alisa-user 承载接口。
- alisa-notification：可替换的邮件发送接口与 SMTP 自动配置，无独立进程。

目标、待办、社区等业务功能归各自项目，不放入公共模块。

账号认证通过 IdentityProvider 扩展，资源授权通过 AuthorizationProvider 扩展。新增适配器以 Spring Bean 注册，不修改核心厂商分支；绑定依赖稳定外部身份，不按邮箱自动合并账号。详见 [统一提供者架构与接入](docs/provider-architecture.md)。

## 构建与账号服务

复制 alisa-user/example.env 为 alisa-user/.env，填写现有基础设施配置和与网关一致的 JWT_SECRET。
在仓库根目录运行 mvn clean install，再执行 java -jar packages/alisa-user-0.0.1-SNAPSHOT.jar。

网关默认仅包含公共账号路由；推荐通过 [Nacos 动态路由](docs/gateway-dynamic-routes.md) 同时接入 SmallOKR、ScienceCommunity，发布配置即可热更新。也可继续使用完整静态路由文件。详见 [用户服务迁移与项目接入](docs/user-service-migration.md)。

- 向导  
  git clone https://github.com/future-alisa/alisa-cloud.git
  用 ide 打开 alisa-cloud 项目，之后将 alisa-gateway 下 example.env 内容复制到 .env 并修改里面的内容，最后 mvn clean install，然后运行即可。
