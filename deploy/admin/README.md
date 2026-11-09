# Alisa Admin 部署

管理入口为 `https://alisa.vip.cpolar.cn/admin/`。控制台通过独立的 `alisa-admin-api` 使用 Alisa 账号登录，按租户成员与角色授权。后端沿用已有账号数据库和签名配置，关闭 Nacos 注册。OAuth 入口位于 `/admin/auth`，门户自身切换 OAuth 需单独配置客户端。

## 构建

Argo CD 的 alisa-cloud 仓库连接使用 HTTP 代理。用 `scripts/prepare-admin-build.py --revision <40 位 Git SHA> --output <临时目录>` 生成两项构建 Job，通过 Argo CD 同步生成的清单。Job 通过代理下载固定 GitHub 版本，构建和运行镜像从 `registry.kind.internal:5000` 拉取，产物也推送到该地址。构建配置见 `build/config.json`。

用构建日志中已推送的镜像摘要运行 `scripts/prepare-admin-release.py --web-digest <摘要> --api-digest <摘要> --output deploy/admin/production`，评审后合入 main。

## 发布

部署前，在 `alisa-infra` 命名空间通过受控渠道准备以下 Secret：

- `alisa-admin-registry-auth` 为 Docker 配置，仓库地址使用 `registry.kind.internal:5000`。
- `alisa-admin-bootstrap` 中的 `PORTAL_BOOTSTRAP_ADMINISTRATOR` 为指定账号的用户 ID，`AUTH_BACKUP_KEY` 为单独保管的 32 字节随机密钥的 Base64 编码。
- 集群现有的 `alisa-jwt` 和 `alisa-user-db` 由后端复用。

使用 `deploy/argocd/alisa-admin.yaml` 注册应用。同步依次创建备份存储、运行数据库迁移、启动后端与前端，最后开放路由。迁移先写入加密备份再执行新增表、列和索引的语句。备份 PVC 保留，迁移失败时检查 Job 日志与备份状态后再重试。回退时停用该应用的路由与工作负载，保留数据库与备份。

同步后检查两项 Deployment 就绪、`/admin/` 返回页面、未登录请求租户列表被拒绝、Alisa 账号能登录并读取租户列表。通过这些检查后再添加门户入口。
