import { useEffect, useState, type FormEvent } from "react";

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
    if (!session?.canManage || busy) return;
    setBusy(true);
    setError("");
    try {
      const next = await portalRequest<Catalog>(id ? "services/" + encodeURIComponent(id) : "services", {
        method,
        headers: { "Content-Type": "application/json", "X-CSRF-Token": session.csrfToken },
        body: fields ? JSON.stringify(Object.fromEntries(fields)) : undefined,
      });
      setCatalog(next);
      setDraft(undefined);
    } catch (error) {
      setError(error instanceof Error ? error.message : "无法保存服务目录");
    } finally {
      setBusy(false);
    }
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

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (draft) void update(draft.id ? "PUT" : "POST", draft.id, new FormData(event.currentTarget));
  }

  return <>
    <section className="panel">
      <div className="panel-heading">
        <div><h2>门户服务目录</h2><p>管理门户上的服务入口、展示范围和访问要求。</p></div>
        <a className="button" href="/">返回门户</a>
      </div>
      {error && <p role="alert">{error}</p>}
      {!session && !error && <p role="status">正在确认门户管理权限…</p>}
      {session && !session.authenticated && <p><a href="/api/auth/login?next=admin-services">使用 Alisa 账号继续</a>，确认门户服务目录管理权限。</p>}
      {session?.authenticated && !session.canManage && <p role="status">当前账号没有门户服务目录的管理权限。</p>}
      {session?.canManage && !catalog && !error && <p role="status">正在加载服务目录…</p>}
      {catalog && <>
        <button className="button primary" disabled={busy} onClick={() => setDraft(emptyService)}>登记服务</button>
        {catalog.networkSettings && <button className="button" onClick={() => setShowNetworks(value => !value)}>
          {showNetworks ? "收起网络设置" : "网络设置"}
        </button>}
        {!catalog.services.length && <p>还没有登记服务。</p>}
        {catalog.services.map(service => <div className="panel-heading" key={service.id}>
          <div><h3>{service.name}</h3><p>{service.category || "未分类"} · {service.visibility === "public" ? "公开展示" : "仅管理员可见"}</p></div>
          <div>
            <button className="button" disabled={busy} onClick={() => setDraft(service)}>编辑 {service.name}</button>
            <button className="button" disabled={busy} onClick={() => {
              if (window.confirm("从门户目录移除 " + service.name + "？")) void update("DELETE", service.id);
            }}>移除</button>
          </div>
        </div>)}
      </>}
    </section>
    {showNetworks && catalog?.networkSettings && <NetworkManager settings={catalog.networkSettings}
      services={catalog.services} save={saveNetwork} editService={setDraft}/>}
    {draft && <section className="panel settings-panel">
      <h2>{draft.id ? "编辑服务" : "登记服务"}</h2>
      <form key={draft.id || "new"} onSubmit={save}>
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
        <button className="button" type="button" disabled={busy} onClick={() => setDraft(undefined)}>取消</button>
        <button className="button primary" disabled={busy}>{busy ? "保存中…" : "保存服务"}</button>
      </form>
    </section>}
  </>;
}
