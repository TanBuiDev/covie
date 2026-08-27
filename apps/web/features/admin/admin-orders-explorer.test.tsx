import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AdminOrdersExplorer } from "./admin-orders-explorer";

const mocks = vi.hoisted(() => ({ requestBrowserApi: vi.fn() }));
vi.mock("@/lib/api/api-client", () => ({ requestBrowserApi: mocks.requestBrowserApi }));

function response(payload: unknown): Response {
  return { ok: true, json: vi.fn().mockResolvedValue(payload) } as unknown as Response;
}

function order(id: string, code: string) {
  return { id, orderCode: code, status: "CREATED", merchantId: "merchant-1", buyerUserId: "buyer-1", type: "PRODUCT" };
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("AdminOrdersExplorer loading ownership", () => {
  it("loads the initial page and paginates from user intent", async () => {
    mocks.requestBrowserApi.mockImplementation(async (_method: string, path: string) => {
      if (path.includes("?page=0")) return response({ items: [order("order-1", "CV-1")], total: 51 });
      if (path.includes("?page=1")) return response({ items: [order("order-2", "CV-2")], total: 51 });
      if (path.endsWith("/order-1")) return response({ ...order("order-1", "CV-1"), items: [] });
      if (path.endsWith("/order-2")) return response({ ...order("order-2", "CV-2"), items: [] });
      throw new Error(`Unexpected path: ${path}`);
    });

    render(<AdminOrdersExplorer />);
    expect(await screen.findByText("CV-1")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Sau" }));
    expect(await screen.findByText("CV-2")).toBeInTheDocument();
    expect(mocks.requestBrowserApi).toHaveBeenCalledWith("GET", expect.stringContaining("page=1"));
  });

  it("applies the submitted order-code filter", async () => {
    mocks.requestBrowserApi.mockImplementation(async (_method: string, path: string) => {
      if (path.includes("orderCode=CV-2048")) return response({ items: [order("order-2", "CV-2048")], total: 1 });
      if (path.includes("/orders?")) return response({ items: [order("order-1", "CV-1")], total: 1 });
      return response({ ...order(path.endsWith("order-2") ? "order-2" : "order-1", path.endsWith("order-2") ? "CV-2048" : "CV-1"), items: [] });
    });

    render(<AdminOrdersExplorer />);
    await screen.findByText("CV-1");
    fireEvent.change(screen.getByRole("textbox", { name: "Mã đơn" }), { target: { value: "CV-2048" } });
    fireEvent.click(screen.getByRole("button", { name: "Tìm kiếm" }));

    expect(await screen.findByText("CV-2048")).toBeInTheDocument();
    expect(mocks.requestBrowserApi).toHaveBeenCalledWith("GET", expect.stringContaining("orderCode=CV-2048"));
  });

  it("does not let an older list response replace a newer query", async () => {
    let resolveOld: ((value: Response) => void) | undefined;
    const oldResponse = new Promise<Response>((resolve) => { resolveOld = resolve; });
    let listCalls = 0;
    mocks.requestBrowserApi.mockImplementation(async (_method: string, path: string) => {
      if (path.includes("/orders?")) {
        listCalls += 1;
        if (listCalls === 1) return oldResponse;
        return response({ items: [order("order-new", "CV-NEW")], total: 1 });
      }
      return response({ ...order("order-new", "CV-NEW"), items: [] });
    });

    render(<AdminOrdersExplorer />);
    await waitFor(() => expect(listCalls).toBe(1));
    fireEvent.change(screen.getByRole("textbox", { name: "Mã đơn" }), { target: { value: "CV-NEW" } });
    fireEvent.click(screen.getByRole("button", { name: "Tìm kiếm" }));

    expect(await screen.findByText("CV-NEW")).toBeInTheDocument();
    await act(async () => { resolveOld?.(response({ items: [order("order-old", "CV-OLD")], total: 1 })); });
    expect(screen.queryByText("CV-OLD")).not.toBeInTheDocument();
    expect(screen.getByText("CV-NEW")).toBeInTheDocument();
  });
});
