import { fireEvent, render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import SessionsError from "@/app/(buyer)/me/security/sessions/error";
import SessionsLoading from "@/app/(buyer)/me/security/sessions/loading";

it("offers server retry without showing internal session errors",()=>{
  const retry=vi.fn();
  render(<SessionsError error={new Error("private diagnostic")} retry={retry}/>);
  expect(screen.queryByText("private diagnostic")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button",{name:"Thử lại"}));
  expect(retry).toHaveBeenCalledTimes(1);
  expect(screen.getByRole("link",{name:"Quay lại hồ sơ"})).toHaveAttribute("href","/me");
});
it("announces loading sessions without displaying an empty list",()=>{
  render(<SessionsLoading/>);
  expect(screen.getByRole("status")).toHaveTextContent("Đang tải phiên đăng nhập…");
});
