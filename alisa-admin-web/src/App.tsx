import { readInvitation } from "./invitationLink";
import { sharedSignInUrl } from "./state";
import { Fragment, useEffect, useState, type FormEvent, type ReactNode } from "react";
import {
  Link,
  NavLink,
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";
import {
  AppWindow,
  ArrowRight,
  Building2,
  ChevronDown,
  KeyRound,
  LayoutDashboard,
  LogOut,
  Menu,
  ScrollText,
  ShieldCheck,
  Users,
  X,
} from "lucide-react";
import {
  SessionProvider,
  TenantProvider,
  useSession,
  useTenants,
} from "./state";
import { Alert, Button, Empty, ErrorState, Loading, ToastProvider, useToast } from "./ui";
import { Overview, TenantsPage, SettingsPage } from "./pages/Tenants";
import MembersPage from "./pages/Members";
import ApplicationsPage from "./pages/Applications";
import PermissionsPage from "./pages/Permissions";
import AuditPage from "./pages/Audit";
import Organizations, { ActivateOrganizationAccount } from "./pages/Organizations";
import { ChangePassword, ResetPassword } from "./pages/AccountPassword";
import PortalServices from "./pages/PortalServices";
import WorkspacePicker from "./WorkspacePicker";
const nav = [
  { to: "/", label: "总览", icon: LayoutDashboard },
  { to: "/organizations", label: "组织与用户", icon: Building2, group: "组织" },
  { to: "/tenants", label: "租户管理", icon: Building2, group: "租户" },
  { to: "/members", label: "成员管理", icon: Users },
  { to: "/applications", label: "应用与审批", icon: AppWindow },
  { to: "/permissions", label: "资源权限", icon: KeyRound },
  { to: "/audit", label: "审计记录", icon: ScrollText },
  { to: "/portal-services", label: "门户服务目录", icon: AppWindow, group: "门户" },
];
function Brand() {
  return (
    <div className="brand">
      <div className="brand-symbol">
        a<span />
      </div>
      <div>
        <strong>alisa</strong>
        <span>Admin Web</span>
      </div>
    </div>
  );
}
const pendingInvitationKey = "alisa.admin.pending-invitation";

function Login() {
  const { pathname, hash } = useLocation();
  const { login } = useSession();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    setPending(true);
    try {
      await login(username.trim(), password);
    } catch (error) {
      setError(error instanceof Error ? error.message : "无法登录");
    } finally {
      setPending(false);
    }
  }
  return (
    <div className="login-layout">
      <section className="login-story">
        <Brand />
        <div className="login-copy">
          <div className="login-pill">
            <ShieldCheck size={15} /> Identity & Access
          </div>
          <h1>
            让每一份访问，
            <br />
            都有清晰的边界。
          </h1>
          <p>
            在一个工作台管理租户、成员与应用，
            <br />
            让授权清晰，让协作有序。
          </p>
          <div className="login-illustration">
            <div className="identity-orbit orbit-one" />
            <div className="identity-orbit orbit-two" />
            <div className="identity-center">
              <ShieldCheck size={48} strokeWidth={1.4} />
            </div>
            <div className="orbit-card card-user">
              <Users size={20} />
              <span>成员身份</span>
              <i />
            </div>
            <div className="orbit-card card-app">
              <AppWindow size={20} />
              <span>应用授权</span>
              <i />
            </div>
            <div className="orbit-card card-tenant">
              <Building2 size={20} />
              <span>租户空间</span>
              <i />
            </div>
          </div>
        </div>
        <footer>
          Alisa Cloud <span>统一身份与访问管理</span>
        </footer>
      </section>
      <section className="login-panel">
        <div className="login-form">
          <div className="eyebrow">WELCOME BACK</div>
          <h2>登录管理控制台</h2>
          <p>使用你的 Alisa 账号继续。</p>
          {sharedSignInUrl && <a className="button primary" href={sharedSignInUrl}
            onClick={() => {
              if (pathname === "/tenants" && readInvitation(hash)) {
                sessionStorage.setItem(pendingInvitationKey, hash);
              }
              sessionStorage.removeItem("alisa.admin.signed-out");
            }}>
            使用 Alisa 账号继续
          </a>}
          <form onSubmit={submit}>
            <label>
              账号
              <input
                autoFocus
                required
                autoComplete="username"
                placeholder="输入用户名"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
              />
            </label>
            <label>
              密码
              <input
                required
                type="password"
                autoComplete="current-password"
                placeholder="输入密码"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </label>
            <Alert>{error}</Alert>
            <Button type="submit" variant="primary" loading={pending}>
              登录控制台 <ArrowRight size={17} />
            </Button>
          </form>
          <Link to="/reset-password">忘记密码？</Link>
          <div className="login-footnote">
            <ShieldCheck size={16} />
            请使用已注册的账号，权限由所属租户管理。
          </div>
        </div>
        <div className="login-copyright">
          © {new Date().getFullYear()} Alisa Cloud
        </div>
      </section>
    </div>
  );
}
function TenantBoundary({ children }: { children: ReactNode }) {
  const { selected, loading, error, reload } = useTenants();
  if (loading) return <Loading />;
  if (error && !selected) return <ErrorState message={error} retry={reload} />;
  if (!selected)
    return (
      <Empty
        title="先选择一个租户"
        description="创建或加入租户后，即可管理成员、应用和资源权限。"
        action={
          <Link className="button primary" to="/tenants">
            前往租户管理 <ArrowRight size={16} />
          </Link>
        }
      />
    );
  if (!selected.enabled)
    return (
      <Empty
        title="这个租户已停用"
        description="停用期间无法访问业务资源，所有者可以在设置中重新启用。"
        action={
          <Link className="button secondary" to="/settings">
            查看租户设置
          </Link>
        }
      />
    );
  return children;
}
function Layout() {
  const { session, logout } = useSession();
  const { tenants, selected, selectedId, select } = useTenants();
  const location = useLocation();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [changingPassword, setChangingPassword] = useState(false);
  const toast = useToast();
  const current = nav.find((item) => item.to === location.pathname) || nav[0];
  return (
    <div className="shell">
      {changingPassword && session && <ChangePassword username={session.username}
        onClose={() => setChangingPassword(false)} onChanged={() => {
          toast("密码已修改，请用新密码重新登录");
          logout();
        }} />}
      <button
        className={`sidebar-scrim ${mobileOpen ? "visible" : ""}`}
        aria-label="关闭导航"
        onClick={() => setMobileOpen(false)}
      />
      <aside className={`sidebar ${mobileOpen ? "open" : ""}`}>
        <Brand />
        <div className="sidebar-caption">管理工作台</div>
        <nav>
          {nav.map((item) => (
            <Fragment key={item.to}>
              {item.group && <span className="nav-group">{item.group}</span>}
            <NavLink
              end={item.to === "/"}
              to={item.to}
              onClick={() => setMobileOpen(false)}
            >
              <item.icon size={19} />
              <span>{item.label}</span>
              {item.to === "/applications" && (
                <span className="nav-small">OAuth</span>
              )}
            </NavLink>
            </Fragment>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <ShieldCheck size={20} />
          <div>
            <strong>边界清晰，访问有序</strong>
            <span>Alisa Cloud 身份基础设施</span>
          </div>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <div className="topbar-left">
            <button
              className="icon-button mobile-menu"
              aria-label="展开导航"
              onClick={() => setMobileOpen(true)}
            >
              <Menu size={20} />
            </button>
            <span className="breadcrumb-root">工作台</span>
            <span className="breadcrumb-slash">/</span>
            <strong>{location.pathname === "/settings" ? "租户设置" : current.label}</strong>
          </div>
          <div className="topbar-right">
            {current.to !== "/organizations" && current.to !== "/portal-services" && <>
            <WorkspacePicker label="切换租户" kind="tenant" items={tenants}
              selectedId={selected?.id || selectedId} onSelect={select} />
            <div className="topbar-divider" />
            </>}
            <details className="account-menu" onBlur={event => {
              if (!event.currentTarget.contains(event.relatedTarget)) event.currentTarget.open = false;
            }} onKeyDown={event => {
              if (event.key === "Escape" && event.currentTarget.open) {
                event.currentTarget.open = false;
                event.currentTarget.querySelector("summary")?.focus();
              }
            }}>
              <summary className="account" aria-label={`账号菜单，${session?.username}`}>
                <span className="avatar">{session?.username.slice(0, 1).toUpperCase()}</span>
                <span className="account-name">{session?.username}</span>
                <ChevronDown size={13} />
              </summary>
              <div className="account-menu-panel">
                <div className="account-menu-heading"><strong>{session?.username}</strong><span>Alisa 账号</span></div>
                <Button variant="ghost" onClick={event => {
                  const menu = event.currentTarget.closest("details");
                  menu?.querySelector("summary")?.focus();
                  if (menu) menu.open = false;
                  setChangingPassword(true);
                }}><KeyRound size={15} />修改密码</Button>
                <Button variant="ghost" onClick={logout}><LogOut size={15} />退出登录</Button>
              </div>
            </details>
          </div>
        </header>
        <main className="main-content">
          <Routes>
            <Route path="/" element={<Overview key={selectedId} />} />
            <Route path="/portal-services" element={<PortalServices />} />
            <Route path="/organizations" element={<Organizations />} />
            <Route path="/tenants" element={<TenantsPage />} />
            <Route
              path="/settings"
              element={<SettingsPage key={selectedId} />}
            />
            <Route
              path="/members"
              element={
                <TenantBoundary>
                  <MembersPage key={selectedId} />
                </TenantBoundary>
              }
            />
            <Route
              path="/applications"
              element={
                <TenantBoundary>
                  <ApplicationsPage key={selectedId} />
                </TenantBoundary>
              }
            />
            <Route
              path="/permissions"
              element={
                <TenantBoundary>
                  <PermissionsPage key={selectedId} />
                </TenantBoundary>
              }
            />
            <Route
              path="/audit"
              element={
                <TenantBoundary>
                  <AuditPage key={selectedId} />
                </TenantBoundary>
              }
            />
            <Route
              path="*"
              element={
                <Empty
                  title="页面不存在"
                  description="这个地址可能已经变更，请返回工作台。"
                  action={
                    <Link className="button primary" to="/">
                      返回总览
                    </Link>
                  }
                />
              }
            />
          </Routes>
        </main>
        <footer className="workspace-footer">
          <span>Alisa Admin Web</span>
          <span>身份 · 应用 · 资源</span>
        </footer>
      </div>
    </div>
  );
}
export default function App() {
  const { session, checkingSession } = useSession();
  const { pathname, hash } = useLocation();
  const navigate = useNavigate();
  useEffect(() => {
    if (!session) return;
    const pending = sessionStorage.getItem(pendingInvitationKey);
    if (!pending) return;
    sessionStorage.removeItem(pendingInvitationKey);
    if (!readInvitation(hash) && readInvitation(pending)) {
      navigate({ pathname: "/tenants", hash: pending }, { replace: true });
    }
  }, [session, hash, navigate]);
  if (checkingSession) return <main role="status">正在确认 Alisa 登录状态…</main>;
  return (
    <ToastProvider>
      <Routes>
        <Route path="/reset-password" element={<ResetPassword />} />
        <Route path="/activate-account" element={<ActivateOrganizationAccount />} />
        <Route
          path="/login"
          element={session ? <Navigate to="/" replace /> : <Login />}
        />
        <Route
          path="/*"
          element={
            session ? (
              <TenantProvider key={session.userId}>
                <Layout />
              </TenantProvider>
            ) : pathname === "/tenants" && readInvitation(hash) ? (
              <Login />
            ) : (
              <Navigate to="/login" replace />
            )
          }
        />
      </Routes>
    </ToastProvider>
  );
}
