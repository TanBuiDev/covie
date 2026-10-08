import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { CovieApiError } from "@/lib/api/api-error";

const { createSavedAddress, listSavedAddressesClient, updateSavedAddress, reverseGeocodeAddress, navigation, browseLocation } = vi.hoisted(() => ({
  createSavedAddress: vi.fn(),
  listSavedAddressesClient: vi.fn(),
  updateSavedAddress: vi.fn(),
  reverseGeocodeAddress: vi.fn(),
  navigation: { back: vi.fn(), push: vi.fn(), refresh: vi.fn() },
  browseLocation: { value: undefined as undefined | { kind: "address"; addressId: string; label: string } },
}));

vi.mock("@/features/profile/address-api", () => ({
  createSavedAddress,
  listSavedAddressesClient,
  updateSavedAddress,
  reverseGeocodeAddress,
}));
vi.mock("next/navigation", () => ({ useRouter: () => navigation }));
vi.mock("@/features/location/use-browse-location", () => ({
  useBrowseLocation: () => ({ location: browseLocation.value }),
}));

import { AddressForm } from "@/features/profile/address-form";

vi.mock("@/features/profile/delivery-point-map", () => ({ DeliveryPointMap: ({ onChange, onLocate, locating }: { onChange: (point: { latitude: number; longitude: number }) => void; onLocate: () => void; locating: boolean }) => <><button type="button" aria-label="Dùng vị trí hiện tại" disabled={locating} onClick={onLocate} /><button type="button" onClick={() => onChange({ latitude: 10.9, longitude: 106.9 })}>Chọn điểm thử trên bản đồ</button></> }));

afterEach(()=>vi.unstubAllGlobals());

