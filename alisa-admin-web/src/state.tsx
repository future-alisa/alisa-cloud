import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { createApi, tokenUserId } from "./api";
import type { Result, Session, Tenant } from "./types";
const sessionKey = "alisa.admin.session";
const signedOutKey = "alisa.admin.signed-out";
const adminBase = import.meta.env.BASE_URL;
export const sharedSignInUrl = adminBase === "/admin/" ? adminBase + "auth/oauth/admin/continue" : "";

const SessionContext = createContext<ReturnType<typeof useSessionState> | null>(
  null
);
function readSession(): Session | null {
  try {
    const value = JSON.parse(
      sessionStorage.getItem(sessionKey) || "null"
    );
    return value &&
      typeof value.token === "string" &&
      typeof value.username === "string"
      ? { ...value, userId: tokenUserId(value.token) }
      : null;
  } catch {
    return null;
  }
}
function useSessionState() {
  const [session, setSession] = useState<Session | null>(readSession);
  const logout = useCallback(() => {
    sessionStorage.removeItem(sessionKey);
    sessionStorage.setItem(signedOutKey, "1");
    setSession(null);
  }, []);
  const api = useMemo(
    () => createApi(session?.token || "", logout),
    [session?.token, logout]
  );
  const [checkingSession, setCheckingSession] = useState(
    () => !!sharedSignInUrl && !session && !sessionStorage.getItem(signedOutKey)
  );
  const saveSession = useCallback((username: string, token: string) => {
    const value = { username, token, userId: tokenUserId(token) };
    if (!value.userId) throw new Error("无法确认登录身份");
    sessionStorage.setItem(sessionKey, JSON.stringify(value));
    sessionStorage.removeItem(signedOutKey);
    setSession(value);
  }, []);

  useEffect(() => {
    if (!checkingSession) return;
    const controller = new AbortController();
    const url = adminBase + "auth/oauth/admin/session";
    const options = { signal: controller.signal, cache: "no-store" as const, redirect: "error" as const };
    const timer = window.setTimeout(() => controller.abort(), 5000);
    let active = true;
    void (async () => {
      try {
        const challenge = await fetch(url, options);
        if (!challenge.ok) return;
        const { header, csrf } = await challenge.json();
        if (header !== "X-CSRF-TOKEN" || typeof csrf !== "string" || !csrf) return;
        const response = await fetch(url, { ...options, method: "POST", headers: { [header]: csrf } });
        if (!response.ok) return;
        const { username, token } = await response.json();
        if (active && typeof username === "string" && typeof token === "string") saveSession(username, token);
      } catch {
        // The login screen remains available if the shared session cannot be used.
      } finally {
        if (active) setCheckingSession(false);
        window.clearTimeout(timer);
      }
    })();
    return () => {
      active = false;
      controller.abort();
      window.clearTimeout(timer);
    };
  }, [checkingSession, saveSession]);

  const login = async (username: string, password: string) => {
    const result = await createApi("", () => {})<Result<string>>(
      "/api/v1/user/signin",
      { method: "POST", body: { username, password } }
    );
    if (result.code !== 200)
      throw new Error(result.message || "登录失败，请稍后重试");
    if (typeof result.data !== "string" || !result.data.trim())
      throw new Error("登录失败，请检查账号和密码");
    saveSession(username, result.data);
  };
  return { session, api, login, logout, checkingSession };
}
export function SessionProvider({ children }: { children: ReactNode }) {
  const value = useSessionState();
  return (
    <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
  );
}
export function useSession() {
  const value = useContext(SessionContext);
  if (!value) throw new Error("SessionProvider missing");
  return value;
}
export function useQuery<T>(path: string | null) {
  const { api } = useSession();
  const [nonce, setNonce] = useState(0);
  const [state, setState] = useState<{
    key: string | null;
    data?: T;
    loading: boolean;
    error?: string;
  }>({ key: path, loading: !!path });
  const reload = useCallback(() => setNonce((value) => value + 1), []);
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    if (!path) {
      setState({ key: null, loading: false });
      return;
    }
    setState((previous) => ({
      key: path,
      data: previous.key === path ? previous.data : undefined,
      loading: true,
    }));
    api<T>(path, { signal: controller.signal })
      .then((data) => {
        if (active) setState({ key: path, data, loading: false });
      })
      .catch((error) => {
        if (active && error.name !== "AbortError")
          setState((previous) => ({
            key: path,
            data: previous.key === path ? previous.data : undefined,
            loading: false,
            error: error.message || "请求失败",
          }));
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [api, path, nonce]);
  return {
    ...(state.key === path ? state : { key: path, loading: !!path }),
    reload,
  };
}
export function useAction() {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const run = async (task: () => Promise<void>) => {
    if (pending) return false;
    setError("");
    setPending(true);
    try {
      await task();
      return true;
    } catch (error) {
      setError(error instanceof Error ? error.message : "操作失败");
      return false;
    } finally {
      setPending(false);
    }
  };
  return { pending, error, setError, run };
}
const TenantContext = createContext<{
  tenants: Tenant[];
  selected: Tenant | null;
  selectedId: string;
  select: (id: string) => void;
  reload: () => void;
  loading: boolean;
  error?: string;
} | null>(null);
export function TenantProvider({ children }: { children: ReactNode }) {
  const query = useQuery<Tenant[]>("/api/v1/user/tenants");
  const { session } = useSession();
  const storageKey = `alisa.admin.tenant.${session?.userId}`;
  const [selectedId, setSelectedId] = useState(
    () => sessionStorage.getItem(storageKey) || ""
  );
  const tenants = query.data || [];
  useEffect(() => {
    if (query.data && !tenants.some((tenant) => tenant.id === selectedId)) {
      setSelectedId(
        tenants.find((tenant) => tenant.enabled)?.id || tenants[0]?.id || ""
      );
    }
  }, [query.data, selectedId]);
  const select = useCallback(
    (id: string) => {
      setSelectedId(id);
      sessionStorage.setItem(storageKey, id);
    },
    [storageKey]
  );
  const selected = tenants.find((tenant) => tenant.id === selectedId) || null;
  return (
    <TenantContext.Provider
      value={{
        tenants,
        selected,
        selectedId,
        select,
        reload: query.reload,
        loading: query.loading && !query.data,
        error: query.error,
      }}
    >
      {children}
    </TenantContext.Provider>
  );
}
export function useTenants() {
  const value = useContext(TenantContext);
  if (!value) throw new Error("TenantProvider missing");
  return value;
}
export const isAdmin = (tenant: Tenant | null) =>
  !!tenant && ["OWNER", "ADMIN"].includes(tenant.role);
