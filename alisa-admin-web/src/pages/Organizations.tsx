import { ArrowUpRight, Building2, ChevronDown, KeyRound, MoreHorizontal, PauseCircle, PlayCircle, Plus, Search, Trash2 } from "lucide-react";
import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { createApi } from "../api";
import WorkspacePicker from "../WorkspacePicker";
import { useQuery, useSession, useTenants } from "../state";
import { Alert, Badge, Button, ConfirmDialog, FormFooter, Modal, PageTitle, SecretModal, useToast } from "../ui";

const root = "/api/v1/user/organizations";
type Organization = { id: string; name: string };
type Account = {
  id: string;
  username: string;
  email: string;
  userId: string | null;
  expiresAt: string | null;
  status: "PENDING" | "ACTIVE" | "SUSPENDED" | "DELETED";
  restoreUntil: string | null;
};
const operations = {
  RESET_PASSWORD: { label: "发送重置验证码", message: "向账号的注册邮箱发送密码重置验证码。用户可以在登录页选择「忘记密码」完成重置。" },
  SUSPEND: { label: "停用", message: "阻止账号登录并撤销现有授权。租户成员关系会保留。" },
  RESUME: { label: "重新启用", message: "允许账号重新登录，恢复其租户访问权限。" },
  DELETE: { label: "删除", message: "阻止登录并撤销授权。30 天内可以恢复账号及其原有成员关系。" },
  RESTORE: { label: "恢复", message: "重新启用账号，恢复其租户访问权限。用户需要重新登录。" },
} as const;
type AccountAction = keyof typeof operations;
const actionIcons = { RESET_PASSWORD: KeyRound, SUSPEND: PauseCircle, RESUME: PlayCircle, DELETE: Trash2, RESTORE: PlayCircle };
const accountStatusLabels = {
  PENDING: "待激活",
  ACTIVE: "正常",
  SUSPENDED: "已停用",
  DELETED: "已删除",
} as const;

function accountStatus(account: Account) {
  return account.status ?? (account.userId ? "ACTIVE" : "PENDING");
}

type Dialog =
  | { kind: "create" | "account" | "link" }
  | { kind: "assign"; account: Account };

