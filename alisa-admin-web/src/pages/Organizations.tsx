import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { createApi } from "../api";
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
};
type Dialog =
  | { kind: "create" | "account" | "link" }
  | { kind: "assign"; account: Account };

export default function Organizations() {
  const { api } = useSession();
  const { tenants } = useTenants();
  const toast = useToast();
  const organizations = useQuery<Organization[]>(root);
  const [selected, setSelected] = useState("");
  const organization = organizations.data?.find(item => item.id === selected) ?? organizations.data?.[0];
  const path = organization ? root + "/" + encodeURIComponent(organization.id) : null;
  const accounts = useQuery<Account[]>(path && path + "/accounts");
  const linked = useQuery<Organization[]>(path && path + "/tenants");
  const [dialog, setDialog] = useState<Dialog>();
  const [cancel, setCancel] = useState<Account>();
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
    <PageTitle title="组织与用户" description="开通组织账号，并将已激活的账号分配到租户。"
      actions={<Button variant="primary" onClick={() => open({ kind: "create" })}>创建组织</Button>} />
    <Alert>{organizations.error || accounts.error || linked.error}</Alert>
    {organizations.loading && <p role="status">正在加载组织…</p>}
    {!organizations.loading && !organization && <p>创建组织后，即可开通账号或关联你拥有的租户。</p>}
    {organization && <section className="panel settings-panel">
      <div className="panel-heading">
        <label>当前组织<select value={organization.id} onChange={event => setSelected(event.target.value)}>
          {organizations.data?.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}
        </select></label>
        <div className="row-actions">
          <Button onClick={() => open({ kind: "link" })} disabled={!linkable.length}>关联租户</Button>
          <Button variant="primary" onClick={() => open({ kind: "account" })}>创建托管账号</Button>
        </div>
      </div>
      <div className="panel-heading">
        <div><h2>已关联租户</h2><p>{linked.data?.map(item => item.name).join("、") || "尚未关联可管理的租户"}</p></div>
      </div>
      <div className="table-scroll"><table>
        <thead><tr><th>用户名</th><th>邮箱</th><th>状态</th><th className="align-right">操作</th></tr></thead>
        <tbody>{accounts.data?.map(account => <tr key={account.id}>
          <td>{account.username}</td><td>{account.email}</td>
          <td><Badge tone={account.userId ? "green" : "amber"}>
            {account.userId ? "已激活" : account.expiresAt && Date.parse(account.expiresAt) <= Date.now() ? "链接已过期" : "待激活"}
          </Badge></td>
          <td><div className="row-actions">{account.userId
            ? <Button variant="ghost" disabled={!linked.data?.length} onClick={() => open({ kind: "assign", account })}>分配租户</Button>
            : <Button variant="ghost" onClick={() => setCancel(account)}>撤销链接</Button>}
          </div></td>
        </tr>)}</tbody>
      </table></div>
      {accounts.loading && <p role="status">正在加载组织账号…</p>}
      {accounts.data?.length === 0 && <div className="panel-heading">当前组织暂无托管账号。</div>}
    </section>}
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
