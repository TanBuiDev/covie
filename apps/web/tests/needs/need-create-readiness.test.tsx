import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { CovieApiError } from "@/lib/api/api-error";
import { NeedCreateForm } from "@/features/needs/need-create-form";
const mocks=vi.hoisted(()=>({categories:vi.fn(),addresses:vi.fn(),create:vi.fn()}));
vi.mock("@/features/needs/need-api",()=>({listNeedCategories:mocks.categories,listNeedAddresses:mocks.addresses,createNeed:mocks.create}));
vi.mock("next/navigation",()=>({useRouter:()=>({push:vi.fn()})}));
afterEach(()=>{cleanup();vi.resetAllMocks();});

async function readyForm() {
  mocks.categories.mockResolvedValue([{id:"category-1",name:"Rau"}]);
  mocks.addresses.mockResolvedValue([{id:"address-1",addressText:"Nhà"}]);
  mocks.create.mockReturnValue(new Promise(()=>{}));
  render(<NeedCreateForm/>);
  await waitFor(()=>expect(screen.getByRole("button",{name:"Đăng Cần mua"})).toBeEnabled());
  fireEvent.change(screen.getByRole("textbox",{name:/Tên \/ nhu cầu/}),{target:{value:"Tìm rau"}});
  fireEvent.change(screen.getByRole("textbox",{name:/Từ khóa tìm kiếm/}),{target:{value:"rau"}});
  fireEvent.change(screen.getByLabelText(/Thời hạn/),{target:{value:"2099-01-01T12:00"}});
  return screen.getByRole("button",{name:"Đăng Cần mua"}).closest("form")!;
}

it.each(["", "0", "1.5", "9007199254740992"])("rejects invalid product quantity %s before POST",async quantity=>{
  const form=await readyForm();
  fireEvent.change(screen.getByRole("spinbutton",{name:/Số lượng/}),{target:{value:quantity}});
  fireEvent.submit(form);
  expect(mocks.create).not.toHaveBeenCalled();
  expect(await screen.findByRole("alert")).toHaveTextContent("Số lượng");
});

it("rejects a past expiry before POST",async()=>{
  const form=await readyForm();
  fireEvent.change(screen.getByLabelText(/Thời hạn/),{target:{value:"2000-01-01T12:00"}});
  fireEvent.submit(form);
  expect(mocks.create).not.toHaveBeenCalled();
  expect(await screen.findByRole("alert")).toHaveTextContent("Thời hạn");
});

it("sends only one request for two submissions in the same event batch",async()=>{
  const form=await readyForm();
  act(()=>{fireEvent.submit(form);fireEvent.submit(form);});
  expect(mocks.create).toHaveBeenCalledTimes(1);
});

it("releases the submission guard after a failed create so the user can retry",async()=>{
  const form=await readyForm();
  mocks.create.mockRejectedValueOnce(new CovieApiError(503,"ERROR","failed"));
  fireEvent.submit(form);
  await screen.findByRole("alert");
  expect(screen.getByRole("button",{name:"Đăng Cần mua"})).toBeEnabled();
  fireEvent.submit(form);
  expect(mocks.create).toHaveBeenCalledTimes(2);
});

it("keeps backend details out of failed-create feedback",async()=>{
  const form=await readyForm();
  mocks.create.mockRejectedValueOnce(new CovieApiError(503,"ERROR","private backend detail"));
  fireEvent.submit(form);
  expect(await screen.findByRole("alert")).toHaveTextContent("Không thể tạo nhu cầu");
  expect(screen.queryByText("private backend detail")).not.toBeInTheDocument();
});

it("keeps entered text and offers login when create fails401",async()=>{
  const form=await readyForm();
  mocks.create.mockRejectedValueOnce(new CovieApiError(401,"AUTH_REQUIRED","opaque"));
  fireEvent.submit(form);
  expect(await screen.findByRole("link",{name:"Đăng nhập lại"})).toHaveAttribute("href","/login?returnTo=%2Fneeds%2Fnew");
  expect(screen.getByRole("textbox",{name:/Tên \/ nhu cầu/})).toHaveValue("Tìm rau");
});

it("offers retry after a prerequisite read fails and preserves entered text",async()=>{
  mocks.categories.mockRejectedValueOnce(new CovieApiError(503,"ERROR","private"));
  mocks.addresses.mockResolvedValue([{id:"address-1",addressText:"Nhà"}]);
  render(<NeedCreateForm/>);
  fireEvent.change(screen.getByRole("textbox",{name:/Tên \/ nhu cầu/}),{target:{value:"Tìm rau"}});
  const retry=await screen.findByRole("button",{name:"Thử lại"});
  expect(screen.queryByText("private")).not.toBeInTheDocument();
  mocks.categories.mockResolvedValue([{id:"category-1",name:"Rau"}]);
  fireEvent.click(retry);
  await waitFor(()=>expect(screen.getByRole("button",{name:"Đăng Cần mua"})).toBeEnabled());
  expect(screen.getByRole("textbox",{name:/Tên \/ nhu cầu/})).toHaveValue("Tìm rau");
});

it("does not permit stale product selections while service prerequisites are loading",async()=>{
  mocks.categories.mockResolvedValueOnce([{id:"product-category",name:"Rau"}]);
  mocks.addresses.mockResolvedValue([{id:"address-1",addressText:"Nhà"}]);
  render(<NeedCreateForm/>);
  await waitFor(()=>expect(screen.getByRole("button",{name:"Đăng Cần mua"})).toBeEnabled());
  mocks.categories.mockReturnValue(new Promise(()=>{}));
  fireEvent.click(screen.getByRole("button",{name:"Dịch vụ"}));
  expect(screen.getByRole("button",{name:"Đăng Cần mua"})).toBeDisabled();
  fireEvent.submit(screen.getByRole("button",{name:"Đăng Cần mua"}).closest("form")!);
  expect(mocks.create).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button",{name:"Sản phẩm"}));
  expect(screen.getByRole("button",{name:"Đăng Cần mua"})).toBeDisabled();
});

it("offers login to the creation page when prerequisite reads report401",async()=>{
  mocks.categories.mockResolvedValue([]);
  mocks.addresses.mockRejectedValue(new CovieApiError(401,"AUTH_REQUIRED","opaque"));
  render(<NeedCreateForm/>);
  expect(await screen.findByRole("link",{name:"Đăng nhập lại"})).toHaveAttribute("href","/login?returnTo=%2Fneeds%2Fnew");
  expect(screen.getByRole("button",{name:"Đăng Cần mua"})).toBeDisabled();
});

it("prefills title and queryText from URL search parameter q", async () => {
  const originalLocation = window.location;
  Object.defineProperty(window, "location", {
    writable: true,
    value: { ...originalLocation, search: "?q=B%C3%BAn%20b%C3%B2%20Hu%E1%BA%BF" },
  });
  mocks.categories.mockResolvedValue([{ id: "category-1", name: "Đồ ăn" }]);
  mocks.addresses.mockResolvedValue([{ id: "address-1", addressText: "Toà S5" }]);

  render(<NeedCreateForm />);
  await waitFor(() => expect(screen.getByRole("button", { name: "Đăng Cần mua" })).toBeEnabled());

  expect(screen.getByRole("textbox", { name: /Tên \/ nhu cầu/ })).toHaveValue("Bún bò Huế");
  expect(screen.getByRole("textbox", { name: /Từ khóa tìm kiếm/ })).toHaveValue("Bún bò Huế");

  Object.defineProperty(window, "location", {
    writable: true,
    value: originalLocation,
  });
});
