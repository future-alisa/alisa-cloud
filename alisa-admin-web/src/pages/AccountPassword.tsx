import { useState, type FormEvent } from "react";
import { ArrowLeft, CheckCircle2, KeyRound } from "lucide-react";
import { Link } from "react-router-dom";
import { createApi } from "../api";
import { Alert, Button, FormFooter, Modal } from "../ui";

const api = createApi("", () => {});
const root = "/api/v1/user";
type PasswordResult = { code: number; data: boolean };

function newPassword(fields: FormData) {
  const password = String(fields.get("newPassword") ?? "");
  if (password.length < 12 || new TextEncoder().encode(password).length > 72) {
    throw new Error("新密码至少 12 个字符，且最多 72 字节");
  }
  if (password !== fields.get("confirmPassword")) throw new Error("两次输入的新密码不一致");
  return password;
}

function NewPasswordFields() {
  return <>
    <label>新密码<input name="newPassword" required type="password" autoComplete="new-password" minLength={12} /></label>
    <label>确认新密码<input name="confirmPassword" required type="password" autoComplete="new-password" minLength={12} /></label>
  </>;
}

export function ChangePassword({ username, onClose, onChanged }: {
  username: string;
  onClose: () => void;
  onChanged: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const fields = new FormData(event.currentTarget);
    setError("");
    setBusy(true);
    try {
      const result = await api<PasswordResult>(root + "/change-password", { method: "POST", body: {
        username, currentPassword: fields.get("currentPassword"), newPassword: newPassword(fields),
      } });
      if (result.code !== 200 || result.data !== true) throw new Error("当前密码不正确，请重试");
      onChanged();
    } catch (error) {
      setError(error instanceof Error ? error.message : "修改密码失败");
    } finally {
      setBusy(false);
    }
  }

  return <Modal title="修改密码" description={`当前账号 ${username}。保存后需要重新登录。`} onClose={onClose} busy={busy}>
    <form onSubmit={submit}>
      <label>当前密码<input name="currentPassword" required type="password" autoComplete="current-password" /></label>
      <NewPasswordFields />
      <p className="muted">新密码至少 12 个字符，最多 72 字节。</p>
      <Alert>{error}</Alert>
      <FormFooter onClose={onClose} pending={busy} label="保存新密码" />
    </form>
  </Modal>;
}

export function ResetPassword() {
  const [email, setEmail] = useState("");
  const [sentTo, setSentTo] = useState("");
  const [requestedCode, setRequestedCode] = useState(false);
  const [busy, setBusy] = useState<"send" | "reset">();
  const [error, setError] = useState("");
  const [done, setDone] = useState(false);

  async function send() {
    setBusy("send");
    setError("");
    try {
      await api(root + "/send", { method: "POST", body: { email: email.trim() } });
      setSentTo(email.trim());
      setRequestedCode(true);
    } catch (error) {
      setError(error instanceof Error ? error.message : "发送验证码失败");
    } finally {
      setBusy(undefined);
    }
  }

  async function reset(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!sentTo) return;
    const fields = new FormData(event.currentTarget);
    setBusy("reset");
    setError("");
    try {
      const result = await api<PasswordResult>(root + "/change-password-email", { method: "POST", body: {
        email: sentTo, code: fields.get("code"), newPassword: newPassword(fields),
      } });
      if (result.code !== 200 || result.data !== true) throw new Error("验证码无效或已过期，请重新获取");
      setDone(true);
    } catch (error) {
      setError(error instanceof Error ? error.message : "重置密码失败");
    } finally {
      setBusy(undefined);
    }
  }

  return <main className="login-panel password-recovery">
    <div className="login-form recovery-card">
      <div className="recovery-icon">{done ? <CheckCircle2 size={22} /> : <KeyRound size={22} />}</div>
      <div className="eyebrow">ALISA ACCOUNT</div>
      <h2>重置 Alisa 账号密码</h2>
      {done ? <p role="status">密码已重置，请使用新密码登录。</p> : <>
        <p>{sentTo ? "第 2 步，共 2 步 · 验证邮箱并设置新密码" : "第 1 步，共 2 步 · 输入注册邮箱"}</p>
        {!sentTo ? <form onSubmit={event => { event.preventDefault(); void send(); }}>
          <label>注册邮箱<input type="email" autoComplete="email" required value={email}
            onChange={event => setEmail(event.target.value)} /></label>
          <Button type="submit" variant="primary" loading={busy === "send"}>发送验证码</Button>
        </form> : <form onSubmit={reset}>
          <div className="recovery-notice" role="status">
            {requestedCode ? <>若邮箱已注册，验证码将发送到 <strong>{sentTo}</strong>。</>
              : <>请输入发到 <strong>{sentTo}</strong> 的验证码。</>}
          </div>
          <label>邮箱验证码<input name="code" autoComplete="one-time-code" inputMode="numeric" pattern="[0-9]{6}" required /></label>
          <NewPasswordFields />
          <Button type="submit" variant="primary" loading={busy === "reset"} disabled={!!busy}>重置密码</Button>
          <div className="recovery-actions">
            <Button variant="ghost" type="button" disabled={!!busy} onClick={() => void send()}>重新发送</Button>
            <Button variant="ghost" type="button" disabled={!!busy} onClick={() => {
              setSentTo("");
              setRequestedCode(false);
              setError("");
            }}>更换邮箱</Button>
          </div>
        </form>}
        {!sentTo && <Button variant="ghost" disabled={!!busy || !email.trim()}
          onClick={() => { setSentTo(email.trim()); setError(""); }}>我已有验证码</Button>}
        <Alert>{error}</Alert>
      </>}
      <Link className="recovery-back" to="/login"><ArrowLeft size={15} />返回登录</Link>
    </div>
  </main>;
}
