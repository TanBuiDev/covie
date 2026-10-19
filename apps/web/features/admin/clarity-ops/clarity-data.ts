import type {
  ActionableInsight,
  ChannelShare,
  DateRangeKey,
  KpiMetric,
  TimePoint,
  TransactionRecord,
} from "./clarity-types";

export function formatVnd(amount: number): string {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(amount);
}

export function formatNumber(num: number): string {
  return new Intl.NumberFormat("vi-VN").format(num);
}

export function getKpiMetrics(
  range: DateRangeKey = "30d",
  liveCounts?: { orders?: number; users?: number; merchants?: number; cases?: number }
): KpiMetric[] {
  const is7d = range === "7d";
  const is90d = range === "90d";

  const revenueVal = is7d ? 32180000 : is90d ? 384620000 : 128450000;
  const ordersVal = liveCounts?.orders && liveCounts.orders > 0
    ? liveCounts.orders
    : is7d
    ? 912
    : is90d
    ? 11420
    : 3842;
  const conversionVal = is7d ? 4.9 : is90d ? 4.4 : 4.6;
  const refundVal = is7d ? 0.9 : is90d ? 1.4 : 1.2;

  return [
    {
      id: "revenue",
      label: "Doanh thu thuần",
      value: revenueVal,
      formattedValue: formatVnd(revenueVal),
      delta: is7d ? 14.2 : is90d ? 11.5 : 12.8,
      deltaType: "percent",
      isGood: true,
      comparisonPeriod: is7d ? "so với 7 ngày trước" : is90d ? "so với 90 ngày trước" : "so với 30 ngày trước",
      definition: "Doanh thu thuần sau chiết khấu và các đơn hoàn tiền.",
      sparkline: is7d
        ? [3.8, 4.2, 4.0, 4.7, 5.1, 4.9, 5.4]
        : [2.1, 2.4, 2.2, 2.8, 3.1, 3.0, 3.4, 3.2, 3.6, 3.9, 4.1, 4.0, 4.4, 4.2, 4.8, 5.1, 5.0, 5.3],
      targetFilter: "REVENUE",
    },
    {
      id: "orders",
      label: "Đơn hàng",
      value: ordersVal,
      formattedValue: formatNumber(ordersVal),
      delta: is7d ? 9.1 : is90d ? 7.6 : 8.4,
      deltaType: "percent",
      isGood: true,
      comparisonPeriod: is7d ? "so với 7 ngày trước" : is90d ? "so với 90 ngày trước" : "so với 30 ngày trước",
      definition: "Số lượng đơn hàng hợp lệ hoàn tất thành công trong kỳ.",
      sparkline: is7d
        ? [110, 125, 120, 138, 142, 135, 142]
        : [85, 90, 88, 95, 102, 98, 108, 112, 110, 122, 128, 124, 130, 135, 140],
      targetHref: "/admin/orders",
    },
    {
      id: "conversion",
      label: "Tỷ lệ chuyển đổi",
      value: conversionVal,
      formattedValue: `${conversionVal}%`,
      delta: is7d ? 0.9 : is90d ? 0.5 : 0.7,
      deltaType: "pp",
      isGood: true,
      comparisonPeriod: is7d ? "so với 7 ngày trước" : is90d ? "so với 90 ngày trước" : "so với 30 ngày trước",
      definition: "Tỷ lệ chuyển đổi thành công từ lượt xem sang đơn hàng hợp lệ.",
      sparkline: [3.9, 4.0, 4.1, 4.0, 4.3, 4.2, 4.4, 4.3, 4.5, 4.4, 4.6],
    },
    {
      id: "refund",
      label: "Tỷ lệ hoàn tiền",
      value: refundVal,
      formattedValue: `${refundVal}%`,
      delta: -0.3,
      deltaType: "pp",
      isGood: true, // Giảm hoàn tiền là tốt
      comparisonPeriod: is7d ? "so với 7 ngày trước" : is90d ? "so với 90 ngày trước" : "so với 30 ngày trước",
      definition: "Tỷ lệ đơn hàng hoàn tiền so với tổng số đơn được tiếp nhận.",
      sparkline: [1.8, 1.7, 1.6, 1.7, 1.5, 1.4, 1.5, 1.3, 1.3, 1.2],
      targetFilter: "REFUNDED",
    },
  ];
}

