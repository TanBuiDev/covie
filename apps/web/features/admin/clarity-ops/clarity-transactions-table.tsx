"use client";

import { useMemo, useState } from "react";
import {
  MagnifyingGlass,
  DownloadSimple,
  CaretUp,
  CaretDown,
  Eye,
  CheckSquare,
  Square,
  Funnel,
} from "@phosphor-icons/react";
import type { TableFilterOptions, TransactionRecord, TransactionStatus } from "./clarity-types";
import { exportTransactionsToCsv } from "./clarity-data";
import styles from "./clarity-ops.module.css";

interface ClarityTransactionsTableProps {
  records: TransactionRecord[];
  onSelectRecord: (record: TransactionRecord) => void;
  initialStatusFilter?: "ALL" | TransactionStatus;
}

export function ClarityTransactionsTable({
  records,
  onSelectRecord,
  initialStatusFilter = "ALL",
}: ClarityTransactionsTableProps) {
  const [filter, setFilter] = useState<TableFilterOptions>({
    searchQuery: "",
    statusFilter: initialStatusFilter,
    channelFilter: "ALL",
    sortColumn: "formattedDate",
    sortDirection: "desc",
    page: 1,
    pageSize: 5,
  });

  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  // Filtered & Sorted Records
  const filteredRecords = useMemo(() => {
    return records
      .filter((r) => {
        // Search query
        if (filter.searchQuery.trim()) {
          const q = filter.searchQuery.toLowerCase();
          const matches =
            r.orderCode.toLowerCase().includes(q) ||
            r.customerName.toLowerCase().includes(q) ||
            r.customerEmail.toLowerCase().includes(q);
          if (!matches) return false;
        }
        // Status filter
        if (filter.statusFilter !== "ALL" && r.status !== filter.statusFilter) {
          return false;
        }
        // Channel filter
        if (filter.channelFilter !== "ALL" && r.channel !== filter.channelFilter) {
          return false;
        }
        return true;
      })
      .sort((a, b) => {
        let valA: string | number = a[filter.sortColumn];
        let valB: string | number = b[filter.sortColumn];

        if (filter.sortColumn === "amount") {
          valA = a.amount;
          valB = b.amount;
        }

        if (valA < valB) return filter.sortDirection === "asc" ? -1 : 1;
        if (valA > valB) return filter.sortDirection === "asc" ? 1 : -1;
        return 0;
      });
  }, [records, filter]);

  // Pagination calculation
  const totalPages = Math.ceil(filteredRecords.length / filter.pageSize) || 1;
  const currentPage = Math.min(filter.page, totalPages);
  const paginatedRecords = useMemo(() => {
    const start = (currentPage - 1) * filter.pageSize;
    return filteredRecords.slice(start, start + filter.pageSize);
  }, [filteredRecords, currentPage, filter.pageSize]);

  // Bulk selection helpers
  const isAllPageSelected =
    paginatedRecords.length > 0 &&
    paginatedRecords.every((r) => selectedIds.includes(r.id));

  const toggleSelectAllPage = () => {
    if (isAllPageSelected) {
      setSelectedIds((prev) => prev.filter((id) => !paginatedRecords.some((r) => r.id === id)));
    } else {
      const pageIds = paginatedRecords.map((r) => r.id);
      setSelectedIds((prev) => Array.from(new Set([...prev, ...pageIds])));
    }
  };

  const toggleSelectOne = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
    );
  };

  const handleSort = (column: TableFilterOptions["sortColumn"]) => {
    setFilter((prev) => ({
      ...prev,
      sortColumn: column,
      sortDirection:
        prev.sortColumn === column && prev.sortDirection === "asc" ? "desc" : "asc",
    }));
  };

  const handleExportAllFiltered = () => {
    exportTransactionsToCsv(filteredRecords);
  };

  const handleExportSelected = () => {
    const selectedRecords = records.filter((r) => selectedIds.includes(r.id));
    if (selectedRecords.length > 0) {
      exportTransactionsToCsv(selectedRecords);
    }
  };

  const renderStatusBadge = (status: TransactionStatus) => {
    switch (status) {
      case "COMPLETED":
        return <span className={`${styles.statusBadge} ${styles.statusCompleted}`}>● Hoàn tất</span>;
      case "PENDING":
        return <span className={`${styles.statusBadge} ${styles.statusPending}`}>● Chờ xử lý</span>;
      case "REFUNDED":
        return <span className={`${styles.statusBadge} ${styles.statusRefunded}`}>● Hoàn tiền</span>;
      case "ISSUE":
        return <span className={`${styles.statusBadge} ${styles.statusIssue}`}>● Có vấn đề</span>;
      default:
        return <span className={styles.statusBadge}>● {status}</span>;
    }
  };

  return (
    <div className={styles.cardContainer}>
      <div className={styles.tableToolbar}>
        <div className={styles.cardTitleGroup}>
          <h2>Giao dịch & Đơn hàng gần đây</h2>
          <p>Kiểm tra chi tiết đơn hàng, dòng tiền và phân luồng trạng thái</p>
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: "10px", flexWrap: "wrap" }}>
          {selectedIds.length > 0 && (
            <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              <span style={{ fontSize: "12px", fontWeight: 600, color: "var(--clarity-action-primary)" }}>
                Đã chọn {selectedIds.length} mục
              </span>
              <button
                type="button"
                className={styles.btnPrimary}
                style={{ minHeight: "32px", padding: "4px 10px", fontSize: "12px" }}
                onClick={handleExportSelected}
              >
                <DownloadSimple size={14} weight="bold" />
                <span>Xuất đã chọn</span>
              </button>
              <button
                type="button"
                className={styles.btnOutline}
                style={{ minHeight: "32px", padding: "4px 8px", fontSize: "12px" }}
                onClick={() => setSelectedIds([])}
              >
                Bỏ chọn
              </button>
            </div>
          )}

          <button
            type="button"
            className={styles.btnOutline}
            style={{ minHeight: "34px", padding: "6px 12px", fontSize: "12px" }}
            onClick={handleExportAllFiltered}
            title="Xuất danh sách đơn hàng đã lọc ra tệp CSV"
          >
            <DownloadSimple size={16} />
            <span>Xuất báo cáo ({filteredRecords.length})</span>
          </button>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className={styles.tableToolbar} style={{ borderBottom: "1px solid var(--clarity-border)", paddingBottom: "14px" }}>
        <div className={styles.searchBox} style={{ maxWidth: "280px" }}>
          <MagnifyingGlass size={16} style={{ color: "var(--clarity-text-muted)" }} />
          <input
            type="text"
            className={styles.searchInput}
            placeholder="Tìm theo mã đơn, khách hàng..."
            value={filter.searchQuery}
            onChange={(e) => setFilter((prev) => ({ ...prev, searchQuery: e.target.value, page: 1 }))}
            aria-label="Tìm kiếm giao dịch"
          />
        </div>

        <div className={styles.tableFilters}>
          <div className={styles.filterChips}>
            <button
              type="button"
              className={`${styles.filterChip} ${filter.statusFilter === "ALL" ? styles.filterChipActive : ""}`}
              onClick={() => setFilter((prev) => ({ ...prev, statusFilter: "ALL", page: 1 }))}
            >
              Tất cả
            </button>
            <button
              type="button"
              className={`${styles.filterChip} ${filter.statusFilter === "COMPLETED" ? styles.filterChipActive : ""}`}
              onClick={() => setFilter((prev) => ({ ...prev, statusFilter: "COMPLETED", page: 1 }))}
            >
              Hoàn tất
            </button>
            <button
              type="button"
              className={`${styles.filterChip} ${filter.statusFilter === "PENDING" ? styles.filterChipActive : ""}`}
              onClick={() => setFilter((prev) => ({ ...prev, statusFilter: "PENDING", page: 1 }))}
            >
              Chờ xử lý
            </button>
            <button
              type="button"
              className={`${styles.filterChip} ${filter.statusFilter === "REFUNDED" ? styles.filterChipActive : ""}`}
              onClick={() => setFilter((prev) => ({ ...prev, statusFilter: "REFUNDED", page: 1 }))}
            >
              Hoàn tiền
            </button>
            <button
              type="button"
              className={`${styles.filterChip} ${filter.statusFilter === "ISSUE" ? styles.filterChipActive : ""}`}
              onClick={() => setFilter((prev) => ({ ...prev, statusFilter: "ISSUE", page: 1 }))}
            >
              Có vấn đề
            </button>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
            <Funnel size={14} style={{ color: "var(--clarity-text-muted)" }} />
            <select
              className={styles.selectDropdown}
              value={filter.channelFilter}
              onChange={(e) => setFilter((prev) => ({ ...prev, channelFilter: e.target.value, page: 1 }))}
              aria-label="Lọc theo kênh giao dịch"
            >
              <option value="ALL">Tất cả kênh</option>
              <option value="Trực tiếp">Trực tiếp</option>
              <option value="Tìm kiếm">Tìm kiếm</option>
              <option value="Mạng xã hội cư dân">Mạng xã hội cư dân</option>
              <option value="Giới thiệu">Giới thiệu</option>
            </select>
          </div>
        </div>
      </div>

      {/* Table Content */}
      <div className={styles.tableWrapper}>
        <table className={styles.dataTable} aria-label="Bảng dữ liệu giao dịch">
          <thead>
            <tr>
              <th style={{ width: "40px", textAlign: "center" }}>
                <button
                  type="button"
                  style={{ background: "transparent", border: "none", cursor: "pointer", display: "inline-flex", color: "var(--clarity-text-secondary)" }}
                  onClick={toggleSelectAllPage}
                  aria-label="Chọn tất cả bản ghi trên trang"
                >
                  {isAllPageSelected ? <CheckSquare size={16} weight="fill" color="var(--clarity-action-primary)" /> : <Square size={16} />}
                </button>
              </th>
              <th scope="col" style={{ cursor: "pointer" }} onClick={() => handleSort("orderCode")}>
                <div style={{ display: "flex", alignItems: "center", gap: "4px" }}>
                  <span>Mã đơn</span>
                  {filter.sortColumn === "orderCode" && (
                    filter.sortDirection === "asc" ? <CaretUp size={12} weight="bold" /> : <CaretDown size={12} weight="bold" />
                  )}
                </div>
              </th>
              <th scope="col">Khách hàng</th>
              <th scope="col" style={{ cursor: "pointer" }} onClick={() => handleSort("formattedDate")}>
                <div style={{ display: "flex", alignItems: "center", gap: "4px" }}>
                  <span>Thời gian</span>
                  {filter.sortColumn === "formattedDate" && (
                    filter.sortDirection === "asc" ? <CaretUp size={12} weight="bold" /> : <CaretDown size={12} weight="bold" />
                  )}
                </div>
              </th>
              <th scope="col">Kênh</th>
              <th scope="col" style={{ cursor: "pointer" }} onClick={() => handleSort("amount")}>
                <div style={{ display: "flex", alignItems: "center", gap: "4px" }}>
                  <span>Giá trị</span>
                  {filter.sortColumn === "amount" && (
                    filter.sortDirection === "asc" ? <CaretUp size={12} weight="bold" /> : <CaretDown size={12} weight="bold" />
                  )}
                </div>
              </th>
              <th scope="col">Trạng thái</th>
              <th scope="col" style={{ textAlign: "right" }}>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {paginatedRecords.length === 0 ? (
              <tr>
                <td colSpan={8} style={{ textAlign: "center", padding: "40px 16px", color: "var(--clarity-text-secondary)" }}>
                  Không tìm thấy giao dịch nào phù hợp với bộ lọc hiện tại.
                </td>
              </tr>
            ) : (
              paginatedRecords.map((record) => {
                const isSelected = selectedIds.includes(record.id);
                return (
                  <tr
                    key={record.id}
                    className={`${styles.tableRow} ${isSelected ? styles.tableRowSelected : ""}`}
                    onClick={() => onSelectRecord(record)}
                    tabIndex={0}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" || e.key === " ") {
                        e.preventDefault();
                        onSelectRecord(record);
                      }
                    }}
                  >
                    <td style={{ textAlign: "center" }} onClick={(e) => toggleSelectOne(record.id, e)}>
                      <button
                        type="button"
                        style={{ background: "transparent", border: "none", cursor: "pointer", display: "inline-flex", color: "var(--clarity-text-secondary)" }}
                        aria-label={`Chọn đơn hàng ${record.orderCode}`}
                      >
                        {isSelected ? <CheckSquare size={16} weight="fill" color="var(--clarity-action-primary)" /> : <Square size={16} />}
                      </button>
                    </td>
                    <td>
                      <strong>{record.orderCode}</strong>
                    </td>
                    <td>
                      <div>{record.customerName}</div>
                      <small style={{ color: "var(--clarity-text-muted)" }}>{record.customerEmail}</small>
                    </td>
                    <td style={{ whiteSpace: "nowrap" }}>{record.formattedDate}</td>
                    <td>{record.channel}</td>
                    <td>
                      <strong>{record.formattedAmount}</strong>
                    </td>
                    <td>{renderStatusBadge(record.status)}</td>
                    <td style={{ textAlign: "right" }}>
                      <button
                        type="button"
                        className={styles.btnOutline}
                        style={{ padding: "4px 8px", fontSize: "12px", minHeight: "28px" }}
                        onClick={(e) => {
                          e.stopPropagation();
                          onSelectRecord(record);
                        }}
                        aria-label={`Xem chi tiết đơn ${record.orderCode}`}
                      >
                        <Eye size={14} />
                        <span>Xem</span>
                      </button>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* Pagination Footer */}
      <div className={styles.tablePagination}>
        <div>
          Hiển thị <strong>{paginatedRecords.length}</strong> trên tổng số <strong>{filteredRecords.length}</strong> bản ghi
        </div>

        <div className={styles.paginationButtons}>
          <button
            type="button"
            className={styles.pageBtn}
            disabled={currentPage <= 1}
            onClick={() => setFilter((prev) => ({ ...prev, page: Math.max(1, prev.page - 1) }))}
          >
            Trang trước
          </button>

          {Array.from({ length: totalPages }, (_, i) => i + 1).map((p) => (
            <button
              key={p}
              type="button"
              className={`${styles.pageBtn} ${currentPage === p ? styles.pageBtnActive : ""}`}
              onClick={() => setFilter((prev) => ({ ...prev, page: p }))}
            >
              {p}
            </button>
          ))}

          <button
            type="button"
            className={styles.pageBtn}
            disabled={currentPage >= totalPages}
            onClick={() => setFilter((prev) => ({ ...prev, page: Math.min(totalPages, prev.page + 1) }))}
          >
            Trang sau
          </button>
        </div>
      </div>
    </div>
  );
}
