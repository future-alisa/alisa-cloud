// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { SessionProvider, useQuery } from "./state";
import App from "./App";
import "@testing-library/jest-dom/vitest";
const jwt = `header.${btoa(JSON.stringify({ userId: "user-1" }))}.signature`;
const session = { username: "admin", userId: "user-1", token: jwt };
const respond = (value: unknown, status = 200) =>
  Promise.resolve({
    ok: status >= 200 && status < 300,
    status,
    text: async () => (value === undefined ? "" : JSON.stringify(value)),
  });
beforeEach(() => {
  sessionStorage.clear();
  Object.defineProperty(HTMLDialogElement.prototype, "showModal", {
    configurable: true,
    value: function () {
      this.setAttribute("open", "");
    },
  });
  Object.defineProperty(HTMLDialogElement.prototype, "close", {
    configurable: true,
    value: function () {
      this.removeAttribute("open");
    },
  });
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});
it("logs in and creates a tenant without embedding business scopes in the UI", async () => {
  let created = false;
  vi.stubGlobal(
    "fetch",
    vi.fn((path: string, options: RequestInit) => {
      if (path.endsWith("/signin")) return respond({ code: 200, data: jwt });
      if (path === "/api/v1/user/tenants" && options.method === "POST") {
        created = true;
        return respond({ tenantId: "tenant-1" });
      }
      if (path === "/api/v1/user/tenants")
        return respond(
          created
            ? [
                {
                  id: "tenant-1",
                  name: "研发团队",
                  role: "OWNER",
                  enabled: true,
                },
              ]
            : []
        );
      if (path.endsWith("/members"))
        return respond([{ userId: "user-1", role: "OWNER" }]);
      if (path.endsWith("/applications")) return respond([]);
      return respond({});
    })
  );
  render(
    <MemoryRouter initialEntries={["/login"]}>
      <SessionProvider>
        <App />
      </SessionProvider>
    </MemoryRouter>
  );
  fireEvent.change(screen.getByLabelText("账号"), {
    target: { value: "admin" },
  });
  fireEvent.change(screen.getByLabelText("密码"), {
    target: { value: "never-store-this" },
  });
  fireEvent.click(screen.getByRole("button", { name: /登录控制台/ }));
  await screen.findByText("所有协作，从一个空间开始");
  expect(sessionStorage.getItem("alisa.admin.session")).not.toContain(
    "never-store-this"
  );
  fireEvent.click(screen.getByRole("link", { name: /创建或加入租户/ }));
  fireEvent.click(screen.getAllByRole("button", { name: "创建租户" })[0]);
  const dialog = screen.getByRole("dialog", { name: "创建租户" });
  fireEvent.change(within(dialog).getByLabelText(/租户名称/), {
    target: { value: "研发团队" },
  });
  fireEvent.click(within(dialog).getByRole("button", { name: "创建租户" }));
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "切换租户" })).toHaveTextContent("研发团队")
  );
  expect(screen.getByText("租户已创建")).toBeInTheDocument();
});
it("shows a credential error when signin returns success without a token", async () => {
  vi.stubGlobal("fetch", vi.fn(() => respond({ code: 200, message: "成功", data: "" })));
  render(
    <MemoryRouter initialEntries={["/login"]}>
      <SessionProvider>
        <App />
      </SessionProvider>
    </MemoryRouter>
  );
  fireEvent.change(screen.getByLabelText("账号"), { target: { value: "alisa" } });
  fireEvent.change(screen.getByLabelText("密码"), { target: { value: "invalid-password" } });
  fireEvent.click(screen.getByRole("button", { name: /登录控制台/ }));
  expect(await screen.findByText("登录失败，请检查账号和密码")).toBeInTheDocument();
  expect(sessionStorage.getItem("alisa.admin.session")).toBeNull();
});
it("shows a permission explanation for ordinary members without fetching admin policies", async () => {
  sessionStorage.setItem("alisa.admin.session", JSON.stringify(session));
  const fetch = vi.fn((path: string) =>
    respond(
      path === "/api/v1/user/tenants"
        ? [{ id: "tenant-1", name: "团队", enabled: true, role: "MEMBER" }]
        : []
    )
  );
  vi.stubGlobal("fetch", fetch);
  render(
    <MemoryRouter initialEntries={["/permissions"]}>
      <SessionProvider>
        <App />
      </SessionProvider>
    </MemoryRouter>
  );
  await screen.findByText("此页面需要管理员权限");
  expect(fetch.mock.calls.some(([path]) => path.endsWith("/permissions"))).toBe(
    false
  );
});
it("ignores old tenant responses even when a transport does not honor abort", async () => {
  sessionStorage.setItem("alisa.admin.session", JSON.stringify(session));
  let resolveOld!: (value: unknown) => void;
  vi.stubGlobal(
    "fetch",
    vi.fn((path: string) =>
      path === "/api/a"
        ? new Promise((resolve) => {
            resolveOld = resolve;
          })
        : respond({ name: "tenant B" })
    )
  );
  function Probe({ path }: { path: string }) {
    const query = useQuery<{ name: string }>(path);
    return <div>{query.data?.name || "loading"}</div>;
  }
  const tree = render(
    <SessionProvider>
      <Probe path="/api/a" />
    </SessionProvider>
  );
  tree.rerender(
    <SessionProvider>
      <Probe path="/api/b" />
    </SessionProvider>
  );
  await screen.findByText("tenant B");
  resolveOld({
    ok: true,
    status: 200,
    text: async () => JSON.stringify({ name: "tenant A" }),
  });
  await waitFor(() =>
    expect(screen.queryByText("tenant A")).not.toBeInTheDocument()
  );
  expect(screen.getByText("tenant B")).toBeInTheDocument();
});
it("uses an existing email code and keeps the recovery form open when the code is rejected", async () => {
  const request = vi.fn(() => respond({ code: 200, data: false }));
  vi.stubGlobal("fetch", request);
  render(
    <MemoryRouter initialEntries={["/login"]}>
      <SessionProvider><App /></SessionProvider>
    </MemoryRouter>
  );
  fireEvent.click(screen.getByRole("link", { name: "忘记密码？" }));
  fireEvent.change(screen.getByLabelText("注册邮箱"), { target: { value: "managed@example.org" } });
  fireEvent.click(screen.getByRole("button", { name: "我已有验证码" }));
  expect(request).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText("邮箱验证码"), { target: { value: "123456" } });
  fireEvent.change(screen.getByLabelText("新密码", { exact: true }), { target: { value: "Test password 42" } });
  fireEvent.change(screen.getByLabelText("确认新密码"), { target: { value: "Test password 42" } });
  fireEvent.click(screen.getByRole("button", { name: "重置密码" }));
  expect(await screen.findByText("验证码无效或已过期，请重新获取")).toBeInTheDocument();
  expect(request).toHaveBeenCalledWith("/api/v1/user/change-password-email", expect.objectContaining({
    method: "POST",
    body: JSON.stringify({ email: "managed@example.org", code: "123456", newPassword: "Test password 42" }),
  }));
});
