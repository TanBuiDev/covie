import { act, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { CartDetail } from "@/features/cart/cart-detail";
import { CovieApiError } from "@/lib/api/api-error";
const mocks = vi.hoisted(() => ({getCart:vi.fn(),removeCartItem:vi.fn(),updateCartItem:vi.fn()}));
vi.mock("@/features/cart/cart-api",()=>mocks);
beforeEach(()=>vi.resetAllMocks());

describe("cart detail", () => {
  it("resets cart data and pending state on navigation and ignores a late old refresh", async () => {
    const oldCart = {id:"old",cartType:"PRODUCT" as const,version:1,items:[{id:"a",itemName:"Món cũ",quantity:2}]};
    const newCart = {id:"new",cartType:"PRODUCT" as const,version:2,items:[{id:"b",itemName:"Món mới",quantity:1}]};
    let resolveOld!: (value: typeof oldCart) => void;
    mocks.updateCartItem.mockResolvedValue(undefined);
    mocks.getCart.mockReturnValueOnce(new Promise(resolve=>{resolveOld=resolve;}));
    const view=render(<CartDetail initialCart={oldCart}/>);
    await act(async()=>{fireEvent.click(screen.getByRole("button",{name:"Tăng số lượng"}));});
    view.rerender(<CartDetail initialCart={newCart}/>);
    expect(screen.getByText("Món mới")).toBeInTheDocument();
    expect(screen.queryByText("Món cũ")).not.toBeInTheDocument();
    expect(screen.getByRole("link",{name:"Tiếp tục đặt hàng"})).toHaveAttribute("href","/checkout?source=CART&cartId=new");
    await act(async()=>{resolveOld({...oldCart,version:3});});
    expect(screen.getByText("Món mới")).toBeInTheDocument();
    expect(screen.queryByText("Món cũ")).not.toBeInTheDocument();
  });
  it.each(["command","refresh","retry"])("requires login after %s returns401 and hides stale cart actions", async source => {
    const cart = {id:"cart",cartType:"PRODUCT" as const,version:1,items:[{id:"a",quantity:2}]};
    const error = new CovieApiError(401,"AUTH_REQUIRED","private diagnostic");
    mocks.updateCartItem.mockResolvedValue(undefined);
    mocks.getCart.mockResolvedValue(cart);
    if(source === "command") mocks.updateCartItem.mockRejectedValueOnce(error);
    else if(source === "refresh") mocks.getCart.mockRejectedValueOnce(error);
    else mocks.getCart.mockRejectedValueOnce(new Error("offline")).mockRejectedValueOnce(error);
    render(<CartDetail initialCart={cart}/>);
    fireEvent.click(screen.getByRole("button",{name:"Tăng số lượng"}));
    if(source === "retry") fireEvent.click(await screen.findByRole("button",{name:"Thử lại tải giỏ hàng"}));
    expect(await screen.findByRole("link",{name:"Đăng nhập lại"})).toHaveAttribute("href","/login?returnTo=%2Fcart%2Fcart");
    expect(screen.queryByRole("button",{name:"Tăng số lượng"})).not.toBeInTheDocument();
    expect(screen.queryByRole("link",{name:"Tiếp tục đặt hàng"})).not.toBeInTheDocument();
    expect(screen.queryByText("private diagnostic")).not.toBeInTheDocument();
  });
  it.each([false,true])("blocks stale cart after refresh failure (conflict=%s) and retries only the read", async conflict => {
    const cart = {id:"cart",cartType:"PRODUCT" as const,version:1,items:[{id:"a",quantity:2}]};
    if(conflict) mocks.updateCartItem.mockRejectedValueOnce(new CovieApiError(409,"RESOURCE_VERSION_CONFLICT","internal"));
    else mocks.updateCartItem.mockResolvedValueOnce(undefined);
    mocks.getCart.mockRejectedValue(new Error("offline"));
    render(<CartDetail initialCart={cart}/>);
    fireEvent.click(screen.getByRole("button",{name:"Tăng số lượng"}));
    const retry = await screen.findByRole("button",{name:"Thử lại tải giỏ hàng"});
    expect(screen.getByRole("button",{name:"Tăng số lượng"})).toBeDisabled();
    expect(screen.queryByRole("link",{name:"Tiếp tục đặt hàng"})).not.toBeInTheDocument();
    expect(screen.queryByText(/CoVie đã tải lại dữ liệu mới nhất/)).not.toBeInTheDocument();
    mocks.getCart.mockResolvedValue({...cart,version:2});
    fireEvent.click(retry);
    expect(await screen.findByRole("link",{name:"Tiếp tục đặt hàng"})).toBeInTheDocument();
    expect(mocks.updateCartItem).toHaveBeenCalledTimes(1);
  });
  it("locks all cart actions and checkout while a mutation is pending, including same-batch clicks", async () => {
    const cart = {id:"cart",cartType:"PRODUCT" as const,version:1,items:[{id:"a",quantity:2},{id:"b",quantity:1}]};
    let release!:()=>void;
    mocks.updateCartItem.mockReturnValueOnce(new Promise<void>(resolve=>{release=resolve;}));
    mocks.getCart.mockResolvedValue(cart);
    render(<CartDetail initialCart={cart}/>);
    const buttons = screen.getAllByRole("button",{name:"Tăng số lượng"});
    act(()=>{buttons[0]!.click();buttons[1]!.click();});
    expect(mocks.updateCartItem).toHaveBeenCalledTimes(1);
    for(const button of screen.getAllByRole("button",{name:/Tăng số lượng|Xóa/})) expect(button).toBeDisabled();
    expect(screen.queryByRole("link",{name:"Tiếp tục đặt hàng"})).not.toBeInTheDocument();
    await act(async()=>{release();});
    expect(screen.getByRole("link",{name:"Tiếp tục đặt hàng"})).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole("button",{name:"Tăng số lượng"})[0]!);
    expect(mocks.updateCartItem).toHaveBeenCalledTimes(2);
  });
  it("keeps service quantity fixed at one", () => {
    render(
      <CartDetail
        initialCart={{
          id: "service-cart",
          merchantLocationId: "location-a",
          cartType: "SERVICE",
          version: 3,
          items: [{ id: "item-a", itemName: "Dọn nhà", quantity: 1 }],
        }}
      />,
    );

    expect(screen.getByRole("heading", { name: "Giỏ dịch vụ" })).toBeInTheDocument();
    expect(screen.getByText("Số lượng: 1 (cố định)")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Tăng số lượng" })).not.toBeInTheDocument();
    expect(screen.queryByText("location-a")).not.toBeInTheDocument();
    expect(screen.getByText("Tạm tính hiện tại: 0 ₫")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Tiếp tục đặt hàng" })).toHaveAttribute(
      "href",
      "/checkout?source=CART&cartId=service-cart",
    );
  });

  it("renders product prices and line amount in Vietnamese currency", () => {
    render(
      <CartDetail
        initialCart={{
          id: "product-cart",
          merchantLocationId: "location-b",
          cartType: "PRODUCT",
          version: 1,
          items: [{ id: "item-b", itemName: "Rau củ theo mùa", quantity: 2, priceSnapshot: 85_000 }],
        }}
      />,
    );

    expect(screen.getByRole("heading", { name: "Giỏ sản phẩm" })).toBeInTheDocument();
    expect(screen.getByText("85.000 ₫")).toBeInTheDocument();
    expect(screen.getByText("170.000 ₫")).toBeInTheDocument();
    expect(screen.getByText("Phí giao hàng sẽ được hiển thị khi bạn xác nhận đơn.")).toBeInTheDocument();
    expect(screen.queryByText("Có sẵn")).not.toBeInTheDocument();
    expect(screen.getByText("Giá và khả dụng sẽ được kiểm tra lại trước khi đặt hàng.")).toBeInTheDocument();
  });
});