export function getTimeSeriesData(range: DateRangeKey = "30d"): TimePoint[] {
  if (range === "7d") {
    return [
      { date: "2026-10-09", label: "09/10", primary: 4100000, compare: 3600000 },
      { date: "2026-10-10", label: "10/10", primary: 4450000, compare: 3900000 },
      { date: "2026-10-11", label: "11/10", primary: 4200000, compare: 3800000 },
      { date: "2026-10-12", label: "12/10", primary: 4850000, compare: 4200000 },
      { date: "2026-10-13", label: "13/10", primary: 5200000, compare: 4500000 },
      { date: "2026-10-14", label: "14/10", primary: 4950000, compare: 4400000 },
      { date: "2026-10-15", label: "15/10", primary: 5430000, compare: 4750000 },
    ];
  }

  if (range === "90d") {
    return [
      { date: "2026-07-20", label: "Tuần 30", primary: 26500000, compare: 23500000 },
      { date: "2026-07-27", label: "Tuần 31", primary: 28000000, compare: 24800000 },
      { date: "2026-08-03", label: "Tuần 32", primary: 27500000, compare: 25100000 },
      { date: "2026-08-10", label: "Tuần 33", primary: 30200000, compare: 27000000 },
      { date: "2026-08-17", label: "Tuần 34", primary: 32000000, compare: 28500000 },
      { date: "2026-08-24", label: "Tuần 35", primary: 31500000, compare: 29000000 },
      { date: "2026-08-31", label: "Tuần 36", primary: 34500000, compare: 30500000 },
      { date: "2026-09-07", label: "Tuần 37", primary: 36000000, compare: 31800000 },
      { date: "2026-09-14", label: "Tuần 38", primary: 35200000, compare: 32500000 },
      { date: "2026-09-21", label: "Tuần 39", primary: 38000000, compare: 34000000 },
      { date: "2026-09-28", label: "Tuần 40", primary: 39500000, compare: 35200000 },
      { date: "2026-10-05", label: "Tuần 41", primary: 41200000, compare: 37000000 },
    ];
  }

  // 30d default (15 mốc đại diện 2 ngày/lần)
  return [
    { date: "2026-09-16", label: "16/09", primary: 3500000, compare: 3100000 },
    { date: "2026-09-18", label: "18/09", primary: 3800000, compare: 3300000 },
    { date: "2026-09-20", label: "20/09", primary: 3600000, compare: 3400000 },
    { date: "2026-09-22", label: "22/09", primary: 4100000, compare: 3650000 },
    { date: "2026-09-24", label: "24/09", primary: 4300000, compare: 3800000 },
    { date: "2026-09-26", label: "26/09", primary: 4050000, compare: 3700000 },
    { date: "2026-09-28", label: "28/09", primary: 4500000, compare: 3900000 },
    { date: "2026-09-30", label: "30/09", primary: 4800000, compare: 4100000 },
    { date: "2026-10-02", label: "02/10", primary: 4600000, compare: 4200000 },
    { date: "2026-10-04", label: "04/10", primary: 5050000, compare: 4350000 },
    { date: "2026-10-06", label: "06/10", primary: 5200000, compare: 4500000 },
    { date: "2026-10-08", label: "08/10", primary: 4950000, compare: 4400000 },
    { date: "2026-10-10", label: "10/10", primary: 5350000, compare: 4700000 },
    { date: "2026-10-12", label: "12/10", primary: 5600000, compare: 4850000 },
    { date: "2026-10-15", label: "15/10", primary: 5850000, compare: 5100000 },
  ];
}

export function getChannelBreakdown(): ChannelShare[] {
  return [
    { id: "direct", name: "Trực tiếp tại Sàn CoVie", amount: 53950000, percentage: 42, color: "#2459e0" },
    { id: "search", name: "Tìm kiếm & Khám phá nội bộ", amount: 35966000, percentage: 28, color: "#0ea5e9" },
    { id: "social", name: "Mạng xã hội cư dân V2", amount: 23121000, percentage: 18, color: "#087f5b" },
    { id: "referral", name: "Giới thiệu & Nhóm dân cư", amount: 15413000, percentage: 12, color: "#9a6700" },
  ];
}

