import {
  createContext,
  useContext,
  useEffect,
  useRef,
  useState,
  type ButtonHTMLAttributes,
  type ReactNode,
} from "react";
import {
  AlertCircle,
  Check,
  CheckCircle2,
  Copy,
  Inbox,
  LoaderCircle,
  X,
} from "lucide-react";
export function Button({
  variant = "secondary",
  icon,
  loading,
  children,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary" | "ghost" | "danger";
  icon?: ReactNode;
  loading?: boolean;
}) {
  return (
    <button
      {...props}
      disabled={props.disabled || loading}
      className={`button ${variant} ${props.className || ""}`}
    >
      {loading ? <LoaderCircle size={16} className="spin" /> : icon}
      {children}
    </button>
  );
}
export function Alert({ children }: { children?: ReactNode }) {
  return children ? (
    <div className="alert" role="alert">
      <AlertCircle size={17} />
      <div>{children}</div>
    </div>
  ) : null;
}
export function PageTitle({
  eyebrow,
  title,
  description,
  actions,
}: {
  eyebrow?: string;
  title: string;
  description: string;
  actions?: ReactNode;
}) {
  return (
    <header className="page-title">
      <div>
        {eyebrow && <div className="eyebrow">{eyebrow}</div>}
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      <div className="page-actions">{actions}</div>
    </header>
  );
}
export function Empty({
  title,
  description,
  action,
  icon,
}: {
  title: string;
  description: string;
  action?: ReactNode;
  icon?: ReactNode;
}) {
  return (
    <div className="empty">
      <div className="empty-icon">{icon || <Inbox size={25} />}</div>
      <h3>{title}</h3>
      <p>{description}</p>
      {action}
    </div>
  );
}
export function Loading() {
  return (
    <div className="loading" role="status" aria-label="正在加载">
      <div className="skeleton skeleton-title" />
      {Array.from({ length: 4 }, (_, index) => (
        <div className="skeleton skeleton-row" key={index} />
      ))}
    </div>
  );
}
export function ErrorState({
  message,
  retry,
}: {
  message: string;
  retry: () => void;
}) {
  return (
    <div className="error-state">
      <Alert>{message}</Alert>
      <Button onClick={retry}>重新加载</Button>
    </div>
  );
}
export function Modal({
  title,
  description,
  children,
  onClose,
  wide = false,
  busy = false,
}: {
  title: string;
  description?: string;
  children: ReactNode;
  onClose: () => void;
  wide?: boolean;
  busy?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current!;
    dialog.showModal();
    dialog
      .querySelector<HTMLElement>(
        'input:not([type="checkbox"]), select, textarea'
      )
      ?.focus();
    return () => dialog.close();
  }, []);
  return (
    <dialog
      ref={ref}
      className={`modal ${wide ? "wide" : ""}`}
      aria-label={title}
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onClose();
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget && !busy) onClose();
      }}
    >
      <div className="modal-inner">
        <header className="modal-heading">
          <div>
            <h2>{title}</h2>
            {description && <p>{description}</p>}
          </div>
          <button
            type="button"
            className="icon-button"
            aria-label="关闭对话框"
            disabled={busy}
            onClick={onClose}
          >
            <X size={19} />
          </button>
        </header>
        {children}
      </div>
    </dialog>
  );
}
export function FormFooter({
  onClose,
  pending,
  label = "保存",
  danger = false,
  disabled = false,
}: {
  onClose: () => void;
  pending: boolean;
  label?: string;
  danger?: boolean;
  disabled?: boolean;
}) {
  return (
    <footer className="form-footer">
      <Button type="button" onClick={onClose} disabled={pending}>
        取消
      </Button>
      <Button
        type="submit"
        variant={danger ? "danger" : "primary"}
        loading={pending}
        disabled={disabled}
      >
        {label}
      </Button>
    </footer>
  );
}
export function ConfirmDialog({
  title,
  description,
  label = "确认",
  danger = false,
  onClose,
  onConfirm,
}: {
  title: string;
  description: string;
  label?: string;
  danger?: boolean;
  onClose: () => void;
  onConfirm: () => Promise<void>;
}) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  return (
    <Modal
      title={title}
      description={description}
      onClose={onClose}
      busy={pending}
    >
      <form
        onSubmit={async (event) => {
          event.preventDefault();
          setPending(true);
          setError("");
          try {
            await onConfirm();
            onClose();
          } catch (error) {
            setError(error instanceof Error ? error.message : "操作失败");
          } finally {
            setPending(false);
          }
        }}
      >
        <Alert>{error}</Alert>
        <FormFooter
          onClose={onClose}
          pending={pending}
          label={label}
          danger={danger}
        />
      </form>
    </Modal>
  );
}
export function CopyButton({
  value,
  label = "复制",
}: {
  value: string;
  label?: string;
}) {
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState(false);
  useEffect(() => {
    if (copied) {
      const timeout = setTimeout(() => setCopied(false), 1800);
      return () => clearTimeout(timeout);
    }
  }, [copied]);
  return (
    <button
      type="button"
      className="icon-button copy"
      title={error ? "复制失败，请手动选择复制" : copied ? "已复制" : label}
      aria-label={copied ? "已复制" : label}
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(value);
          setCopied(true);
          setError(false);
        } catch {
          setError(true);
        }
      }}
    >
      {copied ? <Check size={15} /> : <Copy size={15} />}
      <span className="sr-only">{error ? "复制失败，请手动复制" : ""}</span>
    </button>
  );
}
export function Id({ value, full = false }: { value: string; full?: boolean }) {
  return (
    <span className="id-wrap">
      <code title={value}>{full ? value : shortId(value)}</code>
      <CopyButton value={value} label="复制 ID" />
    </span>
  );
}
export function shortId(value: string) {
  return value.length > 20 ? `${value.slice(0, 8)}…${value.slice(-5)}` : value;
}
export const roleLabels: Record<string, string> = {
  OWNER: "所有者",
  ADMIN: "管理员",
  MEMBER: "成员",
  READER: "只读成员",
};
export function Badge({
  tone = "neutral",
  children,
}: {
  tone?: "neutral" | "blue" | "green" | "amber" | "red";
  children: ReactNode;
}) {
  return (
    <span className={`badge ${tone}`}>
      <span />
      {children}
    </span>
  );
}
export function RoleBadge({ role }: { role: string }) {
  return (
    <Badge
      tone={role === "OWNER" ? "blue" : role === "ADMIN" ? "green" : "neutral"}
    >
      {roleLabels[role] || role}
    </Badge>
  );
}
export function ClientStatus({
  approval,
  enabled,
}: {
  approval: string;
  enabled: boolean;
}) {
  return !enabled ? (
    <Badge>已停用</Badge>
  ) : approval === "PENDING" ? (
    <Badge tone="amber">待审批</Badge>
  ) : approval === "REJECTED" ? (
    <Badge tone="red">已拒绝</Badge>
  ) : (
    <Badge tone="green">已批准</Badge>
  );
}
export function ScopePicker({
  catalog,
  selected,
  onChange,
}: {
  catalog: Record<string, string>;
  selected: string[];
  onChange: (values: string[]) => void;
}) {
  const scopes = Object.keys(catalog);
  return (
    <div className="scope-picker">
      <div className="scope-toolbar">
        <span>
          功能范围 <b>{selected.length}</b> / {scopes.length}
        </span>
        <button
          type="button"
          className="text-button"
          onClick={() =>
            onChange(selected.length === scopes.length ? [] : scopes)
          }
        >
          {selected.length === scopes.length ? "清空选择" : "选择全部"}
        </button>
      </div>
      <div className="scope-list">
        {scopes.map((scope) => (
          <label
            className={`scope-option ${
              selected.includes(scope) ? "checked" : ""
            }`}
            key={scope}
          >
            <input
              type="checkbox"
              checked={selected.includes(scope)}
              onChange={() =>
                onChange(
                  selected.includes(scope)
                    ? selected.filter((value) => value !== scope)
                    : [...selected, scope]
                )
              }
            />
            <span>
              <strong>{catalog[scope]}</strong>
              <code>{scope}</code>
            </span>
          </label>
        ))}
      </div>
    </div>
  );
}
export function SecretModal({
  title,
  values,
  onClose,
}: {
  title: string;
  values: { label: string; value: string }[];
  onClose: () => void;
}) {
  return (
    <Modal
      title={title}
      description="请妥善保存以下信息，关闭后不再显示明文凭据。"
      onClose={onClose}
    >
      <div className="info-note">
        密钥和邀请凭据仅展示一次，请保存到安全的位置。
      </div>
      <div className="secret-values">
        {values.map((item) => (
          <div key={item.label}>
            <label>{item.label}</label>
            <div>
              <code>{item.value}</code>
              <CopyButton value={item.value} label={`复制${item.label}`} />
            </div>
          </div>
        ))}
      </div>
      <div className="form-footer">
        <Button variant="primary" onClick={onClose}>
          我已保存，关闭
        </Button>
      </div>
    </Modal>
  );
}
const ToastContext = createContext<(message: string) => void>(() => {});
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<{ message: string; id: number } | null>(
    null
  );
  useEffect(() => {
    if (toast) {
      const timeout = setTimeout(() => setToast(null), 4500);
      return () => clearTimeout(timeout);
    }
  }, [toast]);
  return (
    <ToastContext.Provider
      value={(message) => setToast({ message, id: Date.now() })}
    >
      {children}
      {toast && (
        <div className="toast" role="status">
          <CheckCircle2 size={18} />
          {toast.message}
          <button
            className="icon-button"
            aria-label="关闭提示"
            onClick={() => setToast(null)}
          >
            <X size={16} />
          </button>
        </div>
      )}
    </ToastContext.Provider>
  );
}
export const useToast = () => useContext(ToastContext);
export function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "—"
    : new Intl.DateTimeFormat("zh-CN", {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(date);
}
