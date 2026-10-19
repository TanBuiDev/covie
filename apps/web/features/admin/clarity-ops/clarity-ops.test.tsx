import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import {
  ClarityBreakdownPanel,
  ClarityDetailDrawer,
  ClarityKpiCard,
  ClarityTopbar,
  ClarityTransactionsTable,
  ClarityTrendChart,
  getActionableInsights,
  getChannelBreakdown,
  getKpiMetrics,
  getSampleTransactions,
  getTimeSeriesData,
} from "./index";
import { AdminDashboard } from "../admin-dashboard";

describe("Clarity Ops — Unified Analytics Dashboard", () => {
  it("renders KPI card with tabular value, delta, sparkline and responds to clicks", () => {
    const kpis = getKpiMetrics("30d");
    const revenueKpi = kpis.find((k) => k.id === "revenue")!;
    const onSelect = vi.fn();

    render(<ClarityKpiCard metric={revenueKpi} onSelect={onSelect} />);

    expect(screen.getByText("Doanh thu thuần")).toBeInTheDocument();
    expect(screen.getByText("+12.8%")).toBeInTheDocument();
    expect(screen.getByText("so với 30 ngày trước")).toBeInTheDocument();

    const card = screen.getByRole("button", { name: /Doanh thu thuần/ });
    fireEvent.click(card);
    expect(onSelect).toHaveBeenCalledWith(revenueKpi);

    fireEvent.keyDown(card, { key: "Enter" });
    expect(onSelect).toHaveBeenCalledTimes(2);
  });

  it("handles refund rate delta with positive semantic styling when refund decreases", () => {
    const kpis = getKpiMetrics("30d");
    const refundKpi = kpis.find((k) => k.id === "refund")!;
    expect(refundKpi.delta).toBe(-0.3);
    expect(refundKpi.deltaType).toBe("pp");
    expect(refundKpi.isGood).toBe(true); // Decreased refund is good

    render(<ClarityKpiCard metric={refundKpi} />);
    expect(screen.getByText("-0.3 pp")).toBeInTheDocument();
  });

  it("renders Trend Chart with SVG, switches ranges, toggles comparison and accessible table", () => {
    const data = getTimeSeriesData("30d");
    const onRangeChange = vi.fn();
    const onToggleComparison = vi.fn();

    render(
      <ClarityTrendChart
        data={data}
        range="30d"
        onRangeChange={onRangeChange}
        showComparison={true}
        onToggleComparison={onToggleComparison}
      />
    );

    expect(screen.getByText("Xu hướng doanh thu thuần")).toBeInTheDocument();

    // Range buttons
    fireEvent.click(screen.getByRole("button", { name: "7 ngày" }));
    expect(onRangeChange).toHaveBeenCalledWith("7d");

    // Toggle comparison
    const checkbox = screen.getByLabelText("So sánh kỳ trước");
    fireEvent.click(checkbox);
    expect(onToggleComparison).toHaveBeenCalledWith(false);

    // Switch to Accessible WCAG Table view
    const tableBtn = screen.getByRole("button", { name: "Xem dạng bảng" });
    fireEvent.click(tableBtn);
    expect(screen.getByRole("table", { name: /Bảng số liệu xu hướng doanh thu/ })).toBeInTheDocument();

    // Switch back to Chart view
    const chartBtn = screen.getByRole("button", { name: "Xem dạng biểu đồ" });
    fireEvent.click(chartBtn);
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("renders Channel Breakdown and Actionable Insights with functional filter triggers", () => {
    const channels = getChannelBreakdown();
    const insights = getActionableInsights();
    const onTriggerFilter = vi.fn();

    render(
      <ClarityBreakdownPanel
        channels={channels}
        insights={insights}
        onTriggerFilter={onTriggerFilter}
      />
    );

    expect(screen.getByText("Trực tiếp tại Sàn CoVie")).toBeInTheDocument();
    expect(screen.getByText("(42%)")).toBeInTheDocument();

    const refundInsightBtn = screen.getByRole("button", { name: /Lọc đơn hoàn tiền/ });
    fireEvent.click(refundInsightBtn);
    expect(onTriggerFilter).toHaveBeenCalledWith("REFUNDED");
  });

  it("filters, searches and selects transactions in the Transactions Table", () => {
    const records = getSampleTransactions();
    const onSelectRecord = vi.fn();

    render(
      <ClarityTransactionsTable
        records={records}
        onSelectRecord={onSelectRecord}
      />
    );

    // Initial render shows records
    expect(screen.getByText("CV-2026-9041")).toBeInTheDocument();
    expect(screen.getByText("Nguyễn Văn An")).toBeInTheDocument();

    // Filter by status "Hoàn tiền"
    const refundChip = screen.getByRole("button", { name: "Hoàn tiền" });
    fireEvent.click(refundChip);

    // CV-2026-9043 is REFUNDED, CV-2026-9041 is COMPLETED
    expect(screen.getByText("CV-2026-9043")).toBeInTheDocument();
    expect(screen.queryByText("CV-2026-9041")).not.toBeInTheDocument();

    // Search query filter
    const searchInput = screen.getByLabelText("Tìm kiếm giao dịch");
    fireEvent.change(searchInput, { target: { value: "Nguyễn Văn An" } });

    // Since filter is still "Hoàn tiền", searching for Nguyễn Văn An (COMPLETED) gives empty
    expect(screen.getByText(/Không tìm thấy giao dịch nào phù hợp/)).toBeInTheDocument();

    // Switch back to "Tất cả"
    fireEvent.click(screen.getByRole("button", { name: "Tất cả" }));
    expect(screen.getByText("CV-2026-9041")).toBeInTheDocument();

    // Click on row to trigger onSelectRecord
    fireEvent.click(screen.getByText("CV-2026-9041"));
    expect(onSelectRecord).toHaveBeenCalled();
  });

  it("opens Detail Drawer, displays full info, copies order code, and closes on Escape", () => {
    const records = getSampleTransactions();
    const testRecord = records[0]!;
    const onClose = vi.fn();

    const writeTextMock = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, {
      clipboard: { writeText: writeTextMock },
    });

    render(<ClarityDetailDrawer record={testRecord} onClose={onClose} />);

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText(testRecord.orderCode)).toBeInTheDocument();
    expect(screen.getByText(testRecord.customerName)).toBeInTheDocument();
    expect(screen.getByText("Lịch sử tiến trình giao dịch")).toBeInTheDocument();

    // Copy order code
    const copyBtn = screen.getByRole("button", { name: /Sao chép mã đơn/ });
    fireEvent.click(copyBtn);
    expect(writeTextMock).toHaveBeenCalledWith(testRecord.orderCode);

    // Press Escape key
    fireEvent.keyDown(window, { key: "Escape" });
    expect(onClose).toHaveBeenCalled();
  });

  it("renders Clarity Topbar with search, dark mode toggle and notifications popover", () => {
    const onSearch = vi.fn();
    render(<ClarityTopbar onSearch={onSearch} />);

    expect(screen.getByLabelText("Thanh điều khiển đầu trang Admin")).toBeInTheDocument();

    // Search input
    const input = screen.getByLabelText("Tìm kiếm nhanh toàn hệ thống");
    fireEvent.change(input, { target: { value: "order-99" } });
    expect(onSearch).toHaveBeenCalledWith("order-99");

    // Dark mode toggle
    const themeBtn = screen.getByRole("button", { name: /Chuyển sang giao diện/ });
    fireEvent.click(themeBtn);
    expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
    fireEvent.click(themeBtn);
    expect(document.documentElement.getAttribute("data-theme")).toBeNull();

    // Notification bell
    const bellBtn = screen.getByRole("button", { name: /Thông báo hệ thống/ });
    fireEvent.click(bellBtn);
    expect(screen.getByText(/báo cáo vi phạm nội dung đang chờ kiểm duyệt/)).toBeInTheDocument();
  });

  it("renders the integrated AdminDashboard with all 3 layers and operational shortcuts", async () => {
    render(<AdminDashboard />);

    // Header
    expect(screen.getByText("Tổng quan vận hành sàn")).toBeInTheDocument();

    // 4 KPI cards
    expect(screen.getByText("Doanh thu thuần")).toBeInTheDocument();
    expect(screen.getByText("Đơn hàng")).toBeInTheDocument();
    expect(screen.getByText("Tỷ lệ chuyển đổi")).toBeInTheDocument();
    expect(screen.getByText("Tỷ lệ hoàn tiền")).toBeInTheDocument();

    // Operational shortcuts
    expect(screen.getAllByText(/Hàng đợi kiểm duyệt/).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText(/Người bán & Gian hàng/)).toBeInTheDocument();
    expect(screen.getByText("Nhật ký kiểm toán")).toBeInTheDocument();

    // Governance & Compliance cards
    expect(screen.getByText("Trạng thái vận hành an toàn")).toBeInTheDocument();
    expect(screen.getByText("Nguyên tắc thao tác & Quyền riêng tư")).toBeInTheDocument();
    expect(screen.getByText("Đơn hàng Read-only")).toBeInTheDocument();
  });
});
