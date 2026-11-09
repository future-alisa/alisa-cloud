import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, createApi, tenantPath, tokenUserId } from "./api";
const response = (status: number, value: unknown) => ({
  ok: status >= 200 && status < 300,
  status,
  text: async () => (value === undefined ? "" : JSON.stringify(value)),
});
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs(); });
describe("API authorization boundaries", () => {
  it("expires 401 sessions but preserves sessions on permission denial", async () => {
    const logout = vi.fn();
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(response(403, { message: "禁止访问" }))
        .mockResolvedValueOnce(response(401, {}))
    );
    const api = createApi("signed-token", logout);
    await expect(api("/api/private")).rejects.toMatchObject({ status: 403 });
    expect(logout).not.toHaveBeenCalled();
    await expect(api("/api/private")).rejects.toMatchObject({ status: 401 });
    expect(logout).toHaveBeenCalledOnce();
  });
  it("keeps tokens out of URLs and handles empty successful responses", async () => {
    const fetch = vi.fn().mockResolvedValue(response(204, undefined));
    vi.stubGlobal("fetch", fetch);
    await expect(
      createApi("secret", vi.fn())("/api/action", {
        method: "DELETE",
        body: { id: "a" },
      })
    ).resolves.toBeUndefined();
    expect(fetch.mock.calls[0][0]).toBe("/api/action");
    expect(fetch.mock.calls[0][1]).toMatchObject({
      headers: { Authorization: "Bearer secret" },
      cache: "no-store",
      credentials: "omit",
    });
    await expect(
      createApi("secret", vi.fn())("https://untrusted.example")
    ).rejects.toThrow("Invalid API path");
  });
  it("encodes tenant paths and tolerates malformed display claims", () => {
    expect(tenantPath("tenant/a", "/members")).toBe(
      "/api/v1/user/tenants/tenant%2Fa/members"
    );
    expect(tokenUserId("bad")).toBe("");
  });
  it("keeps API requests under the deployed admin prefix", async () => {
    vi.stubEnv("BASE_URL", "/admin/");
    const fetch = vi.fn().mockResolvedValue(response(200, {}));
    vi.stubGlobal("fetch", fetch);
    await createApi("signed-token", vi.fn())("/api/v1/user/tenants");
    expect(fetch.mock.calls[0][0]).toBe("/admin/api/v1/user/tenants");
  });
});
