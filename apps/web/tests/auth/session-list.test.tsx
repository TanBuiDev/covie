import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { SessionList } from "../../features/auth/session-list";
import { invalidateCsrfToken } from "@/features/auth/csrf";

afterEach(() => {
  invalidateCsrfToken();
  vi.unstubAllGlobals();
});

it.each([{ sessions: [] }, { sessions: [{ id: "current", deviceName: "Máy hiện tại", current: true }] }])("does not offer revoke-others when no other session exists: %j", ({ sessions }) => {
  render(<SessionList sessions={sessions} />);
  expect(screen.getByRole("button", { name: "Thu hồi các phiên khác" })).toBeDisabled();
  if (sessions.length === 0) expect(screen.getByText("Không có phiên đăng nhập đang hoạt động.")).toBeInTheDocument();
});

it("retries only the session read after a successful revoke and failed refresh",async()=>{
  let deletes=0;let reads=0;
  vi.stubGlobal("fetch",vi.fn(async(request:Request|string)=>{
    const url=typeof request === "string"?request:request.url;
    if(url.endsWith("/csrf"))return new Response(JSON.stringify({headerName:"X-CSRF-Token",token:"fixture"}));
    if(request instanceof Request && request.method === "DELETE"){deletes++;return new Response(null,{status:204});}
    reads++;
    return reads===1?new Response(JSON.stringify({error:{code:"ERROR"}}),{status:503}):new Response("[]");
  }));
  render(<SessionList sessions={[{id:"a",deviceName:"Máy A"}]}/>);
  fireEvent.click(screen.getByRole("button",{name:"Thu hồi Máy A"}));
  const retry=await screen.findByRole("button",{name:"Thử lại tải phiên"});
  expect(screen.getByRole("button",{name:"Thu hồi Máy A"})).toBeDisabled();
  fireEvent.click(retry);
  await waitFor(()=>expect(screen.queryByText("Máy A")).not.toBeInTheDocument());
  expect(deletes).toBe(1);expect(reads).toBe(2);
});

it.each(["command","refresh"])("offers login after session %s returns401",async source=>{
  vi.stubGlobal("fetch",vi.fn(async(request:Request|string)=>{
    const url=typeof request === "string"?request:request.url;
    if(url.endsWith("/csrf"))return new Response(JSON.stringify({headerName:"X-CSRF-Token",token:"fixture"}));
    if(source === "refresh" && request instanceof Request && request.method === "DELETE")return new Response(null,{status:204});
    return new Response(JSON.stringify({error:{code:"AUTH_REQUIRED",message:"private diagnostic"}}),{status:401});
  }));
  render(<SessionList sessions={[{id:"a",deviceName:"Máy A"}]}/>);
  fireEvent.click(screen.getByRole("button",{name:"Thu hồi Máy A"}));
  expect(await screen.findByRole("link",{name:"Đăng nhập lại"})).toHaveAttribute("href","/login?returnTo=%2Fme%2Fsecurity%2Fsessions");
  expect(screen.queryByText("Máy A")).not.toBeInTheDocument();
  expect(screen.queryByText("private diagnostic")).not.toBeInTheDocument();
});

it("serializes revoke requests in the same batch",async()=>{
  let deletes=0;
  vi.stubGlobal("fetch",vi.fn(async(request:Request)=>{
    if(request.method==="DELETE"){deletes++;return new Promise<Response>(()=>{});}
    return new Response(JSON.stringify({headerName:"X-CSRF-Token",token:"fixture"}));
  }));
  render(<SessionList sessions={[{id:"a",deviceName:"Máy A"},{id:"b",deviceName:"Máy B"}]}/>);
  act(()=>{screen.getByRole("button",{name:"Thu hồi Máy A"}).click();screen.getByRole("button",{name:"Thu hồi Máy B"}).click();});
  await waitFor(()=>expect(deletes).toBeGreaterThan(0));
  expect(deletes).toBe(1);
});

it("identifies the current session and refetches only after revoking another session", async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: "X-CSRF-Token", token: "csrf-token" }), { status: 200, headers: { "content-type": "application/json" } }))
    .mockResolvedValueOnce(new Response(null, { status: 204 }))
    .mockResolvedValueOnce(new Response(JSON.stringify([
      {
        id: "00000000-0000-0000-0000-000000000001",
        deviceName: "This browser",
        current: true,
        createdAt: "2026-08-10T00:00:00Z",
        lastUsedAt: "2026-08-10T00:00:00Z",
        expiresAt: "2026-09-10T00:00:00Z",
      },
    ]), { status: 200, headers: { "content-type": "application/json" } }));
  vi.stubGlobal("fetch", fetchMock);

  render(<SessionList sessions={[
    {
      id: "00000000-0000-0000-0000-000000000001",
      deviceName: "This browser",
      current: true,
      createdAt: "2026-08-10T00:00:00Z",
      lastUsedAt: "2026-08-10T00:00:00Z",
      expiresAt: "2026-09-10T00:00:00Z",
    },
    {
      id: "00000000-0000-0000-0000-000000000002",
      deviceName: "Other browser",
      current: false,
      createdAt: "2026-08-09T00:00:00Z",
      lastUsedAt: "2026-08-09T00:00:00Z",
      expiresAt: "2026-09-09T00:00:00Z",
    },
  ]} />);

  expect(screen.getByText("Thiết bị hiện tại")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Thu hồi Other browser" }));

  await waitFor(() => expect(screen.queryByText("Other browser")).not.toBeInTheDocument());
  expect(fetchMock).toHaveBeenCalledTimes(3);
  const deleteRequest = fetchMock.mock.calls[1]?.[0];
  const listRequest = fetchMock.mock.calls[2]?.[0];
  expect(deleteRequest).toBeInstanceOf(Request);
  expect(listRequest).toBeInstanceOf(Request);
  if (!(deleteRequest instanceof Request) || !(listRequest instanceof Request)) throw new Error("Expected request objects");
  expect(deleteRequest.method).toBe("DELETE");
  expect(listRequest.method).toBe("GET");
});
