import { useEffect, useState, type FormEvent } from 'react';
import { Plus } from 'lucide-react';
import { Alert, Badge, Button, ConfirmDialog, Empty, FormFooter, Modal } from '../ui';
import type { Service } from "./PortalServices";

type NetworkZone = {
    id: string;
    name: string;
    scope: 'internal' | 'external' | 'tunnel';
    cidr: string;
    gateway: string;
    dns: string;
    description: string;
};
export type NetworkSettings = {
    revision: number;
    internalHosts: string[];
    externalHosts: string[];
    networks: NetworkZone[];
};
const serviceEndpoint = (service: Service, scope: 'internal' | 'external') =>
    scope === 'internal' ? service.internalUrl : service.externalUrl;
type Props = {
    settings: NetworkSettings;
    services: Service[];
    save: (settings: NetworkSettings) => Promise<void>;
    editService: (service: Service) => void;
};
const blankZone: NetworkZone = { id: '', name: '', scope: 'internal', cidr: '', gateway: '', dns: '', description: '' };
const scopeLabels = { internal: '内网', external: '外网', tunnel: '穿透 / VPN' };
export function NetworkManager({ settings, services, save, editService }: Props) {
    const [internalHosts, setInternalHosts] = useState(settings.internalHosts.join('\n'));
    const [externalHosts, setExternalHosts] = useState(settings.externalHosts.join('\n'));
    const [zone, setZone] = useState<NetworkZone>();
    const [removing, setRemoving] = useState<NetworkZone>();
    const [pending, setPending] = useState<'rules' | 'zone'>();
    const [ruleError, setRuleError] = useState('');
    const [zoneError, setZoneError] = useState('');
    const [success, setSuccess] = useState('');

    useEffect(() => {
        setInternalHosts(settings.internalHosts.join('\n'));
        setExternalHosts(settings.externalHosts.join('\n'));
    }, [settings]);

    function editZone(next: NetworkZone) {
        setZoneError('');
        setZone(next);
    }

    async function saveRules(event: FormEvent) {
        event.preventDefault();
        const hosts = (value: string) => value.split(/[\s,，]+/).filter(Boolean);
        setPending('rules');
        setRuleError('');
        setSuccess('');
        try {
            await save({ ...settings, internalHosts: hosts(internalHosts), externalHosts: hosts(externalHosts) });
            setSuccess('入口规则已保存');
        } catch (error) {
            setRuleError(error instanceof Error ? error.message : '无法保存入口规则');
        } finally {
            setPending(undefined);
        }
    }

    async function saveZone(event: FormEvent) {
        event.preventDefault();
        if (!zone) return;
        const networks = zone.id
            ? settings.networks.map(network => network.id === zone.id ? zone : network)
            : [...settings.networks, zone];
        setPending('zone');
        setZoneError('');
        try {
            await save({ ...settings, networks });
            setZone(undefined);
        } catch (error) {
            setZoneError(error instanceof Error ? error.message : '无法保存网络区域');
        } finally {
            setPending(undefined);
        }
    }

    return <div className="portal-network-settings">
        <section className="panel">
            <div className="panel-heading"><div>
                <h2>门户入口与默认选址</h2>
                <p>填写门户自身的主机名，每行一个，省略协议、端口和路径。</p>
            </div><Badge tone="blue">按入口域名判断</Badge></div>
            <form className="portal-network-rules" onSubmit={saveRules}>
                <Alert>{ruleError}</Alert>
                <div className="form-grid">
                    <label>内网门户主机<textarea value={internalHosts} onChange={e => setInternalHosts(e.target.value)} placeholder="portal.example.internal"/></label>
                    <label>外网门户主机<textarea value={externalHosts} onChange={e => setExternalHosts(e.target.value)} placeholder="portal.example.com"/></label>
                </div>
                <p className="muted">显式规则优先。私有 IP、localhost 和 .local 默认按内网处理，其他主机默认按外网处理。内外网共用域名时，可由分视 DNS 解析同一个服务地址。</p>
                <div className="row-actions">
                    {success && <span className="muted" role="status">{success}</span>}
                    <Button type="submit" variant="primary" loading={pending === 'rules'}>保存入口规则</Button>
                </div>
            </form>
        </section>
        <section className="panel">
            <div className="panel-heading"><div>
                <h2>网络区域</h2>
                <p>登记网段、网关和 DNS，供服务关联。移除区域前需解除服务关联。</p>
            </div><Button icon={<Plus size={16}/>} disabled={!!pending} onClick={() => editZone(blankZone)}>新增网络</Button></div>
            {!settings.networks.length ? <Empty title="尚未登记网络区域" description="添加网络区域后，可以在编辑服务时建立关联。"/> :
                <div className="table-scroll"><table>
                    <thead><tr><th>名称</th><th>类型</th><th>网段</th><th>网关 / 入口</th><th>DNS</th><th>关联服务</th><th className="align-right">操作</th></tr></thead>
                    <tbody>{settings.networks.map(network => <tr key={network.id}>
                        <td><strong>{network.name}</strong>{network.description && <div className="muted">{network.description}</div>}</td>
                        <td><Badge>{scopeLabels[network.scope]}</Badge></td>
                        <td>{network.cidr || '未配置'}</td><td>{network.gateway || '未配置'}</td><td>{network.dns || '未配置'}</td>
                        <td>{services.filter(service => service.networkId === network.id).length}</td>
                        <td><div className="row-actions">
                            <Button variant="ghost" disabled={!!pending} onClick={() => editZone(network)}>编辑</Button>
                            <Button variant="ghost" disabled={!!pending} onClick={() => setRemoving(network)}>移除</Button>
                        </div></td>
                    </tr>)}</tbody>
                </table></div>}
        </section>
        <section className="panel">
            <div className="panel-heading"><div><h2>服务内外网入口</h2><p>查看每项服务已配置的地址。</p></div></div>
            {!services.length ? <Empty title="暂无服务地址" description="登记服务后，可在这里查看它的内外网入口。"/> :
                <div className="table-scroll"><table>
                    <thead><tr><th>服务</th><th>网络区域</th><th>内网地址</th><th>外网地址</th><th className="align-right">操作</th></tr></thead>
                    <tbody>{services.map(service => <tr key={service.id}>
                        <td><strong>{service.name}</strong></td>
                        <td>{settings.networks.find(network => network.id === service.networkId)?.name || '未归类'}</td>
                        {(['internal', 'external'] as const).map(scope => {
                            const endpoint = serviceEndpoint(service, scope);
                            return <td key={scope}>{endpoint
                                ? <a className="portal-endpoint" href={endpoint} target="_blank" rel="noreferrer">{endpoint}</a>
                                : <span className="muted">未配置</span>}</td>;
                        })}
                        <td><div className="row-actions"><Button variant="ghost" disabled={!!pending} onClick={() => editService(service)}>配置地址</Button></div></td>
                    </tr>)}</tbody>
                </table></div>}
        </section>
        {zone && <Modal title={zone.id ? '编辑网络区域' : '新增网络区域'} onClose={() => setZone(undefined)} busy={pending === 'zone'} wide>
            <form onSubmit={saveZone}>
                <Alert>{zoneError}</Alert>
                <div className="form-grid">
                    <label>网络名称<input value={zone.name} maxLength={80} required onChange={e => setZone({ ...zone, name: e.target.value })}/></label>
                    <label>网络类型<select value={zone.scope} onChange={e => setZone({ ...zone, scope: e.target.value as NetworkZone['scope'] })}>
                        <option value="internal">内网</option><option value="external">外网</option><option value="tunnel">穿透 / VPN</option>
                    </select></label>
                    <label>网段（选填，CIDR）<input value={zone.cidr} placeholder="192.168.1.0/24" onChange={e => setZone({ ...zone, cidr: e.target.value })}/></label>
                    <label>网关或入口<input value={zone.gateway} maxLength={256} onChange={e => setZone({ ...zone, gateway: e.target.value })}/></label>
                    <label>DNS<input value={zone.dns} maxLength={256} onChange={e => setZone({ ...zone, dns: e.target.value })}/></label>
                    <label>说明<textarea value={zone.description} maxLength={500} onChange={e => setZone({ ...zone, description: e.target.value })}/></label>
                </div>
                <FormFooter onClose={() => setZone(undefined)} pending={pending === 'zone'} label="保存网络区域"/>
            </form>
        </Modal>}
        {removing && <ConfirmDialog title="移除网络区域" description={`移除「${removing.name}」的目录登记。`}
            label="移除网络区域" danger onClose={() => setRemoving(undefined)}
            onConfirm={() => save({ ...settings, networks: settings.networks.filter(network => network.id !== removing.id) })}/>}
    </div>;
}