export function getActionableInsights(openCasesCount = 5): ActionableInsight[] {
  return [
    {
      id: "refund-down",
      title: "Tỷ lệ hoàn tiền giảm 0.3 điểm phần trăm",
      description: "Chất lượng dịch vụ giao hàng cư dân cải thiện, chỉ có 46 đơn hoàn tiền trên 3.842 đơn.",
      tag: "Hiệu quả vận hành",
      tone: "success",
      ctaText: "Lọc đơn hoàn tiền",
      filterStatus: "REFUNDED",
    },
    {
      id: "moderation-pending",
      title: `Hàng đợi kiểm duyệt có ${openCasesCount} báo cáo cần xử lý`,
      description: "Có báo cáo nội dung quảng cáo và bình luận cư dân đang chờ phản hồi từ quản trị viên.",
      tag: "An toàn tuân thủ",
      tone: "warning",
      ctaText: "Mở hàng đợi kiểm duyệt",
      ctaHref: "/admin/reports",
    },
    {
      id: "peak-orders",
      title: "Đơn hàng tăng vọt vào khung giờ 19h - 21h",
      description: "Khối lượng đặt hàng nông sản và thực phẩm tươi sống tập trung cao điểm buổi tối.",
      tag: "Thấu hiểu nhu cầu",
      tone: "info",
      ctaText: "Xem đơn hàng",
      ctaHref: "/admin/orders",
    },
  ];
}

