import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  AppWindow,
  ArrowRight,
  Building2,
  CheckCircle2,
  Clock3,
  KeyRound,
  Mail,
  Plus,
  Search,
  ShieldCheck,
  Users,
} from "lucide-react";
import { tenantPath } from "../api";
import { isAdmin, useAction, useQuery, useSession, useTenants } from "../state";
import type { Client, Member } from "../types";
import {
  Alert,
  Badge,
  Button,
  ClientStatus,
  ConfirmDialog,
  Empty,
  ErrorState,
  FormFooter,
  Id,
  Loading,
  Modal,
  PageTitle,
  RoleBadge,
  useToast,
} from "../ui";
export function TenantsPage() {
  const { tenants, selected, select, loading, error, reload } = useTenants();
  const [modal, setModal] = useState<"create" | "accept" | null>(null);
  const [search, setSearch] = useState("");
  const [scope, setScope] = useState<"mine" | "platform">("mine");
  const platform = useQuery<PlatformTenantList>(platformTenantsPath);
  const showPlatform = scope === "platform" && platform.data?.allowed;
  const filtered = tenants.filter((t) =>
    `${t.name} ${t.id}`.toLowerCase().includes(search.toLowerCase())
  );
  return (
    <>
      <PageTitle
        eyebrow="WORKSPACES"
        title="租户管理"
        description="查找所属租户，管理成员、应用和租户设置。"
        actions={
          <>
            <Button
              icon={<Mail size={16} />}
              onClick={() => setModal("accept")}
            >
              接受邀请
            </Button>
            <Button
              variant="primary"
              icon={<Plus size={17} />}
              onClick={() => setModal("create")}
            >
              创建租户
            </Button>
          </>
        }
      />
      <div className="section-toolbar tenant-list-toolbar">
        <div className="tabs" role="group" aria-label="租户范围">
          <button type="button" aria-pressed={!showPlatform} className={!showPlatform ? "active" : ""}
            onClick={() => { setScope("mine"); setSearch(""); }}>
            我的租户 <span>{tenants.length}</span>
          </button>
          {platform.data?.allowed && <button type="button" aria-pressed={!!showPlatform} className={showPlatform ? "active" : ""}
            onClick={() => { setScope("platform"); setSearch(""); }}>
            平台租户 <span>{platform.data.tenants.length}</span>
          </button>}
        </div>
        <div className="search">
          <Search size={17} />
          <input
            aria-label="搜索租户"
            placeholder="搜索租户名称或 ID"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>
      {showPlatform ? <PlatformTenantManagement platform={platform} search={search} /> : loading ? (
        <Loading />
      ) : error ? (
        <ErrorState message={error} retry={reload} />
      ) : !tenants.length ? (
        <div className="panel">
          <Empty
            icon={<Building2 size={27} />}
            title="从你的第一个租户开始"
            description="创建团队空间，或使用管理员发给你的邀请加入已有租户。"
            action={
              <Button
                variant="primary"
                icon={<Plus size={16} />}
                onClick={() => setModal("create")}
              >
                创建租户
              </Button>
            }
          />
        </div>
      ) : !filtered.length ? (
        <Empty title="没有匹配的租户" description="试试其他名称或租户 ID。" />
      ) : (
        <div className="tenant-grid">
          {filtered.map((tenant) => (
            <article
              key={tenant.id}
              className={`tenant-card ${
                selected?.id === tenant.id ? "selected" : ""
              }`}
            >
              <div className="tenant-card-top">
                <div className="tenant-card-identity">
                  <span className="tile-icon"><Building2 size={19} aria-hidden="true" /></span>
                  <div><h2>{tenant.name}</h2><Id value={tenant.id} /></div>
                </div>
                <Badge tone={tenant.enabled ? "green" : "neutral"}>
                  {tenant.enabled ? "已启用" : "已停用"}
                </Badge>
              </div>
              <div className="tenant-card-footer">
                <RoleBadge role={tenant.role} />
                <nav className="tenant-card-actions" aria-label={`${tenant.name}的操作`}>
                  {tenant.enabled && <>
                    <Link className="button ghost" to="/members" onClick={() => select(tenant.id)}>
                      {isAdmin(tenant) ? "管理成员" : "查看成员"}
                    </Link>
                    <Link className="button ghost" to="/applications" onClick={() => select(tenant.id)}>接入应用</Link>
                  </>}
                  <Link className="button ghost" to="/settings" onClick={() => select(tenant.id)}>
                    租户设置
                  </Link>
                </nav>
              </div>
            </article>
          ))}
        </div>
      )}
      {modal && <TenantDialog kind={modal} onClose={() => setModal(null)} />}
    </>
  );
}
type PlatformTenant = { id: string; name: string; enabled: boolean };
type PlatformTenantList = { allowed: boolean; tenants: PlatformTenant[] };
const platformTenantsPath = "/api/v1/user/tenants/platform";