export default function Organizations() {
  const { api } = useSession();
  const { tenants, select } = useTenants();
  const toast = useToast();
  const organizations = useQuery<Organization[]>(root);
  const [selected, setSelected] = useState("");
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const organization = organizations.data?.find(item => item.id === selected) ?? organizations.data?.[0];
  const initialLoading = organizations.loading && !organizations.data;
  const path = organization ? root + "/" + encodeURIComponent(organization.id) : null;
  const accounts = useQuery<Account[]>(path && path + "/accounts");
  const search = query.trim().toLowerCase();
  const visibleAccounts = accounts.data?.filter(account =>
    (statusFilter === "ALL" || accountStatus(account) === statusFilter) &&
    (account.username.toLowerCase().includes(search) || account.email.toLowerCase().includes(search))
  );
  const linked = useQuery<Organization[]>(path && path + "/tenants");
  const [dialog, setDialog] = useState<Dialog>();
  const [cancel, setCancel] = useState<Account>();
  const [operation, setOperation] = useState<{ account: Account; action: AccountAction; path: string }>();
  const [activation, setActivation] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const owned = tenants.filter(t => t.role === "OWNER" && t.enabled);
  const linkable = owned.filter(t => !linked.data?.some(item => item.id === t.id));

  function open(next: Dialog) {
    setError("");
    setDialog(next);
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!dialog || busy || (dialog.kind !== "create" && !path)) return;
    const fields = Object.fromEntries(new FormData(event.currentTarget));
    setBusy(true);
    setError("");
    try {
      switch (dialog.kind) {
        case "create": {
          const next = await api<Organization>(root, { method: "POST", body: fields });
          setSelected(next.id);
          organizations.reload();
          break;
        }
        case "account": {
          const next = await api<{ id: string; token: string }>(path + "/accounts", { method: "POST", body: fields });
          const url = new URL(import.meta.env.BASE_URL + "activate-account", window.location.origin);
          url.hash = new URLSearchParams(next).toString();
          setActivation(url.href);
          accounts.reload();
          break;
        }
        case "link":
          await api(path + "/tenants", { method: "POST", body: fields });
          linked.reload();
          toast("租户已关联");
          break;
        case "assign":
          await api(path + "/accounts/" + encodeURIComponent(dialog.account.id) + "/tenants", { method: "POST", body: fields });
          toast("已确认该账号是目标租户的成员");
          break;
      }
      setDialog(undefined);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "操作失败");
    } finally {
      setBusy(false);
    }
  }

  return <>
    <PageTitle title="组织与用户" description="管理组织的托管账号和租户访问权限。"
      actions={<Button variant={organization || initialLoading ? "secondary" : "primary"} icon={<Plus size={16} />}
        onClick={() => open({ kind: "create" })}>创建组织</Button>} />
    <Alert>{organizations.error || accounts.error || linked.error}</Alert>
    {!organizations.loading && !organizations.error && !organization && <p>创建组织后，即可开通账号或关联你拥有的租户。</p>}
    {(organization || initialLoading) && <>
      <div className="organization-overview">
        <section className="panel organization-context">
          <span className="organization-context-icon"><Building2 size={21} aria-hidden="true" /></span>
          <span className="organization-context-label">当前组织</span>
          {organization ? <WorkspacePicker label="切换组织" kind="organization" items={organizations.data ?? []}
            selectedId={organization.id} onSelect={id => {
              setSelected(id);
              setQuery("");
              setStatusFilter("ALL");
            }} /> : <span className="organization-loading-label" role="status">正在加载组织…</span>}
        </section>
        <section className="panel organization-tenants" aria-labelledby="organization-tenants-title">
          <div className="organization-card-heading">
            <h2 id="organization-tenants-title">已关联租户 {linked.data && <span className="organization-count">{linked.data.length}</span>}</h2>
            <Button onClick={() => open({ kind: "link" })} disabled={!organization || linked.loading || !linkable.length}
              icon={<Plus size={15} />}>关联租户</Button>
          </div>
          <div className="organization-tenant-links">
            {linked.data?.map(item => <Link key={item.id} className="organization-tenant-link"
              to="/members" onClick={() => select(item.id)} title="查看成员和角色">
              <Building2 size={15} aria-hidden="true" /><span>{item.name}</span><ArrowUpRight size={14} aria-hidden="true" />
            </Link>)}
            {(initialLoading || linked.loading) && <span className="muted" role="status">正在加载租户…</span>}
            {!linked.loading && linked.data?.length === 0 && <span className="muted">尚未关联租户</span>}
          </div>
        </section>
      </div>
      <section className="panel organization-accounts" aria-labelledby="organization-accounts-title">
      <div className="panel-heading account-list-heading">
        <div>
          <h2 id="organization-accounts-title">托管账号 {accounts.data && <span className="organization-count">{accounts.data.length}</span>}</h2>
          <p>管理账号状态，并为已激活的用户分配租户。</p>
        </div>
        <Button variant="primary" icon={<Plus size={16} />} disabled={!organization} onClick={() => open({ kind: "account" })}>创建托管账号</Button>
      </div>
      <div className="organization-account-toolbar">
        <div className="account-filters">
          <div className="account-search">
            <Search size={16} aria-hidden="true" />
            <input type="search" aria-label="搜索托管账号" placeholder="搜索用户名或邮箱" disabled={!organization} value={query}
              onChange={event => setQuery(event.target.value)} />
          </div>
          <div className="account-status-filter">
            <select aria-label="筛选账号状态" disabled={!organization} value={statusFilter} onChange={event => setStatusFilter(event.target.value)}>
              <option value="ALL">全部状态</option>
              {Object.entries(accountStatusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
            <ChevronDown size={14} aria-hidden="true" />
          </div>
        </div>
        {visibleAccounts && accounts.data && visibleAccounts.length !== accounts.data.length &&
          <span className="organization-filter-result" role="status">显示 {visibleAccounts.length} / {accounts.data.length} 个账号</span>}
      </div>
      <div className="table-scroll"><table className="managed-accounts">
        <thead><tr><th scope="col">账号</th><th scope="col">状态</th><th scope="col" className="align-right">操作</th></tr></thead>
        <tbody>{visibleAccounts?.map(account => {
          const status = accountStatus(account);
          const actions: AccountAction[] = status === "ACTIVE" ? ["RESET_PASSWORD", "SUSPEND", "DELETE"]
            : status === "SUSPENDED" ? ["RESUME", "DELETE"]
            : status === "DELETED" && account.restoreUntil && Date.parse(account.restoreUntil) > Date.now() ? ["RESTORE"] : [];
          const label = status === "PENDING" && account.expiresAt && Date.parse(account.expiresAt) <= Date.now()
            ? "链接已过期" : accountStatusLabels[status];
          const popoverId = `account-actions-${account.id}`;
          return <tr key={account.id}>
            <td><div className="managed-account-identity">
              <span className="avatar" aria-hidden="true">{account.username.slice(0, 1).toUpperCase()}</span>
              <div><strong>{account.username}</strong><span>{account.email}</span></div>
            </div></td>
            <td><Badge tone={status === "ACTIVE" ? "green" : status === "DELETED" ? "red" : "amber"}>{label}</Badge>
              {status === "DELETED" && account.restoreUntil && <small className="restore-deadline">恢复截止 {new Date(account.restoreUntil).toLocaleDateString()}</small>}
            </td>
            <td><div className="row-actions account-actions">
              {status === "ACTIVE" && <Button variant="ghost" disabled={!linked.data?.length}
                onClick={() => open({ kind: "assign", account })}>分配租户</Button>}
              {status === "PENDING" && <Button variant="ghost" onClick={() => setCancel(account)}>撤销链接</Button>}
              {actions.length === 1 && <Button variant="ghost"
                onClick={() => path && setOperation({ account, action: actions[0], path })}>{operations[actions[0]].label}</Button>}
              {actions.length > 1 && path && <>
                <Button type="button" variant="ghost" className="account-more" popoverTarget={popoverId}
                  aria-label={`${account.username} 的更多操作`} title="更多操作"
                  onClick={event => {
                    const menu = document.getElementById(popoverId);
                    const trigger = event.currentTarget.getBoundingClientRect();
                    if (!menu) return;
                    menu.style.setProperty("--action-right", `${Math.max(8, window.innerWidth - trigger.right)}px`);
                    menu.style.setProperty("--action-top", `${trigger.bottom + 6}px`);
                    menu.style.setProperty("--action-bottom", `${window.innerHeight - trigger.top + 6}px`);
                    menu.dataset.above = String(window.innerHeight - trigger.bottom < 136 && trigger.top > 136);
                  }}>
                  <MoreHorizontal size={18} aria-hidden="true" />
                </Button>
                <div id={popoverId} popover="auto" className="account-action-popover" role="group"
                  aria-label={`${account.username} 的账号操作`}>
                  {actions.map(action => {
                    const Icon = actionIcons[action];
                    return <Button type="button" key={action} variant="ghost"
                      className={action === "DELETE" ? "account-action-delete" : undefined}
                      onClick={event => {
                        event.currentTarget.closest<HTMLElement>("[popover]")?.hidePopover();
                        setOperation({ account, action, path });
                      }}>
                      <Icon size={16} aria-hidden="true" />{operations[action].label}
                    </Button>;
                  })}
                </div>
              </>}
            </div></td>
          </tr>;
        })}</tbody>
      </table></div>
      {(initialLoading || (accounts.loading && !accounts.data)) && <p className="organization-account-empty" role="status">正在加载组织账号…</p>}
      {!accounts.loading && visibleAccounts?.length === 0 && <div className="organization-account-empty">
        {accounts.data?.length ? "没有符合筛选条件的账号。" : "当前组织暂无托管账号。"}
      </div>}
      </section>
    </>}
    {dialog && <Modal title={{ create: "创建组织", account: "创建托管账号", link: "关联现有租户", assign: "分配租户" }[dialog.kind]}
      busy={busy} onClose={() => setDialog(undefined)}>
      <form onSubmit={save}>
        <Alert>{error}</Alert>
        {dialog.kind === "create" && <label>组织名称<input name="name" required maxLength={100} /></label>}
        {dialog.kind === "account" && <>
          <label>用户名<input name="username" required maxLength={45} /></label>
          <label>邮箱<input name="email" type="email" required maxLength={255} /></label>
          <p>开户后请把激活链接交给本人。用户验证邮箱并设置密码后才能登录。</p>
        </>}
        {(dialog.kind === "link" || dialog.kind === "assign") && <>
          {dialog.kind === "assign" && <p>将 {dialog.account.username} 作为普通成员加入所选租户。</p>}
          <label>目标租户<select name="tenantId" required defaultValue="">
            <option value="" disabled>选择租户</option>
            {(dialog.kind === "link" ? linkable : linked.data ?? []).map(t =>
              <option value={t.id} key={t.id}>{t.name}</option>)}
          </select></label>
        </>}
        <FormFooter onClose={() => setDialog(undefined)} pending={busy} label="确认" />
      </form>
    </Modal>}
    {activation && <SecretModal title="账号激活链接" values={[{ label: "激活链接", value: activation }]} onClose={() => setActivation("")} />}
    {operation && <ConfirmDialog title={operations[operation.action].label}
      description={`${operation.account.username} · ${operations[operation.action].message}`}
      label={operations[operation.action].label} danger={["DELETE", "SUSPEND"].includes(operation.action)}
      onClose={() => setOperation(undefined)} onConfirm={async () => {
        await api(operation.path + "/accounts/" + encodeURIComponent(operation.account.id) + "/actions",
          { method: "POST", body: { action: operation.action } });
        accounts.reload();
        toast(operation.action === "RESET_PASSWORD" ? "已发送密码重置验证码" : "账号状态已更新");
      }} />}
    {cancel && <ConfirmDialog title="撤销激活链接" description={`撤销 ${cancel.username} 的待激活链接。`}
      label="撤销" danger onClose={() => setCancel(undefined)} onConfirm={async () => {
        await api(path + "/accounts/" + encodeURIComponent(cancel.id), { method: "DELETE" });
        accounts.reload();
      }} />}
  </>;
}

export function ActivateOrganizationAccount() {
  const [link] = useState(() => new URLSearchParams(window.location.hash.slice(1)));
  const [pending, setPending] = useState<"code" | "activation">();
  const [sent, setSent] = useState(false);
  const [complete, setComplete] = useState(false);
  const [error, setError] = useState("");
  const id = link.get("id");
  const token = link.get("token");

  async function submit(kind: "code" | "activation", fields: Record<string, FormDataEntryValue> = {}) {
    if (!id || !token || pending) return;
    setPending(kind);
    setError("");
    try {
      await createApi("", () => {})(root + (kind === "code" ? "/activation/code" : "/activation"), {
        method: "POST", body: { id, token, ...fields },
      });
      if (kind === "code") setSent(true);
      else {
        window.history.replaceState(window.history.state, "", window.location.pathname);
        setComplete(true);
      }
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "激活请求失败");
    } finally {
      setPending(undefined);
    }
  }

  return <main className="login-panel">
    <section className="login-form">
      <h2>激活组织账号</h2>
      <Alert>{error}</Alert>
      {complete ? <><p>账号已激活，可以使用你设置的密码登录。</p><Link to="/login" className="button primary">前往登录</Link></> :
        !id || !token ? <Alert>激活链接不完整，请向组织管理员获取新链接。</Alert> :
        <form onSubmit={event => {
          event.preventDefault();
          void submit("activation", Object.fromEntries(new FormData(event.currentTarget)));
        }}>
          <p>验证码会发送到组织为你登记的邮箱。</p>
          <Button type="button" disabled={!!pending} loading={pending === "code"} onClick={() => void submit("code")}>
            {sent ? "重新发送验证码" : "发送邮箱验证码"}
          </Button>
          {sent && <p role="status">验证码已发送。</p>}
          <label>邮箱验证码<input name="code" inputMode="numeric" autoComplete="one-time-code" required pattern="[0-9]{6}" /></label>
          <label>设置密码<input name="password" type="password" autoComplete="new-password" required minLength={12} maxLength={72} /></label>
          <Button type="submit" variant="primary" disabled={!sent || !!pending} loading={pending === "activation"}>激活账号</Button>
        </form>}
    </section>
  </main>;
}
