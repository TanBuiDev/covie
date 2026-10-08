import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AdManagement } from "@/features/ads/management/ad-management";
import { CovieApiError } from "@/lib/api/api-error";
const api = vi.hoisted(() => ({ listAdCampaigns: vi.fn(), createAdCampaign: vi.fn(), updateAdCampaign: vi.fn(), deleteAdCampaign: vi.fn(), performAdCampaignAction: vi.fn(), getAdCampaignStats: vi.fn() }));
const upload = vi.hoisted(() => vi.fn());
vi.mock("@/features/ads/ad-api", () => api);
vi.mock("@/features/media/direct-ad-upload", () => ({ uploadAdMedia: upload }));
const campaign = { id: "ad-1", title: "Ưu đãi gần bạn", body: "Khám phá", ctaLabel: "Xem ngay", targetUrl: "/items/one", creativeType: "CSS", template: "HERO", placement: "IN_FEED", device: "ALL", startsAt: "2026-10-01T00:00:00Z", endsAt: "2026-11-01T00:00:00Z", enabled: true, priority: 1, version: 4, status: "PENDING", allowedActions: ["APPROVE", "REJECT", "VIEW_STATS"] };
describe("ad management", () => {
  beforeEach(() => { vi.clearAllMocks(); vi.spyOn(HTMLMediaElement.prototype, "pause").mockImplementation(() => undefined); api.listAdCampaigns.mockResolvedValue([campaign]); });
  it("renders only server supplied campaign actions", async () => {
    render(<AdManagement scope={{ kind: "admin" }} />);
    await screen.findByText(campaign.title);
    expect(screen.getByRole("button", { name: "Duyệt" })).toBeVisible();
    expect(screen.queryByRole("button", { name: "Chỉnh sửa" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Gửi duyệt" })).toBeNull();
  });
  it("serializes approval and retries reads without replaying a successful write", async () => {
    let resolve: (() => void) | undefined;
    api.performAdCampaignAction.mockImplementation(() => new Promise<void>(done => { resolve = done; }));
    render(<AdManagement scope={{ kind: "admin" }} />);
    const approve = await screen.findByRole("button", { name: "Duyệt" });
    act(() => { fireEvent.click(approve); fireEvent.click(approve); });
    expect(api.performAdCampaignAction).toHaveBeenCalledTimes(1);
    expect(api.performAdCampaignAction).toHaveBeenCalledWith({ kind: "admin" }, "ad-1", 4, "APPROVE", undefined);
    api.listAdCampaigns.mockRejectedValueOnce(new Error("private diagnostics"));
    await act(async () => { resolve?.(); });
    expect(await screen.findByRole("alert")).not.toHaveTextContent("private diagnostics");
    fireEvent.click(screen.getByRole("button", { name: "Tải lại danh sách" }));
    await waitFor(() => expect(api.listAdCampaigns).toHaveBeenCalledTimes(3));
    expect(api.performAdCampaignAction).toHaveBeenCalledTimes(1);
  });
  it("requires a rejection reason and sends current version", async () => {
    api.performAdCampaignAction.mockResolvedValue(campaign);
    render(<AdManagement scope={{ kind: "admin" }} />);
    fireEvent.click(await screen.findByRole("button", { name: "Từ chối" }));
    fireEvent.change(screen.getByLabelText("Lý do từ chối"), { target: { value: "Nội dung cần sửa" } });
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận từ chối" }));
    await waitFor(() => expect(api.performAdCampaignAction).toHaveBeenCalledWith({ kind: "admin" }, "ad-1", 4, "REJECT", "Nội dung cần sửa"));
  });
  it("renders a safe empty state", async () => {
    api.listAdCampaigns.mockResolvedValue([]);
    render(<AdManagement scope={{ kind: "seller", merchantId: "merchant-1" }} />);
    expect(await screen.findByText("Chưa có chiến dịch quảng cáo.")).toBeVisible();
  });
  it("replaces stale management on unauthorized reads with exact login return path", async () => {
    api.listAdCampaigns.mockRejectedValue(new CovieApiError(401, "UNAUTHORIZED", "private"));
    render(<AdManagement scope={{ kind: "seller", merchantId: "merchant-1" }} />);
    expect(await screen.findByRole("link", { name: "Đăng nhập lại" })).toHaveAttribute("href", "/login?returnTo=%2Fseller%2Fmerchant-1%2Fads");
    expect(screen.queryByRole("button", { name: "Tạo chiến dịch" })).toBeNull();
  });
  it("preserves form input after an unconfirmed create and requires a read before retry", async () => {
    api.listAdCampaigns.mockResolvedValue([]);
    api.createAdCampaign.mockRejectedValue(new Error("private message"));
    render(<AdManagement scope={{ kind: "admin" }} />);
    await screen.findByText("Chưa có chiến dịch quảng cáo.");
    fireEvent.click(screen.getByRole("button", { name: "Tạo chiến dịch" }));
    fireEvent.change(screen.getByLabelText("Tiêu đề"), { target: { value: "Tiêu điểm khu phố" } });
    fireEvent.change(screen.getByLabelText("Link đích"), { target: { value: "/items/one" } });
    const form = screen.getByRole("form", { name: "Nội dung chiến dịch" });
    act(() => { fireEvent.submit(form); fireEvent.submit(form); });
    await waitFor(() => expect(api.createAdCampaign).toHaveBeenCalledTimes(1));
    expect(await screen.findByRole("alert")).not.toHaveTextContent("private message");
    expect(screen.getByLabelText("Tiêu đề")).toHaveValue("Tiêu điểm khu phố");
    expect(screen.getByRole("button", { name: "Lưu chiến dịch" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Tải lại danh sách" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Lưu chiến dịch" })).toBeEnabled());
    expect(api.createAdCampaign).toHaveBeenCalledTimes(1);
  });
  it("displays API totals and UTC daily table without fabricating activity", async () => {
    api.getAdCampaignStats.mockResolvedValue({ impressions: 200, clicks: 10, ctr: 5, days: [{ date: "2026-10-08", impressions: 200, clicks: 10, ctr: 5 }] });
    render(<AdManagement scope={{ kind: "admin" }} />);
    fireEvent.click(await screen.findByRole("button", { name: "Thống kê" }));
    expect(await screen.findByRole("table")).toHaveTextContent("2026-10-08");
    expect(screen.getAllByText("5.00%")).toHaveLength(2);
    expect(api.getAdCampaignStats).toHaveBeenCalledWith({ kind: "admin" }, "ad-1", expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/), expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/));
  });
  it("ignores a late list response from a previous merchant scope", async () => {
    let resolveOld: ((rows: unknown[]) => void) | undefined;
    api.listAdCampaigns.mockImplementationOnce(() => new Promise(done => { resolveOld = done; })).mockResolvedValueOnce([{ ...campaign, title: "Chiến dịch cửa hàng mới" }]);
    const view = render(<AdManagement scope={{ kind: "seller", merchantId: "old" }} />);
    await waitFor(() => expect(api.listAdCampaigns).toHaveBeenCalledTimes(1));
    view.rerender(<AdManagement scope={{ kind: "seller", merchantId: "new" }} />);
    await screen.findByText("Chiến dịch cửa hàng mới");
    await act(async () => resolveOld?.([campaign]));
    expect(screen.queryByText(campaign.title)).toBeNull();
  });
  it("starts a new statistics query while an older date query remains pending", async () => {
    let resolveOld: ((value: unknown) => void) | undefined;
    api.getAdCampaignStats.mockImplementationOnce(() => new Promise(done => { resolveOld = done; })).mockResolvedValueOnce({ impressions: 3, clicks: 1, ctr: 33.33, days: [] });
    render(<AdManagement scope={{ kind: "admin" }} />);
    fireEvent.click(await screen.findByRole("button", { name: "Thống kê" }));
    await waitFor(() => expect(api.getAdCampaignStats).toHaveBeenCalledTimes(1));
    fireEvent.change(screen.getByLabelText("Từ ngày"), { target: { value: "2026-10-07" } });
    fireEvent.change(screen.getByLabelText("Đến ngày"), { target: { value: "2026-10-08" } });
    await waitFor(() => expect(api.getAdCampaignStats).toHaveBeenCalledWith({ kind: "admin" }, "ad-1", "2026-10-07", "2026-10-08"));
    await screen.findByText("33.33%");
    await act(async () => resolveOld?.({ impressions: 999, clicks: 999, ctr: 100, days: [] }));
    expect(screen.queryByText("999")).toBeNull();
  });
  it("rejects empty statistics dates before calling the API", async () => {
    api.getAdCampaignStats.mockResolvedValue({ impressions: 0, clicks: 0, ctr: 0, days: [] });
    render(<AdManagement scope={{ kind: "admin" }} />);
    fireEvent.click(await screen.findByRole("button", { name: "Thống kê" }));
    await screen.findByText("Chưa có lượt xem trong khoảng ngày này.");
    fireEvent.change(screen.getByLabelText("Từ ngày"), { target: { value: "" } });
    expect(await screen.findByRole("alert")).toHaveTextContent("khoảng ngày hợp lệ");
    expect(api.getAdCampaignStats).toHaveBeenCalledTimes(1);
  });
  it("rejects image uploads for a video creative without calling upload", async () => {
    render(<AdManagement scope={{ kind: "admin" }} />);
    await screen.findByText(campaign.title);
    fireEvent.click(screen.getByRole("button", { name: "Tạo chiến dịch" }));
    fireEvent.change(screen.getByLabelText("Loại nội dung"), { target: { value: "VIDEO" } });
    fireEvent.change(screen.getByLabelText("Ảnh / video quảng cáo"), { target: { files: [new File(["png"], "photo.png", { type: "image/png" })] } });
    expect(await screen.findByRole("alert")).toHaveTextContent("đúng định dạng");
    expect(upload).not.toHaveBeenCalled();
  });
  it("upload unauthorized replaces the form with a login return path", async () => {
    upload.mockRejectedValueOnce(new CovieApiError(401, "UNAUTHORIZED", "private"));
    render(<AdManagement scope={{ kind: "admin" }} />);
    await screen.findByText(campaign.title);
    fireEvent.click(screen.getByRole("button", { name: "Tạo chiến dịch" }));
    fireEvent.change(screen.getByLabelText("Loại nội dung"), { target: { value: "VIDEO" } });
    fireEvent.change(screen.getByLabelText("Ảnh / video quảng cáo"), { target: { files: [new File(["mp4"], "movie.mp4", { type: "video/mp4" })] } });
    expect(await screen.findByRole("link", { name: "Đăng nhập lại" })).toHaveAttribute("href", "/login?returnTo=%2Fadmin%2Fads");
  });
  it("a late retry response cannot replace the latest management list", async () => {
    api.listAdCampaigns.mockRejectedValueOnce(new Error("unavailable"));
    let resolveOld: ((rows: unknown[]) => void) | undefined;
    api.listAdCampaigns.mockImplementationOnce(() => new Promise(done => { resolveOld = done; })).mockResolvedValueOnce([{ ...campaign, title: "Dữ liệu mới nhất" }]);
    render(<AdManagement scope={{ kind: "admin" }} />);
    const retry = await screen.findByRole("button", { name: "Tải lại danh sách" });
    act(() => { fireEvent.click(retry); fireEvent.click(retry); });
    await screen.findByText("Dữ liệu mới nhất");
    await act(async () => resolveOld?.([campaign]));
    expect(screen.queryByText(campaign.title)).toBeNull();
  });
  it("saves the verified uploaded media with image type and no CSS template", async () => {
    upload.mockResolvedValueOnce({ mediaId: "media-1" });
    Object.defineProperty(URL, "createObjectURL", { configurable: true, value: vi.fn(() => "blob:preview") });
    Object.defineProperty(URL, "revokeObjectURL", { configurable: true, value: vi.fn() });
    api.createAdCampaign.mockResolvedValue(campaign);
    render(<AdManagement scope={{ kind: "admin" }} />);
    await screen.findByText(campaign.title);
    fireEvent.click(screen.getByRole("button", { name: "Tạo chiến dịch" }));
    fireEvent.change(screen.getByLabelText("Tiêu đề"), { target: { value: "Ảnh cửa hàng" } });
    fireEvent.change(screen.getByLabelText("Link đích"), { target: { value: "/merchants/one" } });
    fireEvent.change(screen.getByLabelText("Loại nội dung"), { target: { value: "IMAGE" } });
    fireEvent.change(screen.getByLabelText("Ảnh / video quảng cáo"), { target: { files: [new File(["png"], "photo.png", { type: "image/png" })] } });
    await screen.findByText("Tệp đã được xác minh.");
    fireEvent.submit(screen.getByRole("form", { name: "Nội dung chiến dịch" }));
    await waitFor(() => expect(api.createAdCampaign).toHaveBeenCalledWith({ kind: "admin" }, expect.objectContaining({ title: "Ảnh cửa hàng", mediaId: "media-1", creativeType: "IMAGE", template: undefined })));
    vi.restoreAllMocks();
  });
  it("fails closed when a campaign command identity is incomplete", async () => {
    api.listAdCampaigns.mockResolvedValue([{ ...campaign, id: undefined, version: undefined }]);
    render(<AdManagement scope={{ kind: "admin" }} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("dữ liệu chiến dịch");
    expect(screen.queryByRole("button", { name: "Duyệt" })).toBeNull();
  });
  it("appends the next campaign page and retains existing rows after a retry", async () => {
    const first = Array.from({ length: 100 }, (_, index) => ({ ...campaign, id: `ad-${index}`, title: `Chiến dịch ${index}` }));
    api.listAdCampaigns.mockResolvedValueOnce(first).mockRejectedValueOnce(new Error("private")).mockResolvedValueOnce([first[99], { ...campaign, id: "old-ad", title: "Chiến dịch cũ" }]);
    render(<AdManagement scope={{ kind: "admin" }} />);
    fireEvent.click(await screen.findByRole("button", { name: "Xem thêm chiến dịch" }));
    expect(await screen.findByRole("alert")).not.toHaveTextContent("private");
    expect(screen.getByText("Chiến dịch 0")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Thử tải thêm" }));
    await screen.findByText("Chiến dịch cũ");
    expect(screen.getAllByText("Chiến dịch 99")).toHaveLength(1);
    expect(api.listAdCampaigns).toHaveBeenLastCalledWith({ kind: "admin" }, 100);
  });
});
