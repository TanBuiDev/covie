import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { CovieApiError } from "@/lib/api/api-error";

const { createSavedAddress, listSavedAddressesClient, updateSavedAddress, navigation, browseLocation } = vi.hoisted(() => ({
  createSavedAddress: vi.fn(),
  listSavedAddressesClient: vi.fn(),
  updateSavedAddress: vi.fn(),
  navigation: { back: vi.fn(), push: vi.fn(), refresh: vi.fn() },
  browseLocation: { value: undefined as undefined | { kind: "address"; addressId: string; label: string } },
}));

vi.mock("@/features/profile/address-api", () => ({
  createSavedAddress,
  listSavedAddressesClient,
  updateSavedAddress,
}));
vi.mock("next/navigation", () => ({ useRouter: () => navigation }));
vi.mock("@/features/location/use-browse-location", () => ({
  useBrowseLocation: () => ({ location: browseLocation.value }),
}));

import { AddressForm } from "@/features/profile/address-form";

afterEach(()=>vi.unstubAllGlobals());

describe("AddressForm", () => {
  it("prevents duplicate saves while an address request is pending and allows retry after failure", async () => {
    let rejectSave!: (error: Error) => void;
    createSavedAddress.mockReturnValueOnce(new Promise((_resolve, reject) => { rejectSave = reject; }))
      .mockResolvedValueOnce({ id: "new" });
    render(<AddressForm />);
    fireEvent.change(screen.getByLabelText(/Nhãn/), { target: { value: "Nhà" } });
    fireEvent.change(screen.getByLabelText(/Người nhận/), { target: { value: "Nguyễn Văn Minh" } });
    fireEvent.change(screen.getByLabelText(/Số điện thoại/), { target: { value: "0909123456" } });
    fireEvent.change(screen.getByLabelText(/^Địa chỉ/), { target: { value: "TP.HCM" } });
    fireEvent.change(screen.getByLabelText("Nguồn vị trí"), { target: { value: "MANUAL" } });
    fireEvent.change(screen.getByLabelText(/^Vĩ độ/), { target: { value: "10.8" } });
    fireEvent.change(screen.getByLabelText(/^Kinh độ/), { target: { value: "106.8" } });
    fireEvent.click(screen.getByLabelText("Xác nhận điểm giao hàng cho địa chỉ này"));
    const save = screen.getByRole("button", { name: "Lưu địa chỉ" });
    fireEvent.click(save);
    expect(save).toBeDisabled();
    fireEvent.click(save);
    expect(createSavedAddress).toHaveBeenCalledTimes(1);
    rejectSave(new Error("offline"));
    await screen.findByText("Không thể lưu địa chỉ. Vui lòng thử lại.");
    expect(save).toBeEnabled();
    fireEvent.click(save);
    await waitFor(() => expect(createSavedAddress).toHaveBeenCalledTimes(2));
  });
  it("ignores a saved-address failure after switching to another point source", async () => {
    let rejectLoad!: (error: Error) => void;
    listSavedAddressesClient.mockReturnValue(new Promise((_resolve, reject) => { rejectLoad = reject; }));
    render(<AddressForm />);
    fireEvent.change(screen.getByLabelText("Nguồn vị trí"), { target: { value: "SAVED" } });
    fireEvent.change(screen.getByLabelText("Nguồn vị trí"), { target: { value: "MANUAL" } });
    rejectLoad(new Error("offline"));
    await waitFor(() => expect(screen.getByLabelText(/^Vĩ độ/)).toBeInTheDocument());
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("uses the explicitly selected saved point and clears confirmation when it changes", async () => {
    listSavedAddressesClient.mockResolvedValue([
      { id: "home", label: "Nhà", addressText: "Địa chỉ nhà", latitude: 10.8, longitude: 106.8 },
      { id: "office", label: "Công ty", addressText: "Địa chỉ công ty", latitude: 10.9, longitude: 106.9 },
    ]);
    createSavedAddress.mockResolvedValue({ id: "new" });
    render(<AddressForm />);
    fireEvent.change(screen.getByLabelText("Nguồn vị trí"), { target: { value: "SAVED" } });
    await screen.findByRole("option", { name: "Nhà — Địa chỉ nhà" });
    fireEvent.change(screen.getByLabelText("Địa chỉ lấy điểm"), { target: { value: "home" } });
    const confirmation = screen.getByLabelText("Xác nhận điểm giao hàng cho địa chỉ này");
    fireEvent.click(confirmation);
    fireEvent.change(screen.getByLabelText("Địa chỉ lấy điểm"), { target: { value: "office" } });
    expect(confirmation).not.toBeChecked();
    fireEvent.change(screen.getByLabelText(/Nhãn/), { target: { value: "Điểm nhận" } });
    fireEvent.change(screen.getByLabelText(/Người nhận/), { target: { value: "Nguyễn Văn Minh" } });
    fireEvent.change(screen.getByLabelText(/Số điện thoại/), { target: { value: "0909123456" } });
    fireEvent.change(screen.getByLabelText(/^Địa chỉ \*/), { target: { value: "Địa chỉ nhận hàng" } });
    fireEvent.click(confirmation);
    fireEvent.click(screen.getByRole("button", { name: "Lưu địa chỉ" }));
    await waitFor(() => expect(createSavedAddress).toHaveBeenCalledWith(expect.objectContaining({
      latitude: 10.9, longitude: 106.9, addressText: "Địa chỉ nhận hàng", pinSource: "USER_ADJUSTED",
    })));
  });
  it("handles denied GPS and submits a retried GPS point only after confirmation", async () => {
    const getCurrentPosition=vi.fn().mockImplementationOnce((_success,error)=>error({code:1}))
      .mockImplementationOnce(success=>success({coords:{latitude:10.85,longitude:106.85}}));
    vi.stubGlobal("navigator",Object.assign(Object.create(navigator),{geolocation:{getCurrentPosition}}));
    createSavedAddress.mockResolvedValue({id:"new"});
    render(<AddressForm/>);
    expect(screen.queryByLabelText(/^Vĩ độ/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button",{name:"Lấy vị trí hiện tại"}));
    expect(screen.getByRole("alert")).toHaveTextContent("Không thể lấy vị trí hiện tại");
    expect(createSavedAddress).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button",{name:"Lấy vị trí hiện tại"}));
    fireEvent.change(screen.getByLabelText(/Nhãn/),{target:{value:"Nhà"}});
    fireEvent.change(screen.getByLabelText(/Người nhận/),{target:{value:"Nguyễn Văn Minh"}});
    fireEvent.change(screen.getByLabelText(/Số điện thoại/),{target:{value:"0909123456"}});
    fireEvent.change(screen.getByLabelText(/^Địa chỉ/),{target:{value:"TP.HCM"}});
    fireEvent.click(screen.getByLabelText("Xác nhận điểm giao hàng cho địa chỉ này"));
    fireEvent.click(screen.getByRole("button",{name:"Lưu địa chỉ"}));
    await waitFor(()=>expect(createSavedAddress).toHaveBeenCalledWith(expect.objectContaining({latitude:10.85,longitude:106.85,pinSource:"GPS"})));
  });
  it("requires delivery-point confirmation and invalidates it when the address text changes", async () => {
    browseLocation.value = {kind:"address",addressId:"address-1",label:"Nhà cũ"};
    listSavedAddressesClient.mockResolvedValue([{id:"address-1",latitude:10.842,longitude:106.842}]);
    createSavedAddress.mockResolvedValue({id:"address-2"});
    render(<AddressForm/>);
    fireEvent.change(screen.getByLabelText(/Nhãn/),{target:{value:"Nhà mới"}});
    fireEvent.change(screen.getByLabelText(/Người nhận/),{target:{value:"Nguyễn Văn Minh"}});
    fireEvent.change(screen.getByLabelText(/Số điện thoại/),{target:{value:"0909123456"}});
    fireEvent.change(screen.getByLabelText(/^Địa chỉ/),{target:{value:"Địa chỉ mới"}});
    fireEvent.click(screen.getByRole("button",{name:"Lưu địa chỉ"}));
    await waitFor(()=>expect(screen.getByRole("alert")).toHaveTextContent("Xác nhận điểm giao hàng"));
    expect(createSavedAddress).not.toHaveBeenCalled();
    const confirmation=screen.getByLabelText("Xác nhận điểm giao hàng cho địa chỉ này");
    fireEvent.click(confirmation);
    fireEvent.change(screen.getByLabelText(/^Địa chỉ/),{target:{value:"Địa chỉ khác"}});
    expect(confirmation).not.toBeChecked();
    fireEvent.click(confirmation);
    fireEvent.click(screen.getByRole("button",{name:"Lưu địa chỉ"}));
    await waitFor(()=>expect(createSavedAddress).toHaveBeenCalledWith(expect.objectContaining({addressText:"Địa chỉ khác",latitude:10.842,longitude:106.842})));
  });
  it("does not turn missing saved-address coordinates into a valid zero-zero delivery point", async () => {
    browseLocation.value = {kind:"address",addressId:"address-1",label:"Nhà"};
    listSavedAddressesClient.mockResolvedValue([{id:"address-1",latitude:null,longitude:null}]);
    render(<AddressForm/>);
    fireEvent.change(screen.getByLabelText(/Nhãn/),{target:{value:"Nhà mới"}});
    fireEvent.change(screen.getByLabelText(/Người nhận/),{target:{value:"Nguyễn Văn Minh"}});
    fireEvent.change(screen.getByLabelText(/Số điện thoại/),{target:{value:"0909123456"}});
    fireEvent.change(screen.getByLabelText(/^Địa chỉ/),{target:{value:"TP.HCM"}});
    fireEvent.click(screen.getByRole("button",{name:"Lưu địa chỉ"}));
    await waitFor(()=>expect(screen.getByRole("alert")).toHaveTextContent("Vị trí chưa hợp lệ"));
    expect(createSavedAddress).not.toHaveBeenCalled();
  });
  beforeEach(() => {
    createSavedAddress.mockReset();
    listSavedAddressesClient.mockReset();
    updateSavedAddress.mockReset();
    navigation.back.mockReset();
    navigation.push.mockReset();
    navigation.refresh.mockReset();
    browseLocation.value = undefined;
  });

  it("shows backend field errors on the matching address field", async () => {
    createSavedAddress.mockRejectedValueOnce(new CovieApiError(422, "VALIDATION_FAILED", "invalid", undefined, { label: ["REQUIRED"] }));
    render(<AddressForm />);

    fireEvent.change(screen.getByLabelText(/Nhãn/), { target: { value: "Nhà" } });
    fireEvent.change(screen.getByLabelText(/Người nhận/), { target: { value: "Nguyễn Văn Minh" } });
    fireEvent.change(screen.getByLabelText(/Số điện thoại/), { target: { value: "0909123456" } });
    fireEvent.change(screen.getByLabelText(/^Địa chỉ/), { target: { value: "TP.HCM" } });
    fireEvent.change(screen.getByLabelText("Nguồn vị trí"),{target:{value:"MANUAL"}});
    fireEvent.change(screen.getByLabelText(/^Vĩ độ/), { target: { value: "10.77" } });
    fireEvent.change(screen.getByLabelText(/^Kinh độ/), { target: { value: "106.69" } });
    fireEvent.click(screen.getByLabelText("Xác nhận điểm giao hàng cho địa chỉ này"));
    fireEvent.click(screen.getByRole("button", { name: "Lưu địa chỉ" }));

    await waitFor(() => expect(screen.getByText("Trường này là bắt buộc.")).toBeInTheDocument());
  });

  it("marks an address as default when selected", async () => {
    createSavedAddress.mockResolvedValueOnce({ id: "address-1" });
    render(<AddressForm />);
    fireEvent.change(screen.getByLabelText(/Nhãn/), { target: { value: "Nhà" } });
    fireEvent.change(screen.getByLabelText(/Người nhận/), { target: { value: "Nguyễn Văn Minh" } });
    fireEvent.change(screen.getByLabelText(/Số điện thoại/), { target: { value: "0909123456" } });
    fireEvent.change(screen.getByLabelText(/^Địa chỉ/), { target: { value: "TP.HCM" } });
    fireEvent.change(screen.getByLabelText("Nguồn vị trí"),{target:{value:"MANUAL"}});
    fireEvent.change(screen.getByLabelText(/^Vĩ độ/), { target: { value: "10.77" } });
    fireEvent.change(screen.getByLabelText(/^Kinh độ/), { target: { value: "106.69" } });
    fireEvent.click(screen.getByLabelText(/Đặt làm địa chỉ mặc định/));
    fireEvent.click(screen.getByLabelText("Xác nhận điểm giao hàng cho địa chỉ này"));
    fireEvent.click(screen.getByRole("button", { name: "Lưu địa chỉ" }));
    await waitFor(() => expect(createSavedAddress).toHaveBeenCalledWith(expect.objectContaining({ defaultAddress: true })));
    expect(navigation.push).toHaveBeenCalledWith("/me/addresses");
    expect(navigation.refresh).toHaveBeenCalledTimes(1);
  });

  it("reuses the selected saved address coordinates without exposing raw coordinate fields", async () => {
    browseLocation.value = { kind: "address", addressId: "address-1", label: "Nhà" };
    listSavedAddressesClient.mockResolvedValueOnce([
      { id: "address-1", label: "Nhà", latitude: 10.842, longitude: 106.842 },
    ]);
    createSavedAddress.mockResolvedValueOnce({ id: "address-2" });
    render(<AddressForm />);

    expect(screen.queryByLabelText(/^Vĩ độ/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/^Kinh độ/)).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/Nhãn/), { target: { value: "Nhà mới" } });
    fireEvent.change(screen.getByLabelText(/Người nhận/), { target: { value: "Nguyễn Văn Minh" } });
    fireEvent.change(screen.getByLabelText(/Số điện thoại/), { target: { value: "0909123456" } });
    fireEvent.change(screen.getByLabelText(/^Địa chỉ/), { target: { value: "TP.HCM" } });
    fireEvent.click(screen.getByLabelText("Xác nhận điểm giao hàng cho địa chỉ này"));
    fireEvent.click(screen.getByRole("button", { name: "Lưu địa chỉ" }));

    await waitFor(() => expect(createSavedAddress).toHaveBeenCalledWith(expect.objectContaining({
      latitude: 10.842,
      longitude: 106.842,
    })));
  });
});
