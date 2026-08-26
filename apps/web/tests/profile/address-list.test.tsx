import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AddressList } from "@/features/profile/address-list";

const { deleteSavedAddress, navigation } = vi.hoisted(() => ({
  deleteSavedAddress: vi.fn(),
  navigation: { back: vi.fn(), refresh: vi.fn() },
}));

vi.mock("@/features/profile/address-api", () => ({ deleteSavedAddress }));
vi.mock("next/navigation", () => ({ useRouter: () => navigation }));

const addresses = [
  {
    id: "address-1",
    label: "Nhà",
    recipientName: "Nguyễn Văn Minh",
    phone: "0909 123 456",
    addressText: "S5.03, Vinhomes Grand Park, Long Bình, TP Thủ Đức",
    defaultAddress: true,
  },
  {
    id: "address-2",
    label: "Công ty",
    recipientName: "Nguyễn Văn Minh",
    addressText: "Đường Võ Chí Công, TP Thủ Đức",
    note: "Giao tại lễ tân",
    defaultAddress: false,
  },
];

describe("BUY-003 Address List", () => {
  beforeEach(() => {
    deleteSavedAddress.mockReset();
    navigation.back.mockReset();
    navigation.refresh.mockReset();
  });

  it("renders contract-backed address cards and dedicated create/edit links", () => {
    render(<AddressList addresses={addresses} />);

    expect(screen.getByRole("heading", { name: "Địa chỉ của tôi" })).toBeInTheDocument();
    expect(screen.getByText("MẶC ĐỊNH")).toBeInTheDocument();
    expect(screen.getByText("Nhà · Nguyễn Văn Minh")).toBeInTheDocument();
    expect(screen.getByText("0909 123 456")).toBeInTheDocument();
    expect(screen.getByText("Giao tại lễ tân · Không phải địa chỉ mặc định")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Chỉnh sửa Nhà" })).toHaveAttribute("href", "/me/addresses/address-1");
    expect(screen.queryByRole("link", { name: "Chỉnh sửa Công ty" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Thêm địa chỉ mới" })).toHaveAttribute("href", "/me/addresses/new");
  });

  it("requires inline confirmation before deleting and refreshes server data", async () => {
    deleteSavedAddress.mockResolvedValueOnce(undefined);
    render(<AddressList addresses={addresses} />);

    fireEvent.click(screen.getByRole("button", { name: "Xóa Nhà" }));
    expect(screen.getByRole("heading", { name: "Xóa địa chỉ này?" })).toBeInTheDocument();
    expect(screen.getByText("Địa chỉ sẽ bị xóa khỏi tài khoản. Hành động cần xác nhận trước khi thực hiện.")).toBeInTheDocument();
    expect(screen.queryByText("Công ty · Nguyễn Văn Minh")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Xác nhận xóa" }));
    await waitFor(() => expect(deleteSavedAddress).toHaveBeenCalledWith("address-1"));
    expect(navigation.refresh).toHaveBeenCalledTimes(1);
  });

  it("renders the empty state without hiding the create action", () => {
    render(<AddressList addresses={[]} />);

    expect(screen.getByRole("heading", { name: "Chưa có địa chỉ đã lưu" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Thêm địa chỉ mới" })).toHaveAttribute("href", "/me/addresses/new");
  });

  it("keeps the frozen app bar visible while addresses are loading", () => {
    render(<AddressList addresses={[]} state="loading" />);

    expect(screen.getByRole("heading", { name: "Địa chỉ của tôi" })).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Đang tải địa chỉ");
    expect(screen.queryByRole("link", { name: "Thêm địa chỉ mới" })).not.toBeInTheDocument();
  });

  it("offers a retry action when the address list cannot be loaded", () => {
    const retry = vi.fn();
    render(<AddressList addresses={[]} state="error" onRetry={retry} />);

    expect(screen.getByRole("alert")).toHaveTextContent("Không thể tải địa chỉ");
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(retry).toHaveBeenCalledTimes(1);
  });
});
