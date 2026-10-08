import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { CovieApiError } from "@/lib/api/api-error";
import { CheckoutPreview } from "./checkout-preview";

const { previewCheckout, commitCheckout } = vi.hoisted(() => ({ previewCheckout: vi.fn(), commitCheckout: vi.fn() }));
vi.mock("./checkout-api", () => ({ previewCheckout, commitCheckout }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));

afterEach(() => {
  cleanup();
  previewCheckout.mockReset();
  commitCheckout.mockReset();
});

const carts = [{ id: "cart", merchantLocationId: "location", cartType: "PRODUCT" as const, version: 1, items: [] }];

it("sends one commit when both checkout buttons are clicked in the same event batch",async()=>{
  previewCheckout.mockResolvedValue({checkoutToken:"token",subtotal:100,shippingFee:0,total:100,fulfillmentMethod:"PICKUP"});
  commitCheckout.mockReturnValue(new Promise(()=>{}));
  render(<CheckoutPreview carts={carts} addresses={[]}/>);
  fireEvent.click(screen.getByRole("button",{name:"Xem trước"}));
  const buttons=await screen.findAllByRole("button",{name:"Đặt hàng"});
  act(()=>{fireEvent.click(buttons[0]!);fireEvent.click(buttons[1]!);});
  expect(commitCheckout).toHaveBeenCalledTimes(1);
});

it("disables the cart selection while a commit is pending",async()=>{
  previewCheckout.mockResolvedValue({checkoutToken:"token",subtotal:100,shippingFee:0,total:100,fulfillmentMethod:"PICKUP"});
  commitCheckout.mockReturnValue(new Promise(()=>{}));
  render(<CheckoutPreview carts={[...carts,{...carts[0]!,id:"cart-2"}]} addresses={[]}/>);
  fireEvent.click(screen.getByRole("button",{name:"Xem trước"}));
  const buttons=await screen.findAllByRole("button",{name:"Đặt hàng"});
  fireEvent.click(buttons[0]!);
  expect(screen.getByRole("combobox",{name:"Giỏ hàng"})).toBeDisabled();
});

it("changes a direct product quantity and invalidates its old price before requesting the new quantity", async () => {
  previewCheckout.mockResolvedValue({checkoutToken:"token",subtotal:130000,shippingFee:0,total:130000,fulfillmentMethod:"PICKUP"});
  render(<CheckoutPreview carts={[]} addresses={[]} directSelection={{itemId:"item",locationId:"location",locationVariantId:"offer",
    itemName:"Sản phẩm",type:"PRODUCT",quantity:2}}/>);
  fireEvent.click(screen.getByRole("button",{name:"Xem trước"}));
  await screen.findAllByRole("button",{name:"Đặt hàng"});
  fireEvent.click(screen.getByRole("button",{name:"Tăng số lượng"}));
  expect(screen.getByLabelText("Số lượng")).toHaveTextContent("3");
  expect(screen.queryAllByRole("button",{name:"Đặt hàng"})).toHaveLength(0);
  fireEvent.click(screen.getByRole("button",{name:"Xem trước"}));
  await waitFor(()=>expect(previewCheckout).toHaveBeenLastCalledWith(expect.objectContaining({items:[{locationVariantId:"offer",quantity:3}]})));
  expect(commitCheckout).not.toHaveBeenCalled();
});

it("changes a Need product quantity while retaining its Need source", async () => {
  previewCheckout.mockResolvedValue({checkoutToken:"token",subtotal:100,shippingFee:0,total:100,fulfillmentMethod:"PICKUP"});
  render(<CheckoutPreview carts={[]} addresses={[]} needSelection={{needRequestId:"need",needResponseItemId:"response-item",locationVariantId:"offer",
    itemName:"Món cần mua",type:"PRODUCT",quantity:1}}/>);
  expect(screen.getByRole("button",{name:"Giảm số lượng"})).toBeDisabled();
  fireEvent.click(screen.getByRole("button",{name:"Tăng số lượng"}));
  fireEvent.click(screen.getByRole("button",{name:"Xem trước"}));
  await waitFor(()=>expect(previewCheckout).toHaveBeenCalledWith(expect.objectContaining({source:{type:"NEED_REQUEST",needRequestId:"need",needResponseItemId:"response-item"},items:[{locationVariantId:"offer",quantity:2}]})));
});

it.each(["SERVICE","RESALE"])("keeps %s checkout quantity fixed without product controls", kind => {
  render(<CheckoutPreview carts={[]} addresses={[]} directSelection={{itemId:"item",locationId:"location",locationVariantId:"offer",
    itemName:"Mục cố định",type:kind === "SERVICE" ? "SERVICE" : "PRODUCT",fixedQuantity:kind === "RESALE",quantity:1}}/>);
  expect(screen.queryByRole("button",{name:"Tăng số lượng"})).not.toBeInTheDocument();
  expect(screen.queryByRole("button",{name:"Giảm số lượng"})).not.toBeInTheDocument();
  expect(screen.getByText("Số lượng: 1",{exact:false})).toBeInTheDocument();
});

