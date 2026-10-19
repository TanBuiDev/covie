export type DateRangeKey = "7d" | "30d" | "90d";

export type KpiMetric = {
  id: string;
  label: string;
  value: number;
  formattedValue: string;
  delta: number;
  deltaType: "percent" | "pp";
  isGood: boolean;
  comparisonPeriod: string;
  definition: string;
  sparkline: number[];
  targetFilter?: string;
  targetHref?: string;
};

export type TimePoint = {
  date: string;
  label: string;
  primary: number;
  compare: number;
};

export type ChannelShare = {
  id: string;
  name: string;
  amount: number;
  percentage: number;
  color: string;
};

export type ActionableInsight = {
  id: string;
  title: string;
  description: string;
  tag: string;
  tone: "success" | "warning" | "info" | "danger";
  ctaText: string;
  ctaHref?: string;
  filterStatus?: TransactionStatus;
};

export type TransactionStatus = "COMPLETED" | "PENDING" | "REFUNDED" | "ISSUE";

export type TransactionItem = {
  name: string;
  quantity: number;
  unitPrice: number;
  total: number;
};

export type TransactionTimelineEvent = {
  time: string;
  title: string;
  description: string;
  done: boolean;
};

export type TransactionRecord = {
  id: string;
  orderCode: string;
  customerName: string;
  customerEmail: string;
  customerPhone?: string;
  address?: string;
  createdAt: string;
  formattedDate: string;
  amount: number;
  formattedAmount: string;
  channel: string;
  status: TransactionStatus;
  items: TransactionItem[];
  subtotal: number;
  shippingFee: number;
  discount: number;
  paymentMethod: string;
  events: TransactionTimelineEvent[];
};

export type TableFilterOptions = {
  searchQuery: string;
  statusFilter: "ALL" | TransactionStatus;
  channelFilter: string;
  sortColumn: "orderCode" | "formattedDate" | "amount" | "status";
  sortDirection: "asc" | "desc";
  page: number;
  pageSize: number;
};