describe("AddressForm", () => {
  it("saves the adjusted map point with the written address without an extra confirmation checkbox", async () => {
    createSavedAddress.mockResolvedValue({ id: "new" });
    render(<AddressForm />);
    expect(screen.queryByLabelText("Xác nhận điểm giao hàng cho địa chỉ này")).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/Nhãn/), { target: { value: "Nhà" } });
    fireEvent.change(screen.getByLabelText(/Người nhận/), { target: { value: "Nguyễn Văn Minh" } });
    fireEvent.change(screen.getByLabelText(/Số điện thoại/), { target: { value: "0909123456" } });
    fireEvent.change(screen.getByLabelText(/^Địa chỉ \*/), { target: { value: "Căn 1205, tòa S5" } });
    fireEvent.click(screen.getByRole("button", { name: "Chọn điểm thử trên bản đồ" }));
    fireEvent.click(screen.getByRole("button", { name: "Lưu địa chỉ" }));
    await waitFor(() => expect(createSavedAddress).toHaveBeenCalledWith(expect.objectContaining({ addressText: "Căn 1205, tòa S5", latitude: 10.9, longitude: 106.9, pinSource: "USER_ADJUSTED" })));
  });
  it("groups the written address and GPS into one delivery-address section with advanced coordinates collapsed", () => {
    render(<AddressForm />);
    const section = screen.getByRole("group", { name: "Địa chỉ giao hàng" });
    expect(within(section).getByLabelText(/^Địa chỉ \*/)).toBeVisible();
    expect(within(section).getByRole("button", { name: "Dùng vị trí hiện tại" })).toBeVisible();
    expect(screen.queryByText("Điểm giao hàng & Tọa độ")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Lấy vị trí hiện tại" })).not.toBeInTheDocument();
    const advanced = within(section).getByText("Nâng cao: chọn nguồn và nhập tọa độ").closest("details");
    expect(advanced).not.toHaveAttribute("open");
    fireEvent.click(within(section).getByText("Nâng cao: chọn nguồn và nhập tọa độ"));
    expect(advanced).toHaveAttribute("open");
  });
  it("keeps the GPS point and manual address usable when reverse lookup fails", async () => {
    reverseGeocodeAddress.mockRejectedValueOnce(new Error("provider unavailable"));
    vi.stubGlobal("navigator", Object.assign(Object.create(navigator), { geolocation: { getCurrentPosition: (success: (position: unknown) => void) => success({ coords: { latitude: 10.85, longitude: 106.85 } }) } }));
    render(<AddressForm />);
    fireEvent.change(screen.getByLabelText(/^Địa chỉ \*/), { target: { value: "Địa chỉ đã nhập" } });
    fireEvent.click(screen.getByRole("button", { name: "Dùng vị trí hiện tại" }));
    await screen.findByText(/Đã lấy tọa độ nhưng chưa tra được địa chỉ/);
    expect(screen.getByLabelText(/^Địa chỉ \*/)).toHaveValue("Địa chỉ đã nhập");
    expect(document.querySelector('input[type="hidden"][name="latitude"]')).toHaveValue("10.85");
    expect(screen.getByRole("button", { name: "Dùng vị trí hiện tại" })).toBeEnabled();
  });
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

  it("uses the latest explicitly selected saved point", async () => {
    listSavedAddressesClient.mockResolvedValue([
      { id: "home", label: "Nhà", addressText: "Địa chỉ nhà", latitude: 10.8, longitude: 106.8 },
      { id: "office", label: "Công ty", addressText: "Địa chỉ công ty", latitude: 10.9, longitude: 106.9 },
    ]);
    createSavedAddress.mockResolvedValue({ id: "new" });
    render(<AddressForm />);
    fireEvent.change(screen.getByLabelText("Nguồn vị trí"), { target: { value: "SAVED" } });
    await screen.findByRole("option", { name: "Nhà — Địa chỉ nhà" });
    fireEvent.change(screen.getByLabelText("Địa chỉ lấy điểm"), { target: { value: "home" } });
    fireEvent.change(screen.getByLabelText("Địa chỉ lấy điểm"), { target: { value: "office" } });
    fireEvent.change(screen.getByLabelText(/Nhãn/), { target: { value: "Điểm nhận" } });
    fireEvent.change(screen.getByLabelText(/Người nhận/), { target: { value: "Nguyễn Văn Minh" } });
    fireEvent.change(screen.getByLabelText(/Số điện thoại/), { target: { value: "0909123456" } });
    fireEvent.change(screen.getByLabelText(/^Địa chỉ \*/), { target: { value: "Địa chỉ nhận hàng" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu địa chỉ" }));
    await waitFor(() => expect(createSavedAddress).toHaveBeenCalledWith(expect.objectContaining({
      latitude: 10.9, longitude: 106.9, addressText: "Địa chỉ nhận hàng", pinSource: "USER_ADJUSTED",
    })));
  });
  it("handles denied GPS and submits a retried GPS point", async () => {
    const getCurrentPosition=vi.fn().mockImplementationOnce((_success,error)=>error({code:1}))
      .mockImplementationOnce(success=>success({coords:{latitude:10.85,longitude:106.85}}));
    vi.stubGlobal("navigator",Object.assign(Object.create(navigator),{geolocation:{getCurrentPosition}}));
    createSavedAddress.mockResolvedValue({id:"new"});
    render(<AddressForm/>);
    expect(screen.queryByLabelText(/^Vĩ độ/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button",{name:"Dùng vị trí hiện tại"}));
    expect(screen.getByRole("alert")).toHaveTextContent("Không thể lấy vị trí hiện tại");
    expect(createSavedAddress).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button",{name:"Dùng vị trí hiện tại"}));
    fireEvent.change(screen.getByLabelText(/Nhãn/),{target:{value:"Nhà"}});
    fireEvent.change(screen.getByLabelText(/Người nhận/),{target:{value:"Nguyễn Văn Minh"}});
    fireEvent.change(screen.getByLabelText(/Số điện thoại/),{target:{value:"0909123456"}});
    fireEvent.change(screen.getByLabelText(/^Địa chỉ/),{target:{value:"TP.HCM"}});
    fireEvent.click(screen.getByRole("button",{name:"Lưu địa chỉ"}));
    await waitFor(()=>expect(createSavedAddress).toHaveBeenCalledWith(expect.objectContaining({latitude:10.85,longitude:106.85,pinSource:"GPS"})));
  });
  it("saves the latest edited address with a valid delivery point", async () => {
    browseLocation.value = {kind:"address",addressId:"address-1",label:"Nhà cũ"};
    listSavedAddressesClient.mockResolvedValue([{id:"address-1",latitude:10.842,longitude:106.842}]);
    createSavedAddress.mockResolvedValue({id:"address-2"});
    render(<AddressForm/>);
    fireEvent.change(screen.getByLabelText(/Nhãn/),{target:{value:"Nhà mới"}});
    fireEvent.change(screen.getByLabelText(/Người nhận/),{target:{value:"Nguyễn Văn Minh"}});
    fireEvent.change(screen.getByLabelText(/Số điện thoại/),{target:{value:"0909123456"}});
    fireEvent.change(screen.getByLabelText(/^Địa chỉ/),{target:{value:"Địa chỉ mới"}});
    fireEvent.change(screen.getByLabelText(/^Địa chỉ/),{target:{value:"Địa chỉ khác"}});
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
    reverseGeocodeAddress.mockReset().mockResolvedValue({ addressText: "Địa chỉ gợi ý", attribution: "© OpenStreetMap contributors", attributionUrl: "https://www.openstreetmap.org/copyright" });
    createSavedAddress.mockReset();
    listSavedAddressesClient.mockReset();
    updateSavedAddress.mockReset();
    navigation.back.mockReset();
    navigation.push.mockReset();
    navigation.refresh.mockReset();
    browseLocation.value = undefined;
  });

  it("fills an editable text address from GPS", async () => {
    vi.stubGlobal("navigator", Object.assign(Object.create(navigator), { geolocation: { getCurrentPosition: vi.fn(success => success({ coords: { latitude: 10.85, longitude: 106.85 } })) } }));
    render(<AddressForm />);
    fireEvent.click(screen.getByRole("button", { name: "Dùng vị trí hiện tại" }));
    await waitFor(() => expect(screen.getByLabelText(/^Địa chỉ \*/)).toHaveValue("Địa chỉ gợi ý"));
    expect(reverseGeocodeAddress).toHaveBeenCalledWith(10.85, 106.85);
    fireEvent.change(screen.getByLabelText(/^Địa chỉ \*/), { target: { value: "Căn hộ 123, địa chỉ gợi ý" } });
    expect(screen.getByLabelText(/^Địa chỉ \*/)).toHaveValue("Căn hộ 123, địa chỉ gợi ý");
  });

  it("does not overwrite a text edit made while GPS lookup is pending", async () => {
    let resolve!: (value: { addressText: string }) => void;
    reverseGeocodeAddress.mockReturnValue(new Promise(done => { resolve = done; }));
    vi.stubGlobal("navigator", Object.assign(Object.create(navigator), { geolocation: { getCurrentPosition: vi.fn(success => success({ coords: { latitude: 10.85, longitude: 106.85 } })) } }));
    render(<AddressForm />);
    fireEvent.click(screen.getByRole("button", { name: "Dùng vị trí hiện tại" }));
    fireEvent.change(screen.getByLabelText(/^Địa chỉ \*/), { target: { value: "Địa chỉ tôi đang sửa" } });
    resolve({ addressText: "Địa chỉ cũ từ GPS" });
    await waitFor(() => expect(screen.getByRole("button", { name: "Dùng vị trí hiện tại" })).toBeEnabled());
    expect(screen.getByLabelText(/^Địa chỉ \*/)).toHaveValue("Địa chỉ tôi đang sửa");
  });

  it("ignores a reverse lookup after switching the delivery-point source", async () => {
    let resolve!: (value: { addressText: string }) => void;
    reverseGeocodeAddress.mockReturnValue(new Promise(done => { resolve = done; }));
    vi.stubGlobal("navigator", Object.assign(Object.create(navigator), { geolocation: { getCurrentPosition: vi.fn(success => success({ coords: { latitude: 10.85, longitude: 106.85 } })) } }));
    render(<AddressForm />);
    fireEvent.click(screen.getByRole("button", { name: "Dùng vị trí hiện tại" }));
    fireEvent.change(screen.getByLabelText("Nguồn vị trí"), { target: { value: "MANUAL" } });
    resolve({ addressText: "Kết quả không còn phù hợp" });
    await waitFor(() => expect(screen.getByLabelText(/^Vĩ độ/)).toBeVisible());
    expect(screen.getByLabelText(/^Địa chỉ \*/)).toHaveValue("");
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
    fireEvent.click(screen.getByRole("button", { name: "Lưu địa chỉ" }));

    await waitFor(() => expect(createSavedAddress).toHaveBeenCalledWith(expect.objectContaining({
      latitude: 10.842,
      longitude: 106.842,
    })));
  });
});
