import { useState } from "react";
import {
  ChevronLeft,
  ChevronRight,
  RefreshCw,
  ScrollText,
  Search,
  ShieldCheck,
} from "lucide-react";
import { tenantPath } from "../api";
import { isAdmin, useQuery, useTenants } from "../state";
import type { Audit } from "../types";
import {
  Badge,
  Button,
  Empty,
  ErrorState,
  formatDate,
  Id,
  Loading,
  Modal,
  PageTitle,
} from "../ui";
const events: Record<string, string> = {
  TENANT_CREATED: "创建租户",
  TENANT_UPDATED: "更新租户",
  TENANT_DISABLED: "停用租户",
  MEMBER_INVITED: "邀请成员",
  INVITATION_ACCEPTED: "接受邀请",
  INVITATION_CANCELLED: "撤销邀请",
  MEMBER_ROLE_CHANGED: "调整成员角色",
  MEMBER_REMOVED: "移除成员",
  OWNERSHIP_TRANSFERRED: "转移所有权",
  APPLICATION_REQUESTED: "登记应用",
  APPLICATION_APPROVED: "批准应用范围",
  APPLICATION_REJECTED: "拒绝应用申请",
  APPLICATION_DISABLED: "停用应用",
  CLIENT_SECRET_ROTATED: "轮换应用密钥",
  RESOURCE_CLIENT_CREATED: "创建验证凭据",
  PERMISSION_GRANTED: "授予资源权限",
  PERMISSION_REVOKED: "撤销资源权限",
  USER_CONSENT_SAVED: "用户同意授权",
  USER_CONSENT_REVOKED: "用户撤销授权",
  USER_GRANT_REVOKED: "用户撤销授权",
  TOKEN_REQUEST_AUTHORIZED: "批准 token 请求",
  TENANT_TOKEN_REQUESTED: "进入租户空间",
  ACCESS_PERMIT: "允许资源访问",
  ACCESS_DENY: "拒绝资源访问",
  ACCESS_INDETERMINATE: "授权服务不可用",
};
export default function AuditPage() {
  const { selected } = useTenants();
  const admin = isAdmin(selected);
  const [cursors, setCursors] = useState([""]);
  const cursor = cursors[cursors.length - 1];
  const query = useQuery<Audit[]>(
    admin
      ? tenantPath(
          selected!.id,
          `/audit?limit=50&before=${encodeURIComponent(cursor)}`
        )
      : null
  );
  const [search, setSearch] = useState("");
  const [details, setDetails] = useState<Audit | null>(null);
  if (!admin)
    return (
      <>
        <PageTitle
          eyebrow="ACTIVITY"
          title="审计记录"
          description="追踪租户内的管理变更和授权行为。"
        />
        <div className="panel">
          <Empty
            icon={<ShieldCheck size={26} />}
            title="此页面需要管理员权限"
            description="仅租户所有者和管理员可以查看审计记录。"
          />
        </div>
      </>
    );
  const filtered =
    query.data?.filter((item) =>
      `${events[item.eventType] || item.eventType} ${item.actorId} ${
        item.objectId
      }`
        .toLowerCase()
        .includes(search.toLowerCase())
    ) || [];
  return (
    <>
      <PageTitle
        eyebrow="ACTIVITY"
        title="审计记录"
        description="记录关键管理与授权行为，回溯每一次权限变更。"
        actions={
          <Button
            icon={<RefreshCw size={15} />}
            loading={query.loading}
            onClick={() => query.reload()}
          >
            刷新记录
          </Button>
        }
      />
      <div className="panel">
        <div className="table-toolbar">
          <h2>活动日志</h2>
          <div className="search">
            <Search size={17} />
            <input
              aria-label="搜索本页审计"
              placeholder="搜索本页事件、操作者或对象"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
        </div>
        {query.loading ? (
          <Loading />
        ) : query.error ? (
          <ErrorState message={query.error} retry={query.reload} />
        ) : !filtered.length ? (
          <Empty
            icon={<ScrollText size={26} />}
            title={search ? "本页没有匹配的记录" : "暂无审计记录"}
            description={
              search
                ? "筛选只作用于当前页，可翻页查看其他记录。"
                : "管理和授权操作发生后，将在这里留下记录。"
            }
          />
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>时间</th>
                  <th>事件</th>
                  <th>操作者</th>
                  <th>操作对象</th>
                  <th className="align-right">详情</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((event) => (
                  <tr key={event.id}>
                    <td className="nowrap muted">
                      {formatDate(event.createdAt)}
                    </td>
                    <td>
                      <div className="event-cell">
                        <span
                          className={`event-dot ${
                            event.eventType.includes("DENY") ||
                            event.eventType.includes("REJECT")
                              ? "warn"
                              : ""
                          }`}
                        />
                        <strong>
                          {events[event.eventType] || event.eventType}
                        </strong>
                      </div>
                    </td>
                    <td>
                      <Id value={event.actorId} />
                    </td>
                    <td>
                      <Id value={event.objectId} />
                    </td>
                    <td className="align-right">
                      <Button variant="ghost" onClick={() => setDetails(event)}>
                        查看
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <div className="table-pagination">
          <span>第 {cursors.length} 页 · 每页最多 50 条</span>
          <div>
            <Button
              icon={<ChevronLeft size={15} />}
              disabled={cursors.length === 1 || query.loading}
              onClick={() => setCursors((value) => value.slice(0, -1))}
            >
              上一页
            </Button>
            <Button
              disabled={query.data?.length !== 50 || query.loading}
              onClick={() => {
                const last = query.data?.at(-1);
                if (last) setCursors((value) => [...value, last.id]);
              }}
            >
              下一页 <ChevronRight size={15} />
            </Button>
          </div>
        </div>
      </div>
      <div className="info-strip">
        <ShieldCheck size={17} />
        <span>审计记录只读。密钥、token 和邀请明文凭据不会写入审计记录。</span>
      </div>
      {details && (
        <Modal
          title={events[details.eventType] || details.eventType}
          description={formatDate(details.createdAt)}
          onClose={() => setDetails(null)}
        >
          <div className="detail-grid">
            <div>
              <label>操作者</label>
              <Id value={details.actorId} full />
            </div>
            <div>
              <label>操作对象</label>
              <Id value={details.objectId} full />
            </div>
          </div>
          <label>
            事件类型<code className="readonly-field">{details.eventType}</code>
          </label>
          <label>
            审计 ID
            <Id value={details.id} full />
          </label>
          <label>
            事件内容<pre className="json-block">{pretty(details.details)}</pre>
          </label>
          <div className="form-footer">
            <Button onClick={() => setDetails(null)}>关闭</Button>
          </div>
        </Modal>
      )}
    </>
  );
}
function pretty(value: string) {
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}
