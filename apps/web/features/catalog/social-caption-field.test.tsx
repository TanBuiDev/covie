import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { SocialCaptionField } from "./social-caption-field";
import Link from "next/link";

afterEach(() => { cleanup(); vi.restoreAllMocks(); });

it("cancels internal link navigation without losing dirty caption and allows confirmed navigation once", () => {
  const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
  const navigate = vi.fn();
  render(<><SocialCaptionField initialValue="Đã lưu" /><Link href="/seller/catalog" onClick={event => { event.preventDefault(); navigate(); }}>Danh mục</Link></>);
  fireEvent.change(screen.getByRole("textbox", { name: "Caption" }), { target: { value: "Chưa lưu" } });
  fireEvent.click(screen.getByRole("link", { name: "Danh mục" }));
  expect(confirm).toHaveBeenCalledTimes(1);
  expect(navigate).not.toHaveBeenCalled();
  expect(screen.getByRole("textbox", { name: "Caption" })).toHaveValue("Chưa lưu");
  confirm.mockReturnValue(true);
  fireEvent.click(screen.getByRole("link", { name: "Danh mục" }));
  expect(navigate).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole("button", { name: "Hoàn nguyên caption" }));
  fireEvent.click(screen.getByRole("link", { name: "Danh mục" }));
  expect(confirm).toHaveBeenCalledTimes(2);
  expect(navigate).toHaveBeenCalledTimes(2);
});

it("counts UTF-16 units, warns on hard navigation only while dirty and preserves the saved caption for undo", () => {
  const changed = vi.fn();
  const view = render(<SocialCaptionField initialValue="Đã lưu" onChange={changed} />);
  const caption = screen.getByRole("textbox", { name: "Caption" });
  expect(caption).toHaveAttribute("maxlength", "3000");
  expect(window.dispatchEvent(new Event("beforeunload", { cancelable: true }))).toBe(true);
  fireEvent.change(caption, { target: { value: "😀\nabc" } });
  expect(screen.getByLabelText("Số ký tự caption")).toHaveTextContent("6 / 3000");
  expect(screen.getByText("Caption chưa lưu.")).toHaveAttribute("role", "status");
  expect(window.dispatchEvent(new Event("beforeunload", { cancelable: true }))).toBe(false);
  fireEvent.click(screen.getByRole("button", { name: "Hoàn nguyên caption" }));
  expect(caption).toHaveValue("Đã lưu");
  expect(window.dispatchEvent(new Event("beforeunload", { cancelable: true }))).toBe(true);
  expect(changed).toHaveBeenCalledTimes(2);
  view.unmount();
  expect(window.dispatchEvent(new Event("beforeunload", { cancelable: true }))).toBe(true);
});