export function getSampleTransactions(): TransactionRecord[] {
  return [
    {
      id: "ord-9041",
      orderCode: "CV-2026-9041",
      customerName: "Nguyễn Văn An",
      customerEmail: "an.nguyen@covie.vn",
      customerPhone: "0912 345 678",
      address: "Tòa S2.01, Căn 12A08, Vinhomes Grand Park, TP. Thủ Đức",
      createdAt: "2026-10-15T10:15:00Z",
      formattedDate: "15/10/2026 17:15",
      amount: 450000,
      formattedAmount: "450.000 ₫",
      channel: "Trực tiếp",
      status: "COMPLETED",
      items: [
        { name: "Rau củ hữu cơ Đà Lạt tổng hợp (Combo 3kg)", quantity: 1, unitPrice: 180000, total: 180000 },
        { name: "Trứng gà ta thảo mộc (Vỉ 10 quả)", quantity: 2, unitPrice: 65000, total: 130000 },
        { name: "Cam sành Hàm Yên loại 1 (2kg)", quantity: 1, unitPrice: 140000, total: 140000 },
      ],
      subtotal: 450000,
      shippingFee: 0,
      discount: 0,
      paymentMethod: "Ví CoVie Pay",
      events: [
        { time: "17:15 - 15/10", title: "Đã hoàn tất", description: "Người mua đã xác nhận nhận hàng thành công.", done: true },
        { time: "16:40 - 15/10", title: "Đang giao hàng", description: "Tài xế nội khu đang vận chuyển đến căn 12A08.", done: true },
        { time: "15:20 - 15/10", title: "Người bán chuẩn bị xong", description: "Gian hàng Nông Sản Xanh đã đóng gói kiện hàng.", done: true },
        { time: "10:15 - 15/10", title: "Đặt hàng thành công", description: "Đơn hàng khởi tạo với mã CV-2026-9041.", done: true },
      ],
    },
    {
      id: "ord-9042",
      orderCode: "CV-2026-9042",
      customerName: "Trần Thị Mai",
      customerEmail: "mai.tran@gmail.com",
      customerPhone: "0988 776 655",
      address: "Tòa S3.05, Căn 0804, Vinhomes Grand Park, TP. Thủ Đức",
      createdAt: "2026-10-15T11:30:00Z",
      formattedDate: "15/10/2026 18:30",
      amount: 320000,
      formattedAmount: "320.000 ₫",
      channel: "Tìm kiếm",
      status: "PENDING",
      items: [
        { name: "Gạo ST25 Ông Cua chính hãng (Túi 5kg)", quantity: 1, unitPrice: 220000, total: 220000 },
        { name: "Dầu ăn thực vật nguyên chất Simply 1L", quantity: 2, unitPrice: 50000, total: 100000 },
      ],
      subtotal: 320000,
      shippingFee: 15000,
      discount: 15000,
      paymentMethod: "Chuyển khoản VietQR",
      events: [
        { time: "18:30 - 15/10", title: "Đang chờ người bán xác nhận", description: "Hệ thống đang chuyển thông tin tới gian hàng Bách Hóa Cư Dân.", done: true },
        { time: "18:28 - 15/10", title: "Thanh toán thành công", description: "Đã ghi nhận giao dịch VietQR 320.000 ₫.", done: true },
      ],
    },
    {
      id: "ord-9043",
      orderCode: "CV-2026-9043",
      customerName: "Lê Hoàng Long",
      customerEmail: "long.le@fpt.edu.vn",
      customerPhone: "0903 112 233",
      address: "Tòa S5.02, Căn 2011, Vinhomes Grand Park, TP. Thủ Đức",
      createdAt: "2026-10-14T14:20:00Z",
      formattedDate: "14/10/2026 21:20",
      amount: 150000,
      formattedAmount: "150.000 ₫",
      channel: "Mạng xã hội cư dân",
      status: "REFUNDED",
      items: [
        { name: "Sữa chua hạt sen tươi nhà làm (Set 6 hũ)", quantity: 1, unitPrice: 150000, total: 150000 },
      ],
      subtotal: 150000,
      shippingFee: 0,
      discount: 0,
      paymentMethod: "Ví CoVie Pay",
      events: [
        { time: "22:00 - 14/10", title: "Đã hoàn tiền thành công", description: "Hoàn 150.000 ₫ về ví CoVie Pay theo yêu cầu cư dân.", done: true },
        { time: "21:35 - 14/10", title: "Chấp nhận hoàn tiền", description: "Người bán xác nhận hết suất tráng miệng buổi tối.", done: true },
      ],
    },
    {
      id: "ord-9044",
      orderCode: "CV-2026-9044",
      customerName: "Phạm Thúy Hằng",
      customerEmail: "hang.pham@outlook.com",
      customerPhone: "0934 567 890",
      address: "Tòa S1.07, Căn 0502, Vinhomes Grand Park, TP. Thủ Đức",
      createdAt: "2026-10-14T09:10:00Z",
      formattedDate: "14/10/2026 16:10",
      amount: 890000,
      formattedAmount: "890.000 ₫",
      channel: "Trực tiếp",
      status: "COMPLETED",
      items: [
        { name: "Bếp nướng điện không khói Lock&Lock", quantity: 1, unitPrice: 890000, total: 890000 },
      ],
      subtotal: 890000,
      shippingFee: 0,
      discount: 0,
      paymentMethod: "Thẻ ATM nội địa",
      events: [
        { time: "16:10 - 14/10", title: "Hoàn tất giao nhận", description: "Đã giao hàng thành công tại sảnh S1.07.", done: true },
      ],
    },
    {
      id: "ord-9045",
      orderCode: "CV-2026-9045",
      customerName: "Vũ Minh Quân",
      customerEmail: "quan.vm@gmail.com",
      customerPhone: "0971 223 344",
      address: "Tòa S6.01, Căn 1506, Vinhomes Grand Park, TP. Thủ Đức",
      createdAt: "2026-10-13T16:00:00Z",
      formattedDate: "13/10/2026 23:00",
      amount: 620000,
      formattedAmount: "620.000 ₫",
      channel: "Giới thiệu",
      status: "ISSUE",
      items: [
        { name: "Thùng hải sản đóng hộp xuất khẩu (4 hộp)", quantity: 1, unitPrice: 620000, total: 620000 },
      ],
      subtotal: 620000,
      shippingFee: 0,
      discount: 0,
      paymentMethod: "Ví CoVie Pay",
      events: [
        { time: "23:00 - 13/10", title: "Phản ánh giao trễ", description: "Người mua báo cáo đơn chưa giao sau 3 giờ hẹn.", done: false },
        { time: "18:00 - 13/10", title: "Đã rời kho", description: "Kiện hàng đang trung chuyển nội bộ.", done: true },
      ],
    },
    {
      id: "ord-9046",
      orderCode: "CV-2026-9046",
      customerName: "Đỗ Bảo Châu",
      customerEmail: "chau.do@yahoo.com",
      customerPhone: "0945 667 788",
      address: "Tòa S8.03, Căn 1809, Vinhomes Grand Park, TP. Thủ Đức",
      createdAt: "2026-10-13T08:45:00Z",
      formattedDate: "13/10/2026 15:45",
      amount: 210000,
      formattedAmount: "210.000 ₫",
      channel: "Trực tiếp",
      status: "COMPLETED",
      items: [
        { name: "Trà sữa Ô long nướng sương sáo (Ly lớn)", quantity: 3, unitPrice: 70000, total: 210000 },
      ],
      subtotal: 210000,
      shippingFee: 0,
      discount: 0,
      paymentMethod: "Ví CoVie Pay",
      events: [
        { time: "15:45 - 13/10", title: "Đã nhận hàng", description: "Người mua lấy hàng tại sảnh.", done: true },
      ],
    },
    {
      id: "ord-9047",
      orderCode: "CV-2026-9047",
      customerName: "Hoàng Gia Huy",
      customerEmail: "huy.hg@covie.vn",
      customerPhone: "0923 889 900",
      address: "Tòa S10.02, Căn 0305, Vinhomes Grand Park, TP. Thủ Đức",
      createdAt: "2026-10-12T13:10:00Z",
      formattedDate: "12/10/2026 20:10",
      amount: 540000,
      formattedAmount: "540.000 ₫",
      channel: "Mạng xã hội cư dân",
      status: "COMPLETED",
      items: [
        { name: "Bánh bông lan trứng muối chà bông phô mai", quantity: 2, unitPrice: 120000, total: 240000 },
        { name: "Set chè tuyết yến dưỡng nhan (5 chai)", quantity: 1, unitPrice: 300000, total: 300000 },
      ],
      subtotal: 540000,
      shippingFee: 0,
      discount: 0,
      paymentMethod: "Chuyển khoản VietQR",
      events: [
        { time: "20:10 - 12/10", title: "Hoàn tất", description: "Giao nhận thành công.", done: true },
      ],
    },
    {
      id: "ord-9048",
      orderCode: "CV-2026-9048",
      customerName: "Bùi Thị Ngọc",
      customerEmail: "ngoc.bui@live.com",
      customerPhone: "0908 990 011",
      address: "Tòa S2.03, Căn 1002, Vinhomes Grand Park, TP. Thủ Đức",
      createdAt: "2026-10-12T07:20:00Z",
      formattedDate: "12/10/2026 14:20",
      amount: 175000,
      formattedAmount: "175.000 ₫",
      channel: "Tìm kiếm",
      status: "COMPLETED",
      items: [
        { name: "Bánh mì hoa cúc Pháp nướng bơ tỏi", quantity: 2, unitPrice: 60000, total: 120000 },
        { name: "Cà phê muối đóng chai đậm đặc 330ml", quantity: 1, unitPrice: 55000, total: 55000 },
      ],
      subtotal: 175000,
      shippingFee: 0,
      discount: 0,
      paymentMethod: "Ví CoVie Pay",
      events: [
        { time: "14:20 - 12/10", title: "Hoàn tất", description: "Giao tận tay cư dân.", done: true },
      ],
    },
  ];
}

export function exportTransactionsToCsv(records: TransactionRecord[]): void {
  const headers = ["Mã đơn", "Khách hàng", "Email", "Thời gian", "Kênh", "Giá trị (VND)", "Trạng thái", "Phương thức"];
  const rows = records.map((r) => [
    r.orderCode,
    `"${r.customerName.replace(/"/g, '""')}"`,
    r.customerEmail,
    r.formattedDate,
    r.channel,
    r.amount,
    r.status === "COMPLETED" ? "Hoàn tất" : r.status === "PENDING" ? "Chờ xử lý" : r.status === "REFUNDED" ? "Hoàn tiền" : "Có vấn đề",
    `"${r.paymentMethod.replace(/"/g, '""')}"`,
  ]);

  const csvContent = "\uFEFF" + [headers.join(","), ...rows.map((e) => e.join(","))].join("\r\n");
  const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.setAttribute("href", url);
  link.setAttribute("download", `covie-transactions-${new Date().toISOString().slice(0, 10)}.csv`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
