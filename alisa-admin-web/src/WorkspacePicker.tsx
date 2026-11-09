import { useId, useRef, useState } from "react";
import { Building2, Check, ChevronDown, Search } from "lucide-react";

type Workspace = { id: string; name: string; enabled?: boolean };

export default function WorkspacePicker({ label, items, selectedId, onSelect, kind }: {
  label: string;
  items: Workspace[];
  selectedId: string;
  onSelect: (id: string) => void;
  kind: "tenant" | "organization";
}) {
  const id = useId();
  const panel = useRef<HTMLDivElement>(null);
  const search = useRef<HTMLInputElement>(null);
  const [query, setQuery] = useState("");
  const selected = items.find(item => item.id === selectedId);
  const term = query.trim().toLowerCase();
  const matches = items.filter(item =>
    item.name.toLowerCase().includes(term)
  );
  const noun = kind === "tenant" ? "租户" : "组织";

  return <div className={`workspace-picker workspace-picker-${kind}`}>
    <button type="button" className="workspace-picker-trigger" popoverTarget={id}
      aria-label={label} disabled={!items.length} title={selected?.name}
      onClick={event => {
        const bounds = event.currentTarget.getBoundingClientRect();
        const width = Math.min(320, window.innerWidth - 16);
        const left = kind === "tenant" ? bounds.right - width : bounds.left;
        panel.current?.style.setProperty("--picker-left", `${Math.max(8, Math.min(left, window.innerWidth - width - 8))}px`);
        const below = window.innerHeight - bounds.bottom - 14;
        const above = bounds.top - 14;
        const openAbove = below < 230 && above > below;
        if (panel.current) {
          panel.current.dataset.above = String(openAbove);
          panel.current.style.setProperty("--picker-top", `${bounds.bottom + 6}px`);
          panel.current.style.setProperty("--picker-bottom", `${window.innerHeight - bounds.top + 6}px`);
          panel.current.style.setProperty("--picker-space", `${Math.max(0, openAbove ? above : below)}px`);
        }
      }}>
      {kind === "tenant" && <Building2 size={16} aria-hidden="true" />}
      <span>{selected?.name ?? (items.length ? `选择${noun}` : `暂无${noun}`)}</span>
      <ChevronDown size={14} aria-hidden="true" />
    </button>
    <div ref={panel} id={id} popover="auto" className="workspace-picker-panel" role="group" aria-label={label}
      onToggle={event => {
        if (event.currentTarget.matches(":popover-open")) search.current?.focus();
        else setQuery("");
      }}>
      <div className="workspace-picker-search">
        <Search size={16} aria-hidden="true" />
        <input ref={search} type="search" aria-label={`搜索${noun}`} placeholder={`搜索${noun}名称`}
          value={query} onChange={event => setQuery(event.target.value)} />
      </div>
      <div className="workspace-picker-items">
        {matches.map(item => <button type="button" key={item.id}
          aria-current={item.id === selectedId ? "true" : undefined}
          onClick={() => {
            panel.current?.hidePopover();
            onSelect(item.id);
          }}>
          <span>
            <strong>{item.name}</strong>
            {item.enabled === false && <small>已停用</small>}
          </span>
          {item.id === selectedId && <Check size={16} aria-hidden="true" />}
        </button>)}
        {matches.length === 0 && <p role="status">没有匹配的{noun}</p>}
      </div>
    </div>
  </div>;
}