it("discards a pending preview after a quantity change", async () => {
  let resolvePreview!: (value: object) => void;
  previewCheckout.mockReturnValue(new Promise(resolve=>{resolvePreview=resolve;}));
  render(<CheckoutPreview carts={[]} addresses={[]} directSelection={{itemId:"item",locationId:"location",locationVariantId:"offer",
    itemName:"Sản phẩm",type:"PRODUCT",quantity:1}}/>);
  fireEvent.click(screen.getByRole("button",{name:"Xem trước"}));
  fireEvent.click(screen.getByRole("button",{name:"Tăng số lượng"}));
  await act(async()=>resolvePreview({checkoutToken:"stale",subtotal:65000,total:65000,fulfillmentMethod:"PICKUP"}));
  expect(screen.queryAllByRole("button",{name:"Đặt hàng"})).toHaveLength(0);
  expect(screen.getByLabelText("Số lượng")).toHaveTextContent("2");
  expect(screen.getByRole("button",{name:"Xem trước"})).toBeEnabled();
});

it("previews a direct purchase using its selected offer without a cart or committing", async () => {
  previewCheckout.mockResolvedValueOnce({checkoutToken:"direct-token",subtotal:130000,shippingFee:0,total:130000,fulfillmentMethod:"PICKUP"});
  render(<CheckoutPreview carts={[]} addresses={[]} directSelection={{itemId:"item-1",locationId:"location-1",
    locationVariantId:"offer-1",itemName:"Món mua trực tiếp",type:"PRODUCT",quantity:2}}/>);
  fireEvent.click(screen.getByRole("button",{name:"Xem trước"}));
  await screen.findAllByRole("button",{name:"Đặt hàng"});
  expect(previewCheckout).toHaveBeenCalledWith({source:{type:"DIRECT"},items:[{locationVariantId:"offer-1",quantity:2}],
    fulfillmentMethod:"PICKUP",addressId:undefined,paymentMethod:"COD"});
  expect(screen.getByRole("link",{name:"Quay lại sản phẩm"})).toHaveAttribute("href","/items/item-1?locationId=location-1");
  expect(commitCheckout).not.toHaveBeenCalled();
});

it("changes a default address inside checkout and discards the previous token before re-previewing", async () => {
  previewCheckout.mockResolvedValueOnce({checkoutToken:"home-token",subtotal:65000,shippingFee:12000,total:77000,fulfillmentMethod:"SELLER_DELIVERY"})
    .mockResolvedValueOnce({checkoutToken:"office-token",subtotal:65000,shippingFee:15000,total:80000,fulfillmentMethod:"SELLER_DELIVERY"});
  render(<CheckoutPreview carts={carts} addresses={[
    {id:"home",label:"Nhà",addressText:"Địa chỉ nhà",defaultAddress:true},
    {id:"office",label:"Công ty",addressText:"Địa chỉ công ty"},
  ]}/>);
  fireEvent.click(screen.getByLabelText("SELLER_DELIVERY"));
  fireEvent.click(screen.getByRole("button",{name:"Xem trước"}));
  await screen.findAllByRole("button",{name:"Đặt hàng"});
  fireEvent.click(screen.getByRole("button",{name:"Thay đổi"}));
  fireEvent.change(screen.getByLabelText("Địa chỉ"),{target:{value:"office"}});
  expect(screen.getByText("Địa chỉ công ty",{exact:false})).toBeInTheDocument();
  expect(screen.queryAllByRole("button",{name:"Đặt hàng"})).toHaveLength(0);
  fireEvent.click(screen.getByRole("button",{name:"Xem trước"}));
  await waitFor(()=>expect(previewCheckout).toHaveBeenLastCalledWith(expect.objectContaining({addressId:"office"})));
  expect(commitCheckout).not.toHaveBeenCalled();
});

it("explains a missing delivery address and prevents a doomed preview request", () => {
  render(<CheckoutPreview carts={carts} addresses={[]}/>);
  fireEvent.click(screen.getByLabelText("SELLER_DELIVERY"));
  expect(screen.getByRole("button",{name:"Xem trước"})).toBeDisabled();
  expect(screen.getByText("Chọn địa chỉ nhận hàng trước khi xem giá.")).toBeInTheDocument();
  expect(screen.getByRole("link",{name:"Thêm địa chỉ nhận hàng"})).toHaveAttribute("href","/me/addresses/new");
  expect(previewCheckout).not.toHaveBeenCalled();
});

