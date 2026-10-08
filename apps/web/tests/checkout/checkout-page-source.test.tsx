import { render, screen } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
const mocks = vi.hoisted(() => ({ GET: vi.fn(), requireUser: vi.fn() }));
vi.mock("@/lib/api/server-api-client", () => ({ createServerReadClient: async () => ({ GET: mocks.GET }) }));
vi.mock("@/lib/auth/require-user", () => ({ requireUser: mocks.requireUser }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));
import CheckoutPage from "@/app/(buyer)/checkout/page";
import CheckoutError from "@/app/(buyer)/checkout/error";
import { fireEvent } from "@testing-library/react";

const result = (data: unknown, status = 200) => ({ data, response: new Response(null, { status }) });
const params = {source:"NEED_REQUEST",needRequestId:"need-1",needResponseItemId:"chosen-item",quantity:"1"};
beforeEach(() => {vi.resetAllMocks();});

it("retains the checkout selection in the login return destination", async () => {
  mocks.requireUser.mockRejectedValue(new Error("login redirect"));
  await expect(CheckoutPage({searchParams:Promise.resolve(params)})).rejects.toThrow("login redirect");
  expect(mocks.requireUser).toHaveBeenCalledWith("/checkout?source=NEED_REQUEST&needRequestId=need-1&needResponseItemId=chosen-item&quantity=1");
  expect(mocks.GET).not.toHaveBeenCalled();
});

it("routes a temporary Need read failure to the retry boundary instead of claiming an expired source", async () => {
  mocks.GET.mockResolvedValue(result(undefined,503));
  await expect(CheckoutPage({searchParams:Promise.resolve(params)})).rejects.toThrow();
});

it("offers a retry for unexpected checkout loading errors", () => {
  const retry = vi.fn();
  render(<CheckoutError error={new Error("temporary")} retry={retry}/>);
  expect(screen.getByRole("alert")).toHaveTextContent("Không thể tải thông tin đặt hàng");
  expect(screen.queryByText(/đã hết hiệu lực/)).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button",{name:"Thử lại"}));
  expect(retry).toHaveBeenCalledTimes(1);
});

it("resolves a direct offer at the chosen location without reading or writing a cart", async () => {
  mocks.GET.mockImplementation(async(path:string)=>result(path === "/api/v1/catalog/items/{itemId}" ? {
    id:"product-1",name:"Sản phẩm mua ngay",type:"PRODUCT",listingKind:"STANDARD",resolvedLocation:{id:"location-1"},
    variants:[{name:"Hộp lớn",offer:{locationVariantId:"offer-1",available:true}}],
  } : []));
  render(await CheckoutPage({searchParams:Promise.resolve({source:"DIRECT",itemId:"product-1",locationId:"location-1",locationVariantId:"offer-1",quantity:"2"})}));
  expect(screen.getByText("Sản phẩm mua ngay")).toBeInTheDocument();
  expect(screen.getByText("Phân loại: Hộp lớn")).toBeInTheDocument();
  expect(screen.getByText("Số lượng: 2")).toBeInTheDocument();
  expect(mocks.GET).toHaveBeenCalledWith("/api/v1/catalog/items/{itemId}",{params:{path:{itemId:"product-1"},query:{locationId:"location-1"}}});
  expect(mocks.GET).not.toHaveBeenCalledWith("/api/v1/me/carts");
});

it.each([
  {type:"PRODUCT",listingKind:"STANDARD",locationId:"different-location",available:true,quantity:"1"},
  {type:"PRODUCT",listingKind:"STANDARD",locationId:"location-1",available:false,quantity:"1"},
  {type:"SERVICE",listingKind:"STANDARD",locationId:"location-1",available:true,quantity:"2"},
  {type:"PRODUCT",listingKind:"RESALE",locationId:"location-1",available:true,quantity:"2"},
])("rejects invalid direct selection %j without falling back to a cart", async input => {
  mocks.GET.mockResolvedValue(result({id:"product-1",name:"Invalid",type:input.type,listingKind:input.listingKind,
    resolvedLocation:{id:input.locationId},variants:[{offer:{locationVariantId:"offer-1",available:input.available}}]}));
  render(await CheckoutPage({searchParams:Promise.resolve({source:"DIRECT",itemId:"product-1",locationId:"location-1",locationVariantId:"offer-1",quantity:input.quantity})}));
  expect(screen.getByRole("alert")).toHaveTextContent("Không thể tiếp tục đặt hàng");
  expect(mocks.GET).toHaveBeenCalledTimes(1);
});

