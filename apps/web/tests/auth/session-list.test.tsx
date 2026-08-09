import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { SessionList } from "../../features/auth/session-list";

afterEach(() => {
  vi.unstubAllGlobals();
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
