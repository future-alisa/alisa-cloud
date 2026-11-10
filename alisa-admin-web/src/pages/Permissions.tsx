import { useState, type FormEvent } from "react";
import { KeyRound, Plus, Search, ShieldCheck } from "lucide-react";
import { tenantPath } from "../api";
import { isAdmin, useAction, useQuery, useSession, useTenants } from "../state";
import type { Catalog, Client, Member, Permission } from "../types";
import {
  Alert,
  Badge,
  Button,
  ConfirmDialog,
  Empty,
  ErrorState,
  FormFooter,
  Id,
  Loading,
  Modal,
  PageTitle,
  roleLabels,
  shortId,
  ScopePicker,
  useToast,
} from "../ui";
const actionNames: Record<string, string> = {
  read: "读取",
  create: "创建",
  update: "修改",
  delete: "删除",
};
export default function PermissionsPage() {
  const { selected } = useTenants();
  const { api } = useSession();
  const admin = isAdmin(selected);
  const permissions = useQuery<Permission[]>(
    admin ? tenantPath(selected!.id, "/permissions") : null
  );
  const catalog = useQuery<Catalog>(
    admin ? tenantPath(selected!.id, "/catalog") : null
  );
  const members = useQuery<Member[]>(
    admin ? tenantPath(selected!.id, "/members") : null
  );
  const apps = useQuery<Client[]>(
    admin ? tenantPath(selected!.id, "/applications") : null
  );
  const [search, setSearch] = useState("");
  const [create, setCreate] = useState(false);
  const [remove, setRemove] = useState<Permission | null>(null);
  const toast = useToast();
  if (!admin)
    return (
      <>
        <PageTitle
          eyebrow="AUTHORIZATION"
          title="资源权限"
          description="明确每个用户和后台应用能够访问的资源。"
        />
        <div className="panel">
          <Empty
            icon={<ShieldCheck size={26} />}
            title="此页面需要管理员权限"
            description="请联系租户所有者或管理员，为你配置需要的资源访问权限。"
          />
        </div>
      </>
    );
  const memberNames = new Map(members.data?.map((m) => [m.userId, m.username]));
  const applicationNames = new Map(apps.data?.map((app) => [app.clientId, app.name]));
  const subjectName = (permission: Permission) =>
    (permission.subjectType === "APPLICATION" ? applicationNames : memberNames)
      .get(permission.subjectId) ?? "";
  const filtered =
    permissions.data?.filter((p) =>
      `${p.applicationId} ${subjectName(p)} ${p.subjectId} ${p.resourceType} ${p.resourceId} ${p.action}`
        .toLowerCase()
        .includes(search.toLowerCase())
    ) || [];
  return (
    <>
      <PageTitle
        eyebrow="AUTHORIZATION"
        title="资源权限"
        description="把功能范围落实到具体资源。每一条授权都只在当前租户内生效。"
        actions={
          <Button
            variant="primary"
            icon={<Plus size={17} />}
            disabled={!catalog.data || !members.data || !apps.data}
            onClick={() => setCreate(true)}
          >
            添加授权
          </Button>
        }
      />
      <div className="info-note inline">
        <ShieldCheck size={19} />
        <span>
          <strong>范围审批与资源授权分别生效。</strong>{" "}
          应用获批后，仍需在这里明确授予它可访问的数据。
        </span>
      </div>
      <Alert>{catalog.error || members.error || apps.error}</Alert>
      <div className="panel">
        <div className="table-toolbar">
          <div>
            <h2>
              授权策略{" "}
              <span className="count-label">
                {permissions.data?.length ?? "—"}
              </span>
            </h2>
          </div>
          <div className="search">
            <Search size={17} />
            <input
              aria-label="搜索资源权限"
              placeholder="搜索业务、主体或资源"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
        </div>
        {permissions.loading ? (
          <Loading />
        ) : permissions.error ? (
          <ErrorState message={permissions.error} retry={permissions.reload} />
        ) : !filtered.length ? (
          <Empty
            icon={<KeyRound size={26} />}
            title={search ? "没有匹配的授权" : "尚未配置资源授权"}
            description="选择用户或后台应用，为其授予指定业务的资源操作权限。"
            action={
              <Button
                onClick={() => setCreate(true)}
                disabled={!catalog.data || !members.data || !apps.data}
              >
                添加授权
              </Button>
            }
          />
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>授权主体</th>
                  <th>接入业务</th>
                  <th>资源类型</th>
                  <th>资源范围</th>
                  <th>允许操作</th>
                  <th className="align-right">操作</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((p) => (
                  <tr key={JSON.stringify(p)}>
                    <td>
                      <div className="subject-cell">
                        <Badge
                          tone={
                            p.subjectType === "APPLICATION" ? "blue" : "neutral"
                          }
                        >
                          {p.subjectType === "APPLICATION" ? "应用" : "用户"}
                        </Badge>
                        {subjectName(p) && <strong>{subjectName(p)}</strong>}
                        <Id value={p.subjectId} />
                      </div>
                    </td>
                    <td>{p.applicationId}</td>
                    <td>
                      <code>{p.resourceType}</code>
                    </td>
                    <td>
                      {p.resourceId === "*" ? (
                        <span className="scope-all">
                          本租户全部资源 <code>*</code>
                        </span>
                      ) : (
                        <Id value={p.resourceId} />
                      )}
                    </td>
                    <td>
                      <Badge tone="green">
                        {actionNames[p.action] || p.action}
                      </Badge>
                    </td>
                    <td className="align-right">
                      <Button
                        variant="ghost"
                        className="danger-text"
                        onClick={() => setRemove(p)}
                      >
                        撤销
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      <div className="info-strip">
        <KeyRound size={17} />
        <span>
          所有者和管理员拥有租户内的管理权限；普通用户还受数据归属限制。后台应用仅能访问明确授予的资源。
        </span>
      </div>
      {create && catalog.data && (
        <PermissionDialog
          catalog={catalog.data}
          members={members.data || []}
          clients={apps.data || []}
          onClose={() => setCreate(false)}
          onSaved={permissions.reload}
          onDone={() => {
            setCreate(false);
            toast("资源授权已添加");
          }}
        />
      )}
      {remove && (
        <ConfirmDialog
          title="撤销这条资源授权？"
          description="撤销后，依赖这条策略的访问会被拒绝。其他授权策略保持不变。"
          danger
          label="撤销授权"
          onClose={() => setRemove(null)}
          onConfirm={async () => {
            await api(tenantPath(selected!.id, "/permissions"), {
              method: "DELETE",
              body: remove,
            });
            permissions.reload();
            toast("资源授权已撤销");
          }}
        />
      )}
    </>
  );
}
function PermissionDialog({
  catalog,
  members,
  clients,
  onClose,
  onSaved,
  onDone,
}: {
  catalog: Catalog;
  members: Member[];
  clients: Client[];
  onClose: () => void;
  onSaved: () => void;
  onDone: () => void;
}) {
  const { api } = useSession();
  const { selected } = useTenants();
  const action = useAction();
  const [subjectType, setSubjectType] = useState<"USER" | "APPLICATION">(
    "APPLICATION"
  );
  const [subjectId, setSubjectId] = useState("");
  const [applicationId, setApplicationId] = useState(
    Object.keys(catalog)[0] || ""
  );
  const [scopes, setScopes] = useState<string[]>([]);
  const [resourceId, setResourceId] = useState("*");
  const clientOptions = clients.filter(
    (c) =>
      c.kind === "machine" && c.enabled && c.applicationId === applicationId
  );
  async function submit(event: FormEvent) {
    event.preventDefault();
    const id = resourceId.trim();
    if (!scopes.length || !id) {
      action.setError(scopes.length ? "请输入资源 ID" : "请选择至少一项功能");
      return;
    }
    const grants = scopes.map((scope) => {
      const [resourceType, action] = scope.slice(applicationId.length + 1).split(":");
      return { scope, resourceType, action };
    });
    if (id !== "*" && new Set(grants.map((grant) => grant.resourceType)).size > 1) {
      action.setError("指定资源 ID 时，请选择同一资源类型的功能");
      return;
    }
    await action.run(async () => {
      let saved = 0;
      try {
        for (const grant of grants) {
          await api(tenantPath(selected!.id, "/permissions"), {
            method: "PUT",
            body: {
              applicationId,
              subjectType,
              subjectId,
              resourceType: grant.resourceType,
              resourceId: id,
              action: grant.action,
            },
          });
          saved++;
        }
      } catch (error) {
        if (!saved) throw error;
        setScopes(scopes.slice(saved));
        const reason = error instanceof Error ? error.message : "保存失败";
        throw new Error(`已保存 ${saved} 项，剩余 ${scopes.length - saved} 项可重试。
${reason}`);
      } finally {
        if (saved) onSaved();
      }
      onDone();
    });
  }
  return (
    <Modal
      title="添加资源授权"
      description="选择授权主体、业务功能和可访问的资源范围。"
      onClose={onClose}
      busy={action.pending}
    >
      <form onSubmit={submit}>
        <fieldset disabled={action.pending} style={{ border: 0, margin: 0, padding: 0, minWidth: 0 }}>
          <label>
            接入业务
            <select
              required
              value={applicationId}
              onChange={(e) => {
                setApplicationId(e.target.value);
                setScopes([]);
                setSubjectId("");
              }}
            >
              {Object.keys(catalog).map((app) => (
                <option key={app}>{app}</option>
              ))}
            </select>
          </label>
          <div className="form-grid">
            <label>
              主体类型
              <select
                value={subjectType}
                onChange={(e) => {
                  setSubjectType(e.target.value as "USER" | "APPLICATION");
                  setSubjectId("");
                }}
              >
                <option value="APPLICATION">后台应用</option>
                <option value="USER">租户成员</option>
              </select>
            </label>
            <label>
              {subjectType === "APPLICATION" ? "选择应用" : "选择成员"}
              <select
                required
                value={subjectId}
                onChange={(e) => setSubjectId(e.target.value)}
              >
                <option value="">请选择</option>
                {subjectType === "APPLICATION"
                  ? clientOptions.map((c) => (
                      <option key={c.clientId} value={c.clientId}>
                        {c.name}
                        {c.approval === "APPROVED" ? "" : " · 尚未批准"}
                      </option>
                    ))
                  : members.map((m) => (
                      <option key={m.userId} value={m.userId}>
                        {m.username || shortId(m.userId)} · {roleLabels[m.role]}
                      </option>
                    ))}
              </select>
            </label>
          </div>
          {subjectType === "APPLICATION" && !clientOptions.length && (
            <div className="info-note">
              当前业务没有可授权的后台应用，请先在「应用与审批」中登记。
            </div>
          )}
          <fieldset style={{ border: 0, margin: 0, padding: 0, minWidth: 0 }}>
            <legend>允许的功能</legend>
            <ScopePicker
              catalog={catalog[applicationId] || {}}
              selected={scopes}
              onChange={setScopes}
            />
          </fieldset>
          <label>
            资源 ID
            <input
              required
              maxLength={191}
              value={resourceId}
              onChange={(e) => setResourceId(e.target.value)}
              placeholder="输入资源 ID，或 *"
            />
            <small>* 表示当前租户内该类型的全部资源，不包含其他租户。</small>
          </label>
        </fieldset>
        <Alert>{action.error}</Alert>
        <FormFooter
          onClose={onClose}
          pending={action.pending}
          label="添加授权"
        />
      </form>
    </Modal>
  );
}