it("resolves a Need item from later response pages using authenticated reads and renders its actual name", async () => {
  mocks.GET.mockImplementation(async (path: string, options?: {params?: {query?: {cursor?: string}}}) => {
    if (path === "/api/v1/me/need-requests/{needRequestId}") return result({id:"need-1",type:"PRODUCT"});
    if (path === "/api/v1/me/need-requests/{needRequestId}/responses") {
      if (!options?.params?.query?.cursor) return result({items:[],hasMore:true,nextCursor:"second-page"});
      return result({items:[{merchantLocationId:"location-1",items:[{id:"chosen-item",catalogItemId:"product-1",catalogItemName:"Món từ phản hồi",locationVariantId:"offer-1",actionable:true}]}],hasMore:false});
    }
    if (path === "/api/v1/catalog/items/{itemId}") return result({type:"PRODUCT",resolvedLocation:{id:"location-1"},variants:[{name:"Phần đặc biệt",offer:{locationVariantId:"offer-1",available:true}}]});
    return result([]);
  });
  render(await CheckoutPage({searchParams:Promise.resolve(params)}));
  expect(screen.getByText("Món từ phản hồi")).toBeInTheDocument();
  expect(screen.getByText("Phân loại: Phần đặc biệt")).toBeInTheDocument();
  expect(mocks.requireUser).toHaveBeenCalledTimes(1);
  expect(mocks.GET).not.toHaveBeenCalledWith("/api/v1/me/carts");
  expect(mocks.GET).toHaveBeenCalledWith("/api/v1/me/need-requests/{needRequestId}/responses",expect.objectContaining({
    params:{path:{needRequestId:"need-1"},query:{limit:100,cursor:"second-page"}},
  }));
});

it("does not substitute a cart when a Need source cannot be read by this Buyer", async () => {
  mocks.GET.mockImplementation(async (path:string)=>result(path === "/api/v1/me/need-requests/{needRequestId}" ? undefined : [],path === "/api/v1/me/need-requests/{needRequestId}" ? 404 : 200));
  render(await CheckoutPage({searchParams:Promise.resolve(params)}));
  expect(screen.getByRole("alert")).toHaveTextContent("Không thể tiếp tục đặt hàng");
  expect(mocks.GET).not.toHaveBeenCalledWith("/api/v1/me/carts");
});

it("rejects a Need offer that is no longer available in the live catalog", async () => {
  mocks.GET.mockImplementation(async(path:string)=> {
    if (path === "/api/v1/me/need-requests/{needRequestId}") return result({type:"PRODUCT"});
    if (path === "/api/v1/me/need-requests/{needRequestId}/responses") return result({items:[{merchantLocationId:"location-1",items:[{
      id:"chosen-item",catalogItemId:"product-1",locationVariantId:"offer-1",actionable:true,
    }]}],hasMore:false});
    return result({type:"PRODUCT",resolvedLocation:{id:"location-1"},variants:[{name:"Không còn bán",offer:{locationVariantId:"offer-1",available:false}}]});
  });
  render(await CheckoutPage({searchParams:Promise.resolve(params)}));
  expect(screen.getByRole("alert")).toHaveTextContent("Không thể tiếp tục đặt hàng");
  expect(mocks.GET).not.toHaveBeenCalledWith("/api/v1/me/addresses");
  expect(mocks.GET).not.toHaveBeenCalledWith("/api/v1/me/carts");
});

it("rejects an explicit malformed source instead of using an unrelated cart", async () => {
  mocks.GET.mockResolvedValue(result([]));
  render(await CheckoutPage({searchParams:Promise.resolve({source:"NEED_REQUEST",needRequestId:"need-1"})}));
  expect(screen.getByRole("alert")).toHaveTextContent("Không thể tiếp tục đặt hàng");
  expect(mocks.GET).not.toHaveBeenCalled();
});
