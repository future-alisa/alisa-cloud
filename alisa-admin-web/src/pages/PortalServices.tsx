import { useEffect, useState, type FormEvent } from "react";

import { ArrowUpRight, Network, Plus } from "lucide-react";
import { Alert, Badge, Button, ConfirmDialog, Empty, FormFooter, Loading, Modal, PageTitle } from "../ui";

import { NetworkManager, type NetworkSettings } from "./PortalNetworks";

export type Service = {
  id: string;
  name: string;
  category: string;
  internalUrl: string;
  externalUrl: string;
  networkId: string;
  description: string;
  publicDescription: string;
  visibility: "private" | "public";
  accessRequirement: "account" | "none";
};
type Catalog = {
  services: Service[];
  networkSettings?: NetworkSettings;
};
type PortalSession = { authenticated: boolean; canManage: boolean; csrfToken: string };
const emptyService: Service = {
  id: "", name: "", category: "", internalUrl: "", externalUrl: "", networkId: "",
  description: "", publicDescription: "", visibility: "private", accessRequirement: "account",
};

async function portalRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch("/api/" + path, { ...init, cache: "no-store" });
  const data = await response.json().catch(() => null);
  if (!response.ok) throw new Error(data?.error || "门户服务暂不可用");
  if (!data) throw new Error("门户返回了无法识别的响应");
  return data as T;
}

