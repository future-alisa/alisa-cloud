# Nacos 动态网关路由

网关保留公共账号路由；SmallOKR、ScienceCommunity 的业务路由由一份 Nacos 配置维护。服务实例仍通过 Nacos Discovery 发现，路由发布后无需重新构建或重启网关。

## 首次接入

1. 在现有 Nacos 控制台选择与网关 `NACOS_NAMESPACE` 相同的命名空间（这里填写 namespace ID；public 通常留空）。创建配置：Data ID 为 `alisa-gateway-routes.yaml`，Group 为 `ALISA_GATEWAY`，格式 YAML。
2. 将 [完整业务路由示例](../examples/gateway/nacos-routes.yaml) 的内容发布到该配置。它包含 SmallOKR 三个服务，以及 ScienceCommunity 的社区、用户转发 API、WebSocket 和 SockJS 路由。无需添加公共 `user-service`。
3. 网关运行环境增加 `GATEWAY_NACOS_ROUTES_ENABLED=true`。继续使用原有 `NACOS_SERVER_ADDR`、`NACOS_NAMESPACE`、`NACOS_USERNAME`、`NACOS_PASSWORD`；账号需具备该配置的读取权限。
4. 构建并部署更新后的网关。Docker Compose 使用根目录的 `alisa-gateway.env`；IDE/本地 JAR 使用环境变量或 `alisa-gateway/.env`。首次需要重启网关以启用订阅。

可用 `GATEWAY_NACOS_ROUTES_DATA_ID` 和 `GATEWAY_NACOS_ROUTES_GROUP` 覆盖默认配置标识，隔离开发、测试和生产。

启用动态路由时，取消原来指向 SmallOKR 或 legacy 路由文件的 `ALISA_GATEWAY_ROUTES_FILE` / `SPRING_CONFIG_ADDITIONAL_LOCATION` 配置。Compose 默认挂载的根目录 `gateway-routes.yml` 仅含账号路由，可以保留。不要在本地和 Nacos 重复定义相同业务路由 ID，否则远程配置会被拒绝。

## 日常发布

直接在 Nacos 修改并发布同一份 YAML。所有订阅该 namespace/group/data ID 的网关实例分别接收更新。日志 `Loaded N business routes from Nacos ...` 表示该实例已应用；各实例更新有传播延迟，不提供跨实例同时切换的事务。

更新是一份业务路由列表的完整替换：删除列表项会移除该路由。显式发布 `spring.cloud.gateway.routes: []` 可以清空全部动态业务路由，公共账号路由仍保留。不要删除整个配置来清空路由。

网关先解析、检查重复 ID 并编译全部谓词和过滤器，成功后替换快照并刷新 Gateway 缓存。空配置、格式错误、未知谓词/过滤器或重复 ID 会拒绝更新，保留当前进程上一份有效快照并记录错误。启动时无法取得有效配置则启动失败，避免只启动账号路由却误以为业务已接入。Nacos SDK 自身的本地缓存行为仍适用；进程内上一份快照不是独立持久化备份。

回滚在 Nacos 配置历史中恢复上一个有效版本并发布即可。配置可用不代表后端健康，请在发布后验证实际接口和服务注册情况。

## 路径与鉴权

- `/target-service/api/v1/**`、`/api/v1/todo/**`、`/api/v1/trace/**` 保持 SmallOKR 现有路径。
- `/community/**` 保留完整前缀，匹配 community-service 的 `/community` servlet context。
- `/api/users/**` 转发给 transfer-service。
- `/ws/**` 的 Upgrade 请求通过 `lb:ws://transfer-service`，其余 SockJS HTTP 请求通过 `lb://transfer-service`。
- 公共账号和 OAuth 路由、CORS、全局鉴权继续由本地网关配置负责；Nacos 仅用于业务路由。
- SmallOKR 示例沿用项目原有 `delegated-ready: true`；仅应在后端已具备独立 Bearer 校验时启用。ScienceCommunity 示例不增加该标记，也不扩大匿名白名单。WebSocket 仍受现有网关鉴权约束，transfer-service 的 STOMP 身份处理不由本次路由配置修改。

## 验证

执行 `bash mvnw -pl alisa-gateway -am test`。测试覆盖完整示例编译、错误更新保留旧路由、更新和清空业务路由、公共路由 ID 冲突、初始配置缺失以及监听释放。不依赖实际 Nacos。

接入真实环境后，确认日志和各业务接口可达，再在 Nacos 发布一次可回滚的路由修改，检查所有网关实例均收到更新。本次代码整理不会自动发布 Nacos 配置或重启运行中的网关。

## 现有 Argo CD 部署

`alisa-infra` 应用跟踪 `codex/alisa-infra-argocd` 分支的 `deploy/kubernetes/alisa-infra*.yaml`。执行完整同步时，PreSync Job 先拉取固定源码提交并运行 Maven 测试及打包，再使用集群已有的 `alisa-registry` Secret 推送带源码版本标签的网关镜像。下一阶段 Job 使用相同镜像，通过 Nacos Java SDK 初始化路由；已有非空配置不会被覆盖。随后才更新网关 Deployment。

构建任务不依赖 SSH 或 GitHub Runner；需要集群能够访问 GitHub、Maven Central 和容器镜像源。构建或 Nacos 初始化失败会阻止进入 Deployment 更新阶段。后续镜像发布需同步更新构建任务的 `SOURCE_REVISION`、目标镜像标签和网关 Deployment 镜像。日常业务路由调整只在 Nacos 发布即可，无需触发此构建任务。

现有集群的构建 Pod 使用外部 DNS；运行时基础层取自已有私有 Registry 网关镜像。`alisa-gateway-build-cache` 保留源码工作区及 Maven 缓存以供重试复用，需要集群默认 StorageClass 提供 2 GiB 的 ReadWriteOnce 卷。Job 最长等待一小时。

初始化 Job 最长等待 30 分钟，以覆盖首次镜像拉取时间。本次构建产物已发布到 `alisa-registry.cpolar.top/alisa-gateway:nacos-a9820abfc060`，镜像摘要为 `sha256:d8eff6fa03f84953cc01bcda5e5c294e8c886b0292720309eb204ec4a92dac0b`。

首次部署时 repo-server 访问 GitHub 曾出现域名解析/连接超时，临时使用 Argo CD 官方本地清单同步接口完成发布。后续检查已验证正常 Git 分支解析和同步预演成功，并通过正常 Git 同步补齐资源跟踪注解；当前不再依赖本地清单同步。构建任务继续通过 GitHub 官方固定提交归档获取源码。

仅更新已有 Deployment、Service、ConfigMap、Namespace 或 HTTPRoute 时，可选择这些资源进行同步，跳过 PreSync 构建和初始化 Hook。完整同步会执行发布流程；日常业务路由调整直接在 Nacos 发布。