function PlatformTenantManagement({ platform, search }: {
  platform: ReturnType<typeof useQuery<PlatformTenantList>>;
  search: string;
}) {
  const { api } = useSession();
  const { reload } = useTenants();
  const toast = useToast();
  const [statusTarget, setStatusTarget] = useState<PlatformTenant>();
  const [deleteTarget, setDeleteTarget] = useState<PlatformTenant>();
  const [confirmationName, setConfirmationName] = useState("");
  const action = useAction();

  if (!platform.data?.allowed) return null;
  const term = search.trim().toLowerCase();
  const filtered = platform.data.tenants.filter(tenant =>
    `${tenant.name} ${tenant.id}`.toLowerCase().includes(term)
  );
  const refresh = () => {
    platform.reload();
    reload();
  };

  return <section className="panel platform-tenants">
    <div className="panel-heading">
      <div><h2>平台租户管理</h2><p>查看平台上的租户，并管理其运行状态。</p></div>
    </div>
    <Alert>{platform.error}</Alert>
    <div className="table-scroll">
      <table>
        <thead><tr><th>租户</th><th>状态</th><th className="align-right">操作</th></tr></thead>
        <tbody>{filtered.map(tenant => (
          <tr key={tenant.id}>
            <td><strong>{tenant.name}</strong><br /><Id value={tenant.id} /></td>
            <td><Badge tone={tenant.enabled ? "green" : "neutral"}>{tenant.enabled ? "已启用" : "已停用"}</Badge></td>
            <td><div className="row-actions">
              <Button variant="ghost" onClick={() => setStatusTarget(tenant)}>{tenant.enabled ? "停用" : "重新启用"}</Button>
              {!tenant.enabled && <Button variant="ghost" className="danger-text" onClick={() => {
                setConfirmationName("");
                action.setError("");
                setDeleteTarget(tenant);
              }}>删除租户</Button>}
            </div></td>
          </tr>
        ))}</tbody>
      </table>
    </div>
    {!filtered.length && <Empty title={term ? "没有匹配的租户" : "暂无平台租户"}
      description={term ? "试试其他租户名称或 ID。" : "创建租户后，它会出现在这里。"} />}
    {statusTarget && <ConfirmDialog
      title={statusTarget.enabled ? "停用租户？" : "重新启用租户？"}
      description={statusTarget.enabled
        ? `停用「${statusTarget.name}」后，成员无法进入租户，现有 OAuth 授权和待接受邀请会失效。`
        : `重新启用「${statusTarget.name}」后，成员可以重新进入租户。`}
      danger={statusTarget.enabled}
      onClose={() => setStatusTarget(undefined)}
      onConfirm={async () => {
        await api(`${platformTenantsPath}/${encodeURIComponent(statusTarget.id)}/status`, {
          method: "PUT",
          body: { enabled: !statusTarget.enabled },
        });
        refresh();
        toast(statusTarget.enabled ? "租户已停用" : "租户已启用");
      }}
    />}
    {deleteTarget && <Modal title="永久删除租户"
      description={`删除「${deleteTarget.name}」会清除成员关系、接入凭据和租户权限。操作不可恢复，审计记录会保留。`}
      busy={action.pending} onClose={() => setDeleteTarget(undefined)}>
      <form onSubmit={event => {
        event.preventDefault();
        if (confirmationName !== deleteTarget.name) return;
        void action.run(async () => {
          await api(`${platformTenantsPath}/${encodeURIComponent(deleteTarget.id)}?name=${encodeURIComponent(confirmationName)}`, {
            method: "DELETE",
          });
          setDeleteTarget(undefined);
          refresh();
          toast("租户已删除");
        });
      }}>
        <label>输入租户名称确认
          <input autoFocus required value={confirmationName} onChange={event => setConfirmationName(event.target.value)} />
        </label>
        <Alert>{action.error}</Alert>
        <div className="form-footer">
          <Button type="button" disabled={action.pending} onClick={() => setDeleteTarget(undefined)}>取消</Button>
          <Button type="submit" variant="danger" loading={action.pending}
            disabled={confirmationName !== deleteTarget.name}>永久删除</Button>
        </div>
      </form>
    </Modal>}
  </section>;
}