it("discards a late preview after changing fulfillment and requests a fresh one", async () => {
  let resolvePreview!: (value: object) => void;
  previewCheckout.mockReturnValueOnce(new Promise(resolve=>{resolvePreview=resolve;}));
  render(<CheckoutPreview carts={carts} addresses={[{id:"home",defaultAddress:true}]}/>);
  fireEvent.click(screen.getByRole("button",{name:"Xem trước"}));
  fireEvent.click(screen.getByLabelText("SELLER_DELIVERY"));
  await act(async()=>resolvePreview({checkoutToken:"old-pickup",subtotal:65000,shippingFee:0,total:65000,fulfillmentMethod:"PICKUP"}));
  expect(screen.queryAllByRole("button",{name:"Đặt hàng"})).toHaveLength(0);
  expect(screen.getByRole("button",{name:"Xem trước"})).toBeEnabled();
  expect(commitCheckout).not.toHaveBeenCalled();
});

it("reviews a Need item with backend totals before committing and uses the canonical Need source", async () => {
  previewCheckout.mockResolvedValueOnce({checkoutToken:"need-token",subtotal:65000,shippingFee:0,total:65000,currency:"VND",fulfillmentMethod:"PICKUP"});
  render(<CheckoutPreview carts={[]} addresses={[]} needSelection={{needRequestId:"need-1",needResponseItemId:"response-item-1",
    locationVariantId:"offer-1",itemName:"Cơm tấm",type:"PRODUCT",quantity:1}}/>);
  expect(screen.getByText("Cơm tấm")).toBeInTheDocument();
  expect(commitCheckout).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button",{name:"Xem trước"}));
  await waitFor(()=>expect(screen.getAllByRole("button",{name:"Đặt hàng"})).toHaveLength(2));
  expect(previewCheckout).toHaveBeenCalledWith({source:{type:"NEED_REQUEST",needRequestId:"need-1",needResponseItemId:"response-item-1"},
    items:[{locationVariantId:"offer-1",quantity:1}],fulfillmentMethod:"PICKUP",addressId:undefined,paymentMethod:"COD"});
  expect(screen.getAllByText("65.000 ₫").length).toBeGreaterThan(0);
  expect(commitCheckout).not.toHaveBeenCalled();
});

it("uses SERVICE fulfillment and a saved address for a Need service without product pickup controls", async () => {
  previewCheckout.mockResolvedValueOnce({checkoutToken:"service-token",subtotal:200000,shippingFee:0,total:200000,currency:"VND",fulfillmentMethod:"SERVICE"});
  render(<CheckoutPreview carts={[]} addresses={[{id:"address-1",label:"Nhà",defaultAddress:true}]} needSelection={{
    needRequestId:"need-service",needResponseItemId:"service-item",locationVariantId:"service-offer",itemName:"Sửa vòi nước",type:"SERVICE",quantity:1}}/>);
  expect(screen.queryByLabelText("PICKUP")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button",{name:"Xem trước"}));
  await waitFor(()=>expect(previewCheckout).toHaveBeenCalledWith(expect.objectContaining({fulfillmentMethod:"SERVICE",addressId:"address-1"})));
  expect(commitCheckout).not.toHaveBeenCalled();
});

it("renders a negotiated delivery preview without inventing a zero fee", async () => {
  previewCheckout.mockResolvedValueOnce({ checkoutToken: "token", subtotal: 100_000, shippingFee: undefined, total: undefined, currency: "VND", fulfillmentMethod: "SELLER_DELIVERY" });
  render(<CheckoutPreview carts={carts} addresses={[{ id: "address", label: "Nhà", addressText: "1 CoVie" }]} />);
  fireEvent.click(screen.getByLabelText("SELLER_DELIVERY"));
  fireEvent.change(screen.getByLabelText("Địa chỉ"), { target: { value: "address" } });
  fireEvent.click(screen.getByRole("button", { name: "Xem trước" }));
  await waitFor(() => expect(screen.getAllByText("Thỏa thuận với người bán").length).toBeGreaterThan(0));
  expect(screen.getAllByText("Khoản tiền đã biết").length).toBeGreaterThan(0);
  expect(screen.queryByText("0 ₫")).not.toBeInTheDocument();
});

it("discards a changed checkout token and requires a fresh preview", async () => {
  previewCheckout.mockResolvedValueOnce({ checkoutToken: "token", subtotal: 100_000, shippingFee: 0, total: 100_000, currency: "VND", fulfillmentMethod: "PICKUP" });
  commitCheckout.mockRejectedValueOnce(new CovieApiError(409, "CHECKOUT_CHANGED", "changed"));
  render(<CheckoutPreview carts={carts} addresses={[]} />);
  fireEvent.click(screen.getByRole("button", { name: "Xem trước" }));
  await waitFor(() => expect(screen.getAllByRole("button", { name: "Đặt hàng" })).toHaveLength(2));
  fireEvent.click(screen.getAllByRole("button", { name: "Đặt hàng" })[0]!);
  await waitFor(() => expect(screen.getByRole("alertdialog")).toBeInTheDocument());
  expect(screen.getByText("Giá hoặc thông tin đã thay đổi")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Kiểm tra giá/thông tin mới" })).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Đặt hàng" })).not.toBeInTheDocument();
});
