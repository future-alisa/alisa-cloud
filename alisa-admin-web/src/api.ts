export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
    this.name = "ApiError";
  }
}
export function createApi(token: string, onUnauthorized: () => void) {
  return async function request<T>(
    path: string,
    options: { method?: string; body?: unknown; signal?: AbortSignal } = {}
  ): Promise<T> {
    if (!path.startsWith("/api/")) throw new Error("Invalid API path");
    let response: Response;
    try {
      const base = import.meta.env.BASE_URL;
      if (!base.startsWith("/") || base.startsWith("//")) throw new Error("Invalid application base");
      response = await fetch(base.replace(/\/$/, "") + path, {
        method: options.method || "GET",
        headers: {
          Accept: "application/json",
          ...(options.body === undefined
            ? {}
            : { "Content-Type": "application/json" }),
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body:
          options.body === undefined ? undefined : JSON.stringify(options.body),
        signal: options.signal,
        credentials: "omit",
        cache: "no-store",
      });
    } catch (error) {
      if (error instanceof Error && error.name === "AbortError") throw error;
      throw new ApiError(0, "无法连接服务，请检查网络或 API 代理配置");
    }
    if (response.status === 401 && token) onUnauthorized();
    const text = await response.text();
    let value: unknown;
    try {
      value = text ? JSON.parse(text) : undefined;
    } catch {
      throw new ApiError(
        response.status,
        "服务返回了无法识别的响应，请检查 API 代理配置"
      );
    }
    if (!response.ok) {
      const payload = value as
        | { message?: string; detail?: string }
        | undefined;
      const defaults: Record<number, string> = {
        401: "登录已失效，请重新登录",
        403: "你没有执行此操作的权限",
        404: "记录不存在或已被移除",
        409: "当前状态不允许此操作",
        503: "服务暂不可用，请稍后重试",
      };
      throw new ApiError(
        response.status,
        payload?.message ||
          payload?.detail ||
          defaults[response.status] ||
          `请求失败（${response.status}）`
      );
    }
    return value as T;
  };
}
export const tenantPath = (id: string, suffix = "") =>
  `/api/v1/user/tenants/${encodeURIComponent(id)}${suffix}`;
/** Display only. Authentication and authorization are always checked by the server. */
export function tokenUserId(token: string): string {
  try {
    const base64 = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const bytes = Uint8Array.from(atob(base64), (char) => char.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes)).userId || "";
  } catch {
    return "";
  }
}
