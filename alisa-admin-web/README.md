# alisa-admin-web

React + TypeScript + Vite 管理控制台，独立于 Maven 模块构建。调用 alisa-user 已有的 Bearer 管理 API，不新增管理后端。

## 本机运行

要求 Node.js 22.12+ 和 pnpm 10.18.1。依赖通过 pnpm-lock.yaml 锁定。

```powershell
cd D:\WorkSpace\Java\alisa-cloud\alisa-admin-web
pnpm install --frozen-lockfile
pnpm dev
```

访问 http://localhost:5173 。开发代理默认转发 `/api/**` 到独立测试实例 `http://localhost:8085`，不经过 Gateway。复制 `.env.example` 为 `.env.local` 可设置 `ALISA_API_TARGET`，例如直连 8084 或网关 8100。本机测试账号由测试服务提供，不包含在前端源码中。

## 页面与权限

- `/` 总览：实时租户、成员、应用和待审批数量。
- `/tenants`：创建、切换租户，以及接受定向邀请。
- `/members`：成员列表、邀请生成/撤销、角色调整、退出与所有权转移。
- `/applications`：委托/后台应用登记、动态功能目录、部分范围审批、密钥轮换和停用；管理员可创建资源服务器验证凭据。
- `/permissions`：为用户或后台应用授予具体资源权限，支持撤销。
- `/audit`：审计记录、详情、当前页搜索和稳定游标分页。
- `/settings`：租户改名、停用/恢复，以及复制自己的用户 ID。

角色只用于界面展示，所有操作仍由服务器逐次鉴权。403 显示权限说明，不强制退出；401 清理会话并返回登录页。页面发出的旧租户查询会中止，并忽略过期响应。

浏览器仅在 sessionStorage 保存第一方登录 token 和账号标识，退出时清除；不保存密码、Client Secret 或邀请凭据。一次性凭据只留在当前弹窗内，关闭或刷新后无法找回。生产环境应使用 HTTPS，做好 CSP 和同源部署；若改用 HttpOnly 会话，需要配套后端会话及 CSRF 设计，不能只改变前端存储方式。

## 构建与测试

```powershell
pnpm test
pnpm build
pnpm preview
```

单测覆盖登录、租户创建、非管理员界面、401/403、无响应体的 API，以及旧租户响应隔离。生产静态文件输出到 dist/。Vite 仅用于开发和本机预览，生产请由 Nginx 等静态服务器托管。

同源部署示例见 `deploy/nginx.conf.example`。生产 API 可直连 alisa-user，或转发至已有 Gateway；浏览器不能持有资源服务器验证凭据。OAuth 登录和用户同意页面仍由 alisa-user 提供，控制台不会替代授权服务器的协议端点。

新增后端接口：`GET /api/v1/user/tenants/{tenant}/catalog` 返回当前租户成员可见的业务功能目录；应用列表另外返回 applicationId，便于准确展示和授权。需要部署本次 alisa-user 版本。
