import { useState, type FormEvent } from "react";
import {
  ArrowRightLeft,
  Mail,
  Plus,
  Search,
  UserMinus,
  Users,
} from "lucide-react";
import { tenantPath } from "../api";
import { isAdmin, useAction, useQuery, useSession, useTenants } from "../state";
import type { CreatedInvitation, Invitation, Member, Role } from "../types";
import {
  Alert,
  Badge,
  Button,
  ConfirmDialog,
  Empty,
  ErrorState,
  FormFooter,
  formatDate,
  Id,
  Loading,
  Modal,
  PageTitle,
  RoleBadge,
  roleLabels,
  SecretModal,
  useToast,
} from "../ui";
export default function MembersPage() {
  const { selected, reload: reloadTenants } = useTenants();
  const { api, session } = useSession();
  const admin = isAdmin(selected);
  const owner = selected?.role === "OWNER";
  const members = useQuery<Member[]>(tenantPath(selected!.id, "/members"));
  const invites = useQuery<Invitation[]>(
    admin ? tenantPath(selected!.id, "/invitations") : null
  );
  const [tab, setTab] = useState<"members" | "invitations">("members");
  const [search, setSearch] = useState("");
  const [dialog, setDialog] = useState<{
    kind: "invite" | "role" | "transfer";
    member?: Member;
  } | null>(null);
  const [confirmation, setConfirmation] = useState<{
    title: string;
    description: string;
    action: () => Promise<void>;
  } | null>(null);
  const [secret, setSecret] = useState<CreatedInvitation | null>(null);
  const toast = useToast();
  const memberName = (id: string) =>
    id === session?.userId ? `${session.username}（我）` : id;
  const refresh = () => {
    members.reload();
    invites.reload();
    reloadTenants();
  };
  const filtered =
    members.data?.filter((member) =>
      `${member.userId} ${memberName(member.userId)}`
        .toLowerCase()
        .includes(search.toLowerCase())
    ) || [];
  return (
    <>
      <PageTitle
        eyebrow="PEOPLE"
        title="成员管理"
        description="邀请团队成员，为每个人分配合适的管理角色。"
        actions={
          <>
            {owner && (
              <Button
                icon={<ArrowRightLeft size={16} />}
                disabled={(members.data?.length || 0) < 2}
                onClick={() => setDialog({ kind: "transfer" })}
              >
                转移所有权
              </Button>
            )}
            {admin && (
              <Button
                variant="primary"
                icon={<Plus size={17} />}
                onClick={() => setDialog({ kind: "invite" })}
              >
                邀请成员
              </Button>
            )}
          </>
        }
      />
      <div className="panel">
        <div className="table-toolbar">
          <div className="tabs">
            <button
              className={tab === "members" ? "active" : ""}
              onClick={() => setTab("members")}
            >
              成员 <span>{members.data?.length ?? "—"}</span>
            </button>
            {admin && (
              <button
                className={tab === "invitations" ? "active" : ""}
                onClick={() => setTab("invitations")}
              >
                待接受邀请 <span>{invites.data?.length ?? "—"}</span>
              </button>
            )}
          </div>
          <div className="search">
            <Search size={17} />
            <input
              aria-label="搜索成员"
              placeholder="搜索用户 ID"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
        </div>
        {tab === "members" ? (
          members.loading ? (
            <Loading />
          ) : members.error ? (
            <ErrorState message={members.error} retry={members.reload} />
          ) : !filtered.length ? (
            <Empty
              title="没有匹配的成员"
              description="请检查搜索条件，或邀请新成员加入。"
            />
          ) : (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>成员</th>
                    <th>用户 ID</th>
                    <th>租户角色</th>
                    <th className="align-right">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((member) => {
                    const me = member.userId === session?.userId;
                    const canManage =
                      admin &&
                      member.role !== "OWNER" &&
                      (owner || member.role !== "ADMIN");
                    return (
                      <tr key={member.userId}>
                        <td>
                          <div className="person-cell">
                            <span className="avatar small">
                              {me
                                ? session.username.slice(0, 1).toUpperCase()
                                : member.userId.slice(0, 1).toUpperCase()}
                            </span>
                            <div>
                              <strong>
                                {me ? session.username : "团队成员"}
                              </strong>
                              <small>
                                {me ? "当前登录账号" : "已加入当前租户"}
                              </small>
                            </div>
                            {me && <span className="mini-label">我</span>}
                          </div>
                        </td>
                        <td>
                          <Id value={member.userId} />
                        </td>
                        <td>
                          <RoleBadge role={member.role} />
                        </td>
                        <td>
                          <div className="row-actions">
                            {canManage && (
                              <Button
                                variant="ghost"
                                onClick={() =>
                                  setDialog({ kind: "role", member })
                                }
                              >
                                修改角色
                              </Button>
                            )}
                            {member.role !== "OWNER" && (canManage || me) && (
                              <Button
                                variant="ghost"
                                className="danger-text"
                                onClick={() =>
                                  setConfirmation({
                                    title: me
                                      ? "退出当前租户？"
                                      : "移除这位成员？",
                                    description: me
                                      ? "退出后你将无法访问这个租户的数据，重新加入需要新的邀请。"
                                      : `移除用户 ${member.userId} 后，其租户会话和委托访问将失效。`,
                                    action: async () => {
                                      await api(
                                        tenantPath(
                                          selected!.id,
                                          `/members/${encodeURIComponent(
                                            member.userId
                                          )}`
                                        ),
                                        { method: "DELETE" }
                                      );
                                      refresh();
                                      toast(me ? "已退出租户" : "成员已移除");
                                    },
                                  })
                                }
                              >
                                {me ? "退出租户" : "移除"}
                              </Button>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )
        ) : invites.loading ? (
          <Loading />
        ) : invites.error ? (
          <ErrorState message={invites.error} retry={invites.reload} />
        ) : !invites.data?.length ? (
          <Empty
            icon={<Mail size={25} />}
            title="没有待接受的邀请"
            description="邀请已注册的 Alisa 用户加入你的团队。"
            action={
              <Button onClick={() => setDialog({ kind: "invite" })}>
                邀请成员
              </Button>
            }
          />
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>邀请用户</th>
                  <th>角色</th>
                  <th>有效期至</th>
                  <th>状态</th>
                  <th className="align-right">操作</th>
                </tr>
              </thead>
              <tbody>
                {invites.data
                  .filter((i) => i.userId.includes(search))
                  .map((invitation) => (
                    <tr key={invitation.id}>
                      <td>
                        <Id value={invitation.userId} />
                      </td>
                      <td>
                        <RoleBadge role={invitation.role} />
                      </td>
                      <td>{formatDate(invitation.expiresAt)}</td>
                      <td>
                        <Badge
                          tone={
                            new Date(invitation.expiresAt).getTime() >
                            Date.now()
                              ? "amber"
                              : "neutral"
                          }
                        >
                          {new Date(invitation.expiresAt).getTime() > Date.now()
                            ? "待接受"
                            : "已过期"}
                        </Badge>
                      </td>
                      <td className="align-right">
                        <Button
                          variant="ghost"
                          className="danger-text"
                          onClick={() =>
                            setConfirmation({
                              title: "撤销这份邀请？",
                              description:
                                "邀请凭据将立即失效。对方需要新邀请才能加入。",
                              action: async () => {
                                await api(
                                  tenantPath(
                                    selected!.id,
                                    `/invitations/${encodeURIComponent(
                                      invitation.id
                                    )}`
                                  ),
                                  { method: "DELETE" }
                                );
                                invites.reload();
                                toast("邀请已撤销");
                              },
                            })
                          }
                        >
                          撤销邀请
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
        <Users size={17} />
        <span>
          所有者可管理整个租户；管理员负责成员与授权。普通成员的业务访问范围由资源权限决定。
        </span>
      </div>
      {dialog && (
        <MemberDialog
          kind={dialog.kind}
          member={dialog.member}
          members={members.data || []}
          onClose={() => setDialog(null)}
          onDone={(value) => {
            setDialog(null);
            refresh();
            if (value) setSecret(value);
          }}
        />
      )}
      {secret && (
        <SecretModal
          title="邀请已生成"
          values={[
            { label: "租户 ID", value: selected!.id },
            { label: "邀请 ID", value: secret.id },
            { label: "邀请凭据", value: secret.token },
          ]}
          onClose={() => setSecret(null)}
        />
      )}{" "}
      {confirmation && (
        <ConfirmDialog
          {...confirmation}
          danger
          label="确认操作"
          onClose={() => setConfirmation(null)}
          onConfirm={confirmation.action}
        />
      )}
    </>
  );
}
function MemberDialog({
  kind,
  member,
  members,
  onClose,
  onDone,
}: {
  kind: "invite" | "role" | "transfer";
  member?: Member;
  members: Member[];
  onClose: () => void;
  onDone: (secret?: CreatedInvitation) => void;
}) {
  const { selected } = useTenants();
  const { api, session } = useSession();
  const [userId, setUserId] = useState(member?.userId || "");
  const [role, setRole] = useState<Role>(member?.role || "MEMBER");
  const action = useAction();
  const toast = useToast();
  const roles: Role[] =
    selected?.role === "OWNER"
      ? ["ADMIN", "MEMBER", "READER"]
      : ["MEMBER", "READER"];
  async function submit(event: FormEvent) {
    event.preventDefault();
    await action.run(async () => {
      let secret: CreatedInvitation | undefined;
      if (kind === "invite")
        secret = await api(tenantPath(selected!.id, "/invitations"), {
          method: "POST",
          body: { userId: userId.trim(), role },
        });
      else if (kind === "role")
        await api(
          tenantPath(selected!.id, `/members/${encodeURIComponent(userId)}`),
          { method: "PUT", body: { role } }
        );
      else
        await api(tenantPath(selected!.id, "/owner"), {
          method: "POST",
          body: { userId },
        });
      onDone(secret);
      toast(
        kind === "invite"
          ? "邀请已创建"
          : kind === "role"
          ? "成员角色已更新"
          : "所有权已转移"
      );
    });
  }
  return (
    <Modal
      title={
        kind === "invite"
          ? "邀请团队成员"
          : kind === "role"
          ? "修改成员角色"
          : "转移租户所有权"
      }
      description={
        kind === "invite"
          ? "仅限已注册的账号。邀请有效期为两天，明文凭据仅展示一次。"
          : kind === "role"
          ? "角色控制租户管理能力，业务数据访问仍受资源权限限制。"
          : "转移后你会成为管理员，新所有者拥有租户的最终管理权。"
      }
      onClose={onClose}
      busy={action.pending}
    >
      <form onSubmit={submit}>
        {kind === "transfer" ? (
          <label>
            新所有者
            <select
              required
              value={userId}
              onChange={(e) => setUserId(e.target.value)}
            >
              <option value="">请选择一位现有成员</option>
              {members
                .filter((m) => m.userId !== session?.userId)
                .map((m) => (
                  <option key={m.userId} value={m.userId}>
                    {m.userId} · {roleLabels[m.role]}
                  </option>
                ))}
            </select>
          </label>
        ) : (
          <>
            <label>
              {kind === "invite" ? "账号邮箱或用户 ID" : "用户 ID"}
              <input
                autoFocus
                required
                readOnly={kind === "role"}
                placeholder="输入对方的账号邮箱或用户 ID"
                value={userId}
                onChange={(e) => setUserId(e.target.value)}
              />
              {kind === "invite" && (
                <small>邮箱必须已绑定 Alisa 账号。新用户请到「组织与用户」开通账号。</small>
              )}
            </label>
            <label>
              租户角色
              <select
                value={role}
                onChange={(e) => setRole(e.target.value as Role)}
              >
                {roles.map((r) => (
                  <option key={r} value={r}>
                    {roleLabels[r]}
                  </option>
                ))}
              </select>
            </label>
          </>
        )}
        <Alert>{action.error}</Alert>
        <FormFooter
          onClose={onClose}
          pending={action.pending}
          label={
            kind === "invite"
              ? "生成邀请"
              : kind === "transfer"
              ? "确认转移"
              : "保存角色"
          }
        />
      </form>
    </Modal>
  );
}
