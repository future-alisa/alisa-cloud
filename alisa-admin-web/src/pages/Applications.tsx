import { useState, type FormEvent } from "react";
import {
  AppWindow,
  ArrowRight,
  Bot,
  Eye,
  KeyRound,
  Plus,
  Search,
  Server,
  ShieldCheck,
  UserRound,
} from "lucide-react";
import { Link } from "react-router-dom";
import { tenantPath } from "../api";
import { isAdmin, useAction, useQuery, useSession, useTenants } from "../state";
import type { Catalog, Client, Credentials } from "../types";
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
  ScopePicker,
  SecretModal,
  useToast,
} from "../ui";
type Confirm = {
  title: string;
  description: string;
  label: string;
  action: () => Promise<void>;
};
export default function ApplicationsPage() {
  const { selected } = useTenants();
  const { api } = useSession();
  const admin = isAdmin(selected);
  const apps = useQuery<Client[]>(tenantPath(selected!.id, "/applications"));
  const catalog = useQuery<Catalog>(tenantPath(selected!.id, "/catalog"));
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState("all");
  const [create, setCreate] = useState<"app" | "resource" | null>(null);
  const [review, setReview] = useState<Client | null>(null);
  const [details, setDetails] = useState<Client | null>(null);
  const [confirm, setConfirm] = useState<Confirm | null>(null);
  const [credentials, setCredentials] = useState<Credentials | null>(null);
  const toast = useToast();
  const filtered = (apps.data || []).filter(
    (client) =>
      `${client.name} ${client.clientId} ${client.applicationId}`
        .toLowerCase()
        .includes(search.toLowerCase()) &&
      (filter === "all" ||
        (filter === "pending" &&
          client.enabled &&
          client.approval === "PENDING") ||
        (filter === "resource" && client.kind === "resource") ||
        (filter === "disabled" && !client.enabled))
  );
  function approvalCatalog(client: Client) {
    const dictionary = catalog.data?.[client.applicationId] || {};
    return Object.fromEntries(
      client.requestedScopes.map((scope) => [scope, dictionary[scope] || scope])
    );
  }
  return (
    <>
      <PageTitle
        eyebrow="APPLICATIONS"
        title="应用与审批"
        description="登记接入应用，审核申请范围，让每个应用只访问必要的功能。"
        actions={
          <>
            {admin && (
              <Button
                icon={<Server size={16} />}
                disabled={!catalog.data}
                onClick={() => setCreate("resource")}
              >
                添加验证凭据
              </Button>
            )}
            <Button
              variant="primary"
              icon={<Plus size={17} />}
              disabled={!catalog.data}
              onClick={() => setCreate("app")}
            >
              登记应用
            </Button>
          </>
        }
      />
      <Alert>{catalog.error}</Alert>
      <div className="panel">
        <div className="table-toolbar">
          <div className="tabs">
            {[
              { key: "all", name: "全部应用" },
              { key: "pending", name: "待审批" },
              { key: "resource", name: "验证凭据" },
              { key: "disabled", name: "已停用" },
            ].map((item) => (
              <button
                className={filter === item.key ? "active" : ""}
                key={item.key}
                onClick={() => setFilter(item.key)}
              >
                {item.name}
                {item.key === "pending" && (
                  <span>
                    {apps.data?.filter(
                      (a) => a.enabled && a.approval === "PENDING"
                    ).length ?? "—"}
                  </span>
                )}
              </button>
            ))}
          </div>
          <div className="search">
            <Search size={17} />
            <input
              aria-label="搜索应用"
              placeholder="搜索应用名称或 Client ID"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
        </div>
        {apps.loading ? (
          <Loading />
        ) : apps.error ? (
          <ErrorState message={apps.error} retry={apps.reload} />
        ) : !filtered.length ? (
          <Empty
            icon={<AppWindow size={27} />}
            title={
              search || filter !== "all"
                ? "没有匹配的应用"
                : "开始接入你的第一个应用"
            }
            description={
              search || filter !== "all"
                ? "可以调整筛选条件，或登记新的应用。"
                : "选择用户委托或后台应用，再声明需要的功能范围。"
            }
            action={
              <Button onClick={() => setCreate("app")} disabled={!catalog.data}>
                登记应用
              </Button>
            }
          />
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>应用名称</th>
                  <th>应用类型</th>
                  <th>Client ID</th>
                  <th>状态</th>
                  <th>批准范围</th>
                  <th className="align-right">操作</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((client) => (
                  <tr key={client.clientId}>
                    <td>
                      <button
                        className="table-app-name"
                        onClick={() => setDetails(client)}
                      >
                        <span
                          className={`app-avatar ${
                            client.kind === "machine" ? "purple" : ""
                          }`}
                        >
                          {client.kind === "machine" ? (
                            <Bot size={19} />
                          ) : client.kind === "resource" ? (
                            <Server size={19} />
                          ) : (
                            <AppWindow size={19} />
                          )}
                        </span>
                        <span>
                          <strong>{client.name}</strong>
                          <small>{client.applicationId}</small>
                        </span>
                      </button>
                    </td>
                    <td>
                      <span className="subtle-text">
                        {client.kind === "machine"
                          ? "后台应用"
                          : client.kind === "resource"
                          ? "资源服务器"
                          : "用户委托"}
                      </span>
                    </td>
                    <td>
                      <Id value={client.clientId} />
                    </td>
                    <td>
                      <ClientStatus
                        approval={client.approval}
                        enabled={client.enabled}
                      />
                    </td>
                    <td>
                      <span className="scope-count">
                        {client.scopes.length} 项功能
                      </span>
                    </td>
                    <td>
                      <div className="row-actions">
                        {admin &&
                          client.enabled &&
                          client.kind !== "resource" && (
                            <Button
                              variant="ghost"
                              onClick={() => setReview(client)}
                            >
                              {client.approval === "PENDING"
                                ? "审批"
                                : "调整范围"}
                            </Button>
                          )}
                        <Button
                          variant="ghost"
                          onClick={() => setDetails(client)}
                        >
                          详情
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      <div className="info-strip">
        <ShieldCheck size={17} />
        <span>
          {admin
            ? "批准功能范围后，还需要配置资源权限，才能访问实际数据。"
            : "你的应用需要租户管理员批准后才能使用；这里只显示你登记的应用。"}
        </span>
        {admin && (
          <Link className="text-link" to="/permissions">
            资源授权 <ArrowRight size={14} />
          </Link>
        )}
      </div>
      {create && catalog.data && (
        <CreateClientDialog
          kind={create}
          catalog={catalog.data}
          onClose={() => setCreate(null)}
          onCreated={(value) => {
            setCreate(null);
            setCredentials(value);
            apps.reload();
            toast(
              create === "resource"
                ? "验证凭据已创建"
                : "应用已登记，等待管理员审批"
            );
          }}
        />
      )}
      {review && (
        <Modal
          title={`审批「${review.name}」`}
          description="只批准这个应用实际需要的功能。每次调整都会撤销已有 token 和用户授权。"
          onClose={() => setReview(null)}
          wide
        >
          <ReviewForm
            client={review}
            catalog={approvalCatalog(review)}
            onClose={() => setReview(null)}
            onDone={() => {
              setReview(null);
              apps.reload();
              toast("应用审批已更新");
            }}
          />
        </Modal>
      )}
      {details && (
        <Modal
          title={details.name}
          description="应用身份与访问范围"
          onClose={() => setDetails(null)}
          wide
        >
          <div className="detail-grid">
            <div>
              <label>Client ID</label>
              <Id value={details.clientId} full />
            </div>
            <div>
              <label>所属业务</label>
              <strong>{details.applicationId}</strong>
            </div>
            <div>
              <label>当前状态</label>
              <ClientStatus
                approval={details.approval}
                enabled={details.enabled}
              />
            </div>
            <div>
              <label>登记用户</label>
              <Id value={details.ownerId} />
            </div>
          </div>
          <h3 className="form-section-title">请求的功能</h3>
          <div className="scope-chips">
            {details.requestedScopes.length ? (
              details.requestedScopes.map((scope) => (
                <span
                  className={`scope-chip ${
                    details.scopes.includes(scope) ? "approved" : ""
                  }`}
                  key={scope}
                >
                  {details.scopes.includes(scope) && <ShieldCheck size={13} />}
                  <span>
                    {catalog.data?.[details.applicationId]?.[scope] || scope}
                    <code>{scope}</code>
                  </span>
                </span>
              ))
            ) : (
              <p className="muted">
                此凭据仅用于验证访问 token，不申请业务功能。
              </p>
            )}
          </div>
          {details.enabled && (
            <div className="detail-actions">
              {admin && (
                <Button
                  icon={<KeyRound size={16} />}
                  onClick={() => {
                    const client = details;
                    setDetails(null);
                    setConfirm({
                      title: "轮换应用密钥？",
                      description:
                        "旧密钥及该应用的现有 token 将立即失效。公开客户端没有密钥。",
                      label: "轮换密钥",
                      action: async () => {
                        const result = await api<Credentials>(
                          tenantPath(
                            selected!.id,
                            `/applications/${client.clientId}/secret`
                          ),
                          { method: "POST" }
                        );
                        setCredentials(result);
                        apps.reload();
                        toast("应用密钥已轮换");
                      },
                    });
                  }}
                >
                  轮换密钥
                </Button>
              )}
              <Button
                variant="danger"
                onClick={() => {
                  const client = details;
                  setDetails(null);
                  setConfirm({
                    title: `停用「${client.name}」？`,
                    description:
                      "应用的 token 和授权将被撤销，之后不能再访问业务数据。",
                    label: "停用应用",
                    action: async () => {
                      await api(
                        tenantPath(
                          selected!.id,
                          `/applications/${client.clientId}`
                        ),
                        { method: "DELETE" }
                      );
                      apps.reload();
                      toast("应用已停用");
                    },
                  });
                }}
              >
                停用应用
              </Button>
            </div>
          )}
        </Modal>
      )}
      {confirm && (
        <ConfirmDialog
          title={confirm.title}
          description={confirm.description}
          label={confirm.label}
          danger
          onClose={() => setConfirm(null)}
          onConfirm={confirm.action}
        />
      )}{" "}
      {credentials && (
        <SecretModal
          title={
            credentials.clientSecret ? "请保存应用凭据" : "公开客户端已登记"
          }
          values={[
            { label: "Client ID", value: credentials.clientId },
            ...(credentials.clientSecret
              ? [{ label: "Client Secret", value: credentials.clientSecret }]
              : [
                  {
                    label: "客户端认证方式",
                    value: "公开客户端无密钥，必须使用 PKCE S256",
                  },
                ]),
          ]}
          onClose={() => setCredentials(null)}
        />
      )}
    </>
  );
}
function CreateClientDialog({
  kind,
  catalog,
  onClose,
  onCreated,
}: {
  kind: "app" | "resource";
  catalog: Catalog;
  onClose: () => void;
  onCreated: (value: Credentials) => void;
}) {
  const { selected } = useTenants();
  const { api } = useSession();
  const action = useAction();
  const [name, setName] = useState("");
  const [applicationId, setApplicationId] = useState(
    Object.keys(catalog)[0] || ""
  );
  const [machine, setMachine] = useState(false);
  const [publicClient, setPublicClient] = useState(false);
  const [redirectUris, setRedirectUris] = useState("");
  const [scopes, setScopes] = useState<string[]>([]);
  async function submit(event: FormEvent) {
    event.preventDefault();
    await action.run(async () => {
      const result = await api<Credentials>(
        tenantPath(
          selected!.id,
          kind === "resource" ? "/resource-clients" : "/applications"
        ),
        {
          method: "POST",
          body: {
            applicationId,
            name: name.trim(),
            machine,
            publicClient: !machine && publicClient,
            redirectUris: machine
              ? []
              : redirectUris
                  .split("\n")
                  .map((value) => value.trim())
                  .filter(Boolean),
            scopes,
          },
        }
      );
      onCreated(result);
    });
  }
  return (
    <Modal
      wide
      title={kind === "resource" ? "添加资源服务器验证凭据" : "登记接入应用"}
      description={
        kind === "resource"
          ? "供后端服务验证本租户的访问 token，不向浏览器或第三方应用分发。"
          : "先选择应用类型和功能范围，再提交给租户管理员审批。"
      }
      onClose={onClose}
      busy={action.pending}
    >
      <form onSubmit={submit}>
        <div className="form-grid">
          <label>
            应用名称
            <input
              autoFocus
              required
              maxLength={100}
              placeholder={
                kind === "resource"
                  ? "例如：订单 API 服务"
                  : "例如：团队目标同步工具"
              }
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </label>
          <label>
            接入业务
            <select
              required
              value={applicationId}
              onChange={(e) => {
                setApplicationId(e.target.value);
                setScopes([]);
              }}
            >
              {Object.keys(catalog).map((app) => (
                <option key={app} value={app}>
                  {app}
                </option>
              ))}
            </select>
          </label>
        </div>
        {kind === "app" && (
          <>
            <label>应用类型</label>
            <div className="type-options">
              <button
                type="button"
                className={!machine ? "selected" : ""}
                onClick={() => setMachine(false)}
              >
                <UserRound size={22} />
                <strong>用户委托应用</strong>
                <small>经用户同意，代表用户访问数据</small>
              </button>
              <button
                type="button"
                className={machine ? "selected" : ""}
                onClick={() => {
                  setMachine(true);
                  setPublicClient(false);
                }}
              >
                <Bot size={22} />
                <strong>后台应用</strong>
                <small>无需用户登录，由管理员授权</small>
              </button>
            </div>
            {!machine && (
              <>
                <label>
                  回调地址
                  <textarea
                    required
                    rows={2}
                    placeholder="https://your-app.example/callback"
                    value={redirectUris}
                    onChange={(e) => setRedirectUris(e.target.value)}
                  />
                  <small>
                    每行一个精确地址。生产环境使用 HTTPS；本机测试可使用
                    localhost HTTP。
                  </small>
                </label>
                <label className="checkbox-line">
                  <input
                    type="checkbox"
                    checked={publicClient}
                    onChange={(e) => setPublicClient(e.target.checked)}
                  />
                  公开客户端（浏览器或移动端应用，不使用密钥）
                </label>
              </>
            )}
            <ScopePicker
              catalog={catalog[applicationId] || {}}
              selected={scopes}
              onChange={setScopes}
            />
          </>
        )}
        <Alert>{action.error}</Alert>
        <FormFooter
          onClose={onClose}
          pending={action.pending}
          label={kind === "resource" ? "创建凭据" : "提交登记"}
          disabled={!applicationId || (kind === "app" && !scopes.length)}
        />
      </form>
    </Modal>
  );
}
function ReviewForm({
  client,
  catalog,
  onClose,
  onDone,
}: {
  client: Client;
  catalog: Record<string, string>;
  onClose: () => void;
  onDone: () => void;
}) {
  const { selected } = useTenants();
  const { api } = useSession();
  const [scopes, setScopes] = useState(
    client.approval === "APPROVED" ? client.scopes : []
  );
  const action = useAction();
  async function approve(approve: boolean) {
    await action.run(async () => {
      await api(
        tenantPath(selected!.id, `/applications/${client.clientId}/approval`),
        { method: "PUT", body: { approve, scopes: approve ? scopes : [] } }
      );
      onDone();
    });
  }
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        void approve(true);
      }}
    >
      <div className="info-note">
        批准范围决定应用能申请哪些功能。实际访问的数据范围，还需要在「资源权限」中授权。
      </div>
      <ScopePicker catalog={catalog} selected={scopes} onChange={setScopes} />
      <Alert>{action.error}</Alert>
      <div className="form-footer split">
        <Button
          type="button"
          variant="danger"
          disabled={action.pending}
          onClick={() => void approve(false)}
        >
          拒绝申请
        </Button>
        <div>
          <Button type="button" onClick={onClose} disabled={action.pending}>
            取消
          </Button>
          <Button
            type="submit"
            variant="primary"
            loading={action.pending}
            disabled={!scopes.length}
          >
            批准所选 {scopes.length} 项
          </Button>
        </div>
      </div>
    </form>
  );
}