function TenantDialog({
  kind,
  onClose,
}: {
  kind: "create" | "accept";
  onClose: () => void;
}) {
  const [name, setName] = useState("");
  const [tenant, setTenant] = useState("");
  const [invitationId, setInvitationId] = useState("");
  const [token, setToken] = useState("");
  const { api } = useSession();
  const { reload, select } = useTenants();
  const action = useAction();
  const toast = useToast();
  const navigate = useNavigate();
  async function submit(event: FormEvent) {
    event.preventDefault();
    await action.run(async () => {
      let id = tenant.trim();
      if (kind === "create") {
        const result = await api<{ tenantId: string }>("/api/v1/user/tenants", {
          method: "POST",
          body: { name: name.trim() },
        });
        id = result.tenantId;
      } else
        await api(tenantPath(id, "/invitations/accept"), {
          method: "POST",
          body: { invitationId: invitationId.trim(), token: token.trim() },
        });
      reload();
      select(id);
      onClose();
      toast(kind === "create" ? "租户已创建" : "已加入租户");
      navigate("/");
    });
  }
  return (
    <Modal
      title={kind === "create" ? "创建租户" : "接受租户邀请"}
      description={
        kind === "create"
          ? "租户拥有独立的成员、应用、授权策略和审计记录。"
          : "填写管理员提供的邀请信息，邀请只能由指定账号接受。"
      }
      onClose={onClose}
      busy={action.pending}
    >
      <form onSubmit={submit}>
        {kind === "create" ? (
          <label>
            租户名称
            <input
              autoFocus
              required
              maxLength={100}
              placeholder="例如：产品研发团队"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
            <small>你将成为这个租户的所有者。</small>
          </label>
        ) : (
          <>
            <label>
              租户 ID
              <input
                autoFocus
                required
                value={tenant}
                onChange={(e) => setTenant(e.target.value)}
              />
            </label>
            <label>
              邀请 ID
              <input
                required
                value={invitationId}
                onChange={(e) => setInvitationId(e.target.value)}
              />
            </label>
            <label>
              邀请凭据
              <input
                required
                type="password"
                autoComplete="off"
                value={token}
                onChange={(e) => setToken(e.target.value)}
              />
            </label>
          </>
        )}
        <Alert>{action.error}</Alert>
        <FormFooter
          onClose={onClose}
          pending={action.pending}
          label={kind === "create" ? "创建租户" : "加入租户"}
        />
      </form>
    </Modal>
  );
}
export function Overview() {
  const { session } = useSession();
  const tenants = useTenants();
  const selected = tenants.selected;
  const clients = useQuery<Client[]>(
    selected?.enabled ? tenantPath(selected.id, "/applications") : null
  );
  const members = useQuery<Member[]>(
    selected?.enabled ? tenantPath(selected.id, "/members") : null
  );
  if (tenants.loading) return <Loading />;
  if (tenants.error)
    return <ErrorState message={tenants.error} retry={tenants.reload} />;
  return (
    <>
      <PageTitle
        eyebrow="OVERVIEW"
        title={`你好，${session?.username}`}
        description={
          selected
            ? `这里是「${selected.name}」的身份与访问概况。`
            : "欢迎来到 Alisa，先为你的团队建立一个独立空间。"
        }
        actions={
          <Link className="button secondary" to="/tenants">
            <Building2 size={16} />
            租户管理
          </Link>
        }
      />
      {!selected ? (
        <div className="panel">
          <Empty
            icon={<Building2 size={28} />}
            title="所有协作，从一个空间开始"
            description="建立租户后，你可以邀请成员、接入应用，并为每一次访问设置清晰的权限。"
            action={
              <Link to="/tenants" className="button primary">
                创建或加入租户 <ArrowRight size={16} />
              </Link>
            }
          />
        </div>
      ) : !selected.enabled ? (
        <div className="panel">
          <Empty
            title="当前租户已停用"
            description="现有数据和审计记录仍保留，所有者可在设置中恢复。"
            action={
              <Link to="/settings" className="button primary">
                前往设置
              </Link>
            }
          />
        </div>
      ) : (
        <>
          <div className="stats-grid">
            {[
              {
                label: "所属租户",
                to: "/tenants",
                value: tenants.tenants.length,
                icon: Building2,
                tone: "blue",
                hint: "你可以访问的团队空间",
              },
              {
                label: "租户成员",
                to: "/members",
                value: members.data?.length,
                icon: Users,
                tone: "teal",
                hint: "已加入当前租户的成员",
              },
              {
                label: isAdmin(selected) ? "接入应用" : "我的接入应用",
                to: "/applications",
                value: clients.data?.filter((a) => a.kind !== "resource")
                  .length,
                icon: AppWindow,
                tone: "violet",
                hint: "已登记的第三方与后台应用",
              },
              {
                label: "待审批应用",
                to: "/applications",
                value: clients.data?.filter(
                  (a) => a.enabled && a.approval === "PENDING"
                ).length,
                icon: Clock3,
                tone: "amber",
                hint: isAdmin(selected)
                  ? "等待管理员审核功能范围"
                  : "等待管理员审批的申请",
              },
            ].map((stat) => (
              <Link to={stat.to} className="stat-card stat-card-link" key={stat.label}>
                <div>
                  <span>{stat.label}</span>
                  <strong>{stat.value ?? "—"}</strong>
                  <small>{stat.hint}</small>
                </div>
                <div className={`stat-icon ${stat.tone}`}>
                  <stat.icon size={22} />
                </div>
              </Link>
            ))}
          </div>
          <div className="overview-grid">
            <section className="panel">
              <div className="panel-heading">
                <div>
                  <h2>接入应用</h2>
                  <p>统一管理应用的访问边界。</p>
                </div>
                <Link to="/applications" className="text-link">
                  查看全部 <ArrowRight size={14} />
                </Link>
              </div>
              {clients.loading ? (
                <Loading />
              ) : clients.error ? (
                <ErrorState message={clients.error} retry={clients.reload} />
              ) : !clients.data?.filter((a) => a.kind !== "resource").length ? (
                <Empty
                  icon={<AppWindow size={25} />}
                  title="还没有接入应用"
                  description="登记应用，选择需要的功能，再提交审批。"
                  action={
                    <Link to="/applications" className="button secondary">
                      登记应用
                    </Link>
                  }
                />
              ) : (
                <div className="overview-apps">
                  {clients.data
                    .filter((a) => a.kind !== "resource")
                    .slice(0, 4)
                    .map((client) => (
                      <Link
                        to="/applications"
                        className="overview-app"
                        key={client.clientId}
                      >
                        <div className="app-avatar">
                          <AppWindow size={20} />
                        </div>
                        <div>
                          <strong>{client.name}</strong>
                          <span>
                            {client.kind === "machine"
                              ? "后台应用"
                              : "用户委托应用"}{" "}
                            · {client.applicationId}
                          </span>
                        </div>
                        <ClientStatus
                          approval={client.approval}
                          enabled={client.enabled}
                        />
                      </Link>
                    ))}
                </div>
              )}
            </section>
            <section className="panel access-guide">
              <div className="guide-icon">
                <ShieldCheck size={28} />
              </div>
              <h2>让访问范围一目了然</h2>
              <p>应用通过审批后，还需要获得资源权限，才能访问租户内的数据。</p>
              <ol>
                <li>
                  <span>1</span>
                  <div>
                    <strong>登记接入应用</strong>
                    <small>声明需要访问的功能范围</small>
                  </div>
                </li>
                <li>
                  <span>2</span>
                  <div>
                    <strong>审核并批准范围</strong>
                    <small>只授予实际需要的功能</small>
                  </div>
                </li>
                <li>
                  <span>3</span>
                  <div>
                    <strong>配置资源权限</strong>
                    <small>明确允许访问哪些数据</small>
                  </div>
                </li>
              </ol>
              {isAdmin(selected) ? (
                <Link to="/permissions" className="text-link">
                  管理资源权限 <ArrowRight size={15} />
                </Link>
              ) : (
                <small>你的租户角色：{selected.role}</small>
              )}
            </section>
          </div>
          <section className="tenant-summary">
            <div className="tile-icon">
              <Building2 size={22} />
            </div>
            <div>
              <strong>{selected.name}</strong>
              <Id value={selected.id} />
            </div>
            <RoleBadge role={selected.role} />
            <Link to="/settings" className="text-link">
              租户设置 <ArrowRight size={15} />
            </Link>
          </section>
          <Alert>{members.error}</Alert>
        </>
      )}
    </>
  );
}
export function SettingsPage() {
  const context = useTenants();
  const { selected } = context;
  const { api, session } = useSession();
  const [name, setName] = useState(selected?.name || "");
  const [confirm, setConfirm] = useState(false);
  const action = useAction();
  const toast = useToast();
  useEffect(() => {
    setName(selected?.name || "");
  }, [selected?.name]);
  if (context.loading) return <Loading />;
  if (context.error)
    return <ErrorState message={context.error} retry={context.reload} />;
  if (!selected)
    return (
      <Empty
        title="未选择租户"
        description="请先选择要管理的租户。"
        action={
          <Link className="button primary" to="/tenants">
            选择租户
          </Link>
        }
      />
    );
  const owner = selected.role === "OWNER";
  async function save(enabled: boolean) {
    await action.run(async () => {
      await api(tenantPath(selected!.id), {
        method: "PUT",
        body: { name: name.trim(), enabled },
      });
      context.reload();
      setConfirm(false);
      toast(enabled ? "租户设置已更新" : "租户已停用");
    });
  }
  return (
    <>
      <PageTitle
        eyebrow="SETTINGS"
        title="租户设置"
        description={`管理「${selected.name}」的基本信息和运行状态。只有所有者可以修改租户设置。`}
        actions={<Link className="button secondary" to="/tenants">返回租户管理</Link>}
      />
      <section className="panel settings-panel">
        <div className="panel-heading">
          <h2>基本信息</h2>
          <RoleBadge role={selected.role} />
        </div>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void save(selected.enabled);
          }}
        >
          <label>
            租户名称
            <input
              required
              maxLength={100}
              value={name}
              disabled={!owner}
              onChange={(e) => setName(e.target.value)}
            />
          </label>
          <label>
            租户 ID
            <div className="readonly-field">
              <Id value={selected.id} full />
            </div>
          </label>
          <label>
            我的用户 ID
            <div className="readonly-field">
              <Id value={session!.userId} full />
            </div>
            <small>其他租户可以通过这个 ID 向你发送定向邀请。</small>
          </label>
          <Alert>{action.error}</Alert>
          <div className="form-footer">
            <Button
              type="submit"
              variant="primary"
              loading={action.pending}
              disabled={!owner || !name.trim()}
            >
              保存更改
            </Button>
          </div>
        </form>
      </section>
      <section className="panel settings-panel danger-zone">
        <div>
          <h2>{selected.enabled ? "停用租户" : "恢复租户"}</h2>
          <p>
            {selected.enabled
              ? "停用将撤销 OAuth 授权，并阻止业务访问。成员和数据会保留。"
              : "恢复后成员可以重新进入。已撤销的授权和旧租户会话不会自动恢复。"}
          </p>
        </div>
        <Button
          variant={selected.enabled ? "danger" : "primary"}
          disabled={!owner}
          onClick={() => setConfirm(true)}
        >
          {selected.enabled ? "停用租户" : "重新启用"}
        </Button>
      </section>
      {confirm && (
        <Modal
          title={selected.enabled ? "确认停用租户？" : "重新启用租户？"}
          description={`正在操作「${selected.name}」。${
            selected.enabled
              ? "当前授权将被撤销，成员将无法访问业务资源。"
              : "恢复后，成员可以重新获取有效会话。"
          }`}
          onClose={() => setConfirm(false)}
          busy={action.pending}
        >
          <form
            onSubmit={(e) => {
              e.preventDefault();
              void save(!selected.enabled);
            }}
          >
            <Alert>{action.error}</Alert>
            <FormFooter
              onClose={() => setConfirm(false)}
              pending={action.pending}
              label={selected.enabled ? "确认停用" : "确认启用"}
              danger={selected.enabled}
            />
          </form>
        </Modal>
      )}
    </>
  );
}