export default function PortalServices() {
  const [session, setSession] = useState<PortalSession>();
  const [catalog, setCatalog] = useState<Catalog>();
  const [draft, setDraft] = useState<Service>();
  const [error, setError] = useState("");
  const [removing, setRemoving] = useState<Service>();
  const [formError, setFormError] = useState("");
  const [busy, setBusy] = useState(false);
  const [showNetworks, setShowNetworks] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    const options = { signal: controller.signal };
    void (async () => {
      try {
        const current = await portalRequest<PortalSession>("auth/session", options);
        if (controller.signal.aborted) return;
        setSession(current);
        if (current.authenticated && current.canManage) {
          const data = await portalRequest<Catalog>("state", options);
          if (!controller.signal.aborted) setCatalog(data);
        }
      } catch (error) {
        if (!controller.signal.aborted) setError(error instanceof Error ? error.message : "无法读取服务目录");
      }
    })();
    return () => controller.abort();
  }, []);

  async function update(method: "POST" | "PUT" | "DELETE", id: string, fields?: FormData) {
    if (!session?.canManage) throw new Error("当前账号没有门户管理权限");
    const next = await portalRequest<Catalog>(id ? "services/" + encodeURIComponent(id) : "services", {
      method,
      headers: { "Content-Type": "application/json", "X-CSRF-Token": session.csrfToken },
      body: fields ? JSON.stringify(Object.fromEntries(fields)) : undefined,
    });
    setCatalog(next);
  }

  async function saveNetwork(settings: NetworkSettings) {
    if (!session?.canManage) throw new Error("当前账号没有门户管理权限");
    const next = await portalRequest<Catalog>("network-settings", {
      method: "PUT",
      headers: { "Content-Type": "application/json", "X-CSRF-Token": session.csrfToken },
      body: JSON.stringify(settings),
    });
    setCatalog(next);
  }

  function editService(service: Service) {
    setFormError("");
    setDraft(service);
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!draft || busy) return;
    setBusy(true);
    setFormError("");
    try {
      await update(draft.id ? "PUT" : "POST", draft.id, new FormData(event.currentTarget));
      setDraft(undefined);
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "无法保存服务");
    } finally {
      setBusy(false);
    }
  }

  return <>
    <PageTitle title="门户服务目录" description="管理门户上的服务入口、展示范围和访问要求。"
      actions={<>
        <a className="button secondary" href="/">返回门户 <ArrowUpRight size={16}/></a>
        {catalog?.networkSettings && <Button icon={<Network size={16}/>} aria-pressed={showNetworks}
          onClick={() => setShowNetworks(value => !value)}>
          {showNetworks ? "返回服务列表" : "网络设置"}
        </Button>}
        {catalog && <Button variant="primary" icon={<Plus size={16}/>}
          onClick={() => editService(emptyService)}>登记服务</Button>}
      </>}/>
    <Alert>{error}</Alert>
    {!catalog && !error && (!session || (session.authenticated && session.canManage)) && <Loading/>}
    {session && !session.authenticated && <section className="panel">
      <Empty title="请确认门户身份" description="使用 Alisa 账号继续管理门户服务目录。"
        action={<a className="button primary" href="/api/auth/login?next=admin-services">使用 Alisa 账号继续</a>}/>
    </section>}
    {session?.authenticated && !session.canManage && <section className="panel">
      <Empty title="没有管理权限" description="当前账号没有门户服务目录的管理权限。"/>
    </section>}
    {catalog && !showNetworks && <section className="panel">
      <div className="panel-heading"><h2>已登记服务</h2><span className="muted">{catalog.services.length} 项</span></div>
      {!catalog.services.length ? <Empty title="还没有登记服务" description="登记服务后，可以配置访问地址和展示范围。"/> :
        <div className="table-scroll"><table>
          <thead><tr><th scope="col">服务名称</th><th scope="col">分类</th><th scope="col">门户展示</th>
            <th scope="col">访问要求</th><th scope="col" className="align-right">操作</th></tr></thead>
          <tbody>{catalog.services.map(service => <tr key={service.id}>
            <td><strong>{service.name}</strong></td>
            <td>{service.category || "未分类"}</td>
            <td><Badge tone={service.visibility === "public" ? "green" : "neutral"}>
              {service.visibility === "public" ? "公开展示" : "仅管理员可见"}
            </Badge></td>
            <td>{service.accessRequirement === "none" ? "可直接访问" : "需要账号或权限"}</td>
            <td><div className="row-actions">
              <Button variant="ghost" aria-label={`编辑 ${service.name}`} onClick={() => editService(service)}>编辑</Button>
              <Button variant="ghost" aria-label={`移除 ${service.name}`} onClick={() => setRemoving(service)}>移除</Button>
            </div></td>
          </tr>)}</tbody>
        </table></div>}
    </section>}
    {showNetworks && catalog?.networkSettings && <NetworkManager settings={catalog.networkSettings}
      services={catalog.services} save={saveNetwork} editService={editService}/>}
    {removing && <ConfirmDialog title="移除服务" description={`将「${removing.name}」从门户目录移除。`}
      label="移除服务" danger onClose={() => setRemoving(undefined)}
      onConfirm={() => update("DELETE", removing.id)}/>}
    {draft && <Modal title={draft.id ? "编辑服务" : "登记服务"} onClose={() => setDraft(undefined)} busy={busy} wide>
      <form key={draft.id || "new"} onSubmit={save}>
        <Alert>{formError}</Alert>
        <div className="form-grid">
          <label>服务名称<input name="name" defaultValue={draft.name} required maxLength={80}/></label>
          <label>分类<input name="category" defaultValue={draft.category} maxLength={80}/></label>
          <label>外网地址<input name="externalUrl" type="url" defaultValue={draft.externalUrl} maxLength={2048}/></label>
          <label>内网地址<input name="internalUrl" type="url" defaultValue={draft.internalUrl} maxLength={2048}/></label>
          <label>网络区域<select name="networkId" defaultValue={draft.networkId}>
            <option value="">未归类</option>
            {catalog?.networkSettings?.networks.map(network => <option key={network.id} value={network.id}>{network.name}</option>)}
          </select></label>
          <label>门户展示范围<select name="visibility" defaultValue={draft.visibility}>
            <option value="private">仅管理员可见</option><option value="public">公开展示</option>
          </select></label>
          <label>服务访问要求<select name="accessRequirement" defaultValue={draft.accessRequirement}>
            <option value="account">需要账号或权限</option><option value="none">可直接访问</option>
          </select></label>
          <label>公开说明<textarea name="publicDescription" defaultValue={draft.publicDescription} maxLength={500}/></label>
          <label>内部说明<textarea name="description" defaultValue={draft.description} maxLength={500}/></label>
        </div>
        <FormFooter onClose={() => setDraft(undefined)} pending={busy} label="保存服务"/>
      </form>
    </Modal>}
  </>;
}
