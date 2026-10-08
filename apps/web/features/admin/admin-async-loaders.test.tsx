import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AdminAuditLog } from "./admin-audit-log";
import { AdminEntityTable } from "./admin-entity-table";
import { AdminModerationDetail } from "./admin-moderation-detail";
import { AdminOrderHistory } from "./admin-order-history";
import { AdminOrdersExplorer } from "./admin-orders-explorer";
import { AdminReadOnlyCollection } from "./admin-readonly-collection";
import { AdminReportsQueue } from "./admin-reports-queue";
import { AdminSettingsScreen } from "./admin-settings-screen";

const mocks = vi.hoisted(() => ({
  requestBrowserApi: vi.fn(),
  loadDefinitions: vi.fn(),
  loadDetail: vi.fn(),
  updateSetting: vi.fn(),
}));

vi.mock("@/lib/api/api-client", () => ({ requestBrowserApi: mocks.requestBrowserApi }));
vi.mock("./admin-api", () => ({
  loadAdminSettingDefinitions: mocks.loadDefinitions,
  loadAdminSettingDetail: mocks.loadDetail,
  updateGlobalAdminSetting: mocks.updateSetting,
}));

function jsonResponse(payload: unknown, ok = true) {
  return {
    ok,
    json: vi.fn().mockResolvedValue(payload),
    headers: new Headers(),
  } as unknown as Response;
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => { resolve = done; });
  return { promise, resolve };
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("admin asynchronous loaders", () => {
  it("starts every admin loader and renders its resolved state", async () => {
    const cases = [
      {
        render: () => render(<AdminAuditLog />),
        responses: [jsonResponse({ content: [] })],
        expected: /Không có mục phù hợp bộ lọc/,
      },
      {
        render: () => render(<AdminEntityTable type="users" title="Người dùng" columns={["id"]} />),
        responses: [jsonResponse({ items: [] })],
        expected: /Chưa có dữ liệu/,
      },
      {
        render: () => render(<AdminModerationDetail caseId="case-1" />),
        responses: [jsonResponse({ moderationCase: { id: "case-1", status: "OPEN" }, actions: [] })],
        expected: /Chưa có hành động trong hồ sơ/,
      },
      {
        render: () => render(<AdminOrderHistory orderId="order-1" />),
        responses: [jsonResponse([]), jsonResponse({ id: "order-1", orderCode: "CV-1" })],
        expected: /Chưa có sự kiện lịch sử/,
      },
      {
        render: () => render(<AdminOrdersExplorer />),
        responses: [jsonResponse({ items: [] })],
        expected: /Không tìm thấy đơn phù hợp/,
      },
      {
        render: () => render(<AdminReadOnlyCollection title="Danh mục" subtitle="Chỉ đọc" path="/api/test" columns={[{ key: "id", label: "ID" }]} note="Contract" />),
        responses: [jsonResponse([])],
        expected: /Chưa có dữ liệu/,
      },
      {
        render: () => render(<AdminReportsQueue />),
        responses: [jsonResponse({ content: [] })],
        expected: /Không có hồ sơ đang mở/,
      },
    ];

    for (const testCase of cases) {
      mocks.requestBrowserApi.mockResolvedValueOnce(testCase.responses[0]);
      if (testCase.responses[1]) mocks.requestBrowserApi.mockResolvedValueOnce(testCase.responses[1]);
      const view = testCase.render();
      await waitFor(() => expect(screen.getByText(testCase.expected)).toBeInTheDocument());
      view.unmount();
      mocks.requestBrowserApi.mockClear();
    }

    mocks.loadDefinitions.mockResolvedValueOnce([]);
    render(<AdminSettingsScreen />);
    await waitFor(() => expect(mocks.loadDefinitions).toHaveBeenCalledTimes(1));
    expect(screen.queryByText("Đang tải cấu hình…")).not.toBeInTheDocument();
  });

  it("reloads orders for a submitted filter and retries an audit failure", async () => {
    mocks.requestBrowserApi.mockResolvedValueOnce(jsonResponse({ items: [] }));
    const orders = render(<AdminOrdersExplorer />);
    await screen.findByText(/Không tìm thấy đơn phù hợp/);
    fireEvent.change(screen.getByLabelText("Mã đơn"), { target: { value: "CV2048" } });
    mocks.requestBrowserApi.mockResolvedValueOnce(jsonResponse({ items: [] }));
    fireEvent.click(screen.getByRole("button", { name: "Tìm kiếm" }));
    await waitFor(() => expect(mocks.requestBrowserApi).toHaveBeenLastCalledWith("GET", expect.stringContaining("orderCode=CV2048")));
    orders.unmount();

    mocks.requestBrowserApi.mockRejectedValueOnce(new Error("Mất kết nối"));
    render(<AdminAuditLog />);
    await screen.findByRole("alert");
    mocks.requestBrowserApi.mockResolvedValueOnce(jsonResponse({ content: [] }));
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    await screen.findByText(/Không có mục phù hợp bộ lọc/);
  });

  it("ignores an obsolete collection response after the path changes", async () => {
    const first = deferred<Response>();
    const second = deferred<Response>();
    mocks.requestBrowserApi.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);

    const view = render(<AdminReadOnlyCollection title="Dữ liệu" subtitle="Chỉ đọc" path="/api/first" columns={[{ key: "name", label: "Tên" }]} note="Contract" />);
    await waitFor(() => expect(mocks.requestBrowserApi).toHaveBeenCalledWith("GET", "/api/first"));
    view.rerender(<AdminReadOnlyCollection title="Dữ liệu" subtitle="Chỉ đọc" path="/api/second" columns={[{ key: "name", label: "Tên" }]} note="Contract" />);
    await waitFor(() => expect(mocks.requestBrowserApi).toHaveBeenCalledWith("GET", "/api/second"));

    second.resolve(jsonResponse([{ name: "Mới" }]));
    await screen.findByText("Mới");
    first.resolve(jsonResponse([{ name: "Cũ" }]));
    await Promise.resolve();
    expect(screen.queryByText("Cũ")).not.toBeInTheDocument();
  });

  it("loads every active location-area level for the Admin hierarchy table", async () => {
    mocks.requestBrowserApi.mockImplementation(async (_method: string, path: string) => {
      if (path === "/api/v1/location-areas") return jsonResponse([{ id: "area-1", parentId: null, areaType: "COMMUNITY", name: "TP. Hồ Chí Minh" }]);
      if (path === "/api/v1/location-areas?parentId=area-1") return jsonResponse([{ id: "area-2", parentId: "area-1", areaType: "ZONE", name: "TP. Thủ Đức" }]);
      if (path === "/api/v1/location-areas?parentId=area-2") return jsonResponse([{ id: "area-3", parentId: "area-2", areaType: "WARD", name: "Long Bình" }]);
      if (path === "/api/v1/location-areas?parentId=area-3") return jsonResponse([]);
      throw new Error(`Unexpected path ${path}`);
    });

    render(<AdminReadOnlyCollection title="Khu vực" subtitle="Cộng đồng & khu vực" path="/api/v1/location-areas" expandHierarchy columns={[{ key: "name", label: "Tên" }, { key: "areaType", label: "Loại khu vực" }, { key: "parentId", label: "Khu vực cha" }]} note="Contract" />);

    expect(await screen.findAllByText("TP. Hồ Chí Minh")).toHaveLength(2);
    expect(await screen.findAllByText("TP. Thủ Đức")).toHaveLength(2);
    expect(await screen.findByText("Long Bình")).toBeInTheDocument();
    expect(screen.getByRole("cell", { name: "Cộng đồng" })).toBeInTheDocument();
    expect(screen.getByRole("cell", { name: "Khu vực" })).toBeInTheDocument();
    expect(mocks.requestBrowserApi).toHaveBeenCalledWith("GET", "/api/v1/location-areas?parentId=area-3");
  });
});
