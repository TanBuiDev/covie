import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { useSocialDraftNavigation } from "./use-social-draft-navigation";
import Link from "next/link";

function Source({ dirty }: { dirty: boolean }) {
  useSocialDraftNavigation(dirty);
  return null;
}

afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it("cancels supported same-document traversal but never cancels Next push/replace commits", () => {
  const navigation = new EventTarget();
  vi.stubGlobal("navigation", navigation);
  const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
  const view = render(<Source dirty />);
  const event = (type: string, cancelable = true, sameDocument = true) => Object.assign(new Event("navigate", { cancelable }), {
    navigationType: type, destination: { sameDocument, url: `${window.location.origin}/seller/other` },
  });
  expect(navigation.dispatchEvent(event("traverse"))).toBe(false);
  expect(confirm).toHaveBeenCalledTimes(1);
  expect(navigation.dispatchEvent(event("push"))).toBe(true);
  expect(navigation.dispatchEvent(event("replace"))).toBe(true);
  expect(navigation.dispatchEvent(event("traverse", false))).toBe(true);
  expect(navigation.dispatchEvent(event("traverse", true, false))).toBe(true);
  expect(confirm).toHaveBeenCalledTimes(1);
  confirm.mockReturnValue(true);
  expect(navigation.dispatchEvent(event("traverse"))).toBe(true);
  expect(confirm).toHaveBeenCalledTimes(2);
  view.unmount();
  navigation.dispatchEvent(event("traverse"));
  expect(confirm).toHaveBeenCalledTimes(2);
});

it("coordinates multiple dirty sources with one prompt and removes listeners after the last source clears", () => {
  const confirm = vi.spyOn(window, "confirm").mockReturnValue(true);
  const navigate = vi.fn();
  const link = <Link href="/seller/another" onClick={event => { event.preventDefault(); navigate(); }}>Đi tiếp</Link>;
  const view = render(<><Source dirty /><Source dirty />{link}</>);
  fireEvent.click(screen.getByRole("link"));
  expect(confirm).toHaveBeenCalledTimes(1);
  expect(navigate).toHaveBeenCalledTimes(1);
  view.rerender(<><Source dirty={false} /><Source dirty />{link}</>);
  expect(window.dispatchEvent(new Event("beforeunload", { cancelable: true }))).toBe(false);
  confirm.mockReturnValue(false);
  fireEvent.click(screen.getByRole("link"));
  expect(navigate).toHaveBeenCalledTimes(1);
  view.rerender(<><Source dirty={false} /><Source dirty={false} />{link}</>);
  fireEvent.click(screen.getByRole("link"));
  expect(confirm).toHaveBeenCalledTimes(2);
  expect(navigate).toHaveBeenCalledTimes(2);
  expect(window.dispatchEvent(new Event("beforeunload", { cancelable: true }))).toBe(true);
});

it("does not prompt for same-document anchors, downloads or new-tab gestures and cleans up on unmount", () => {
  const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
  const stop = (event: React.MouseEvent) => event.preventDefault();
  const view = render(<><Source dirty />
    <Link href={`${window.location.pathname}${window.location.search}#preview`} onClick={stop}>Vị trí</Link>
    <Link href="/file" download onClick={stop}>Tải xuống</Link>
    <Link href="/another" target="_blank" onClick={stop}>Tab mới</Link>
    <Link href="/another" onClick={stop}>Nội bộ</Link>
  </>);
  fireEvent.click(screen.getByText("Vị trí"));
  fireEvent.click(screen.getByText("Tải xuống"));
  fireEvent.click(screen.getByText("Tab mới"));
  fireEvent.click(screen.getByText("Nội bộ"), { ctrlKey: true });
  expect(confirm).not.toHaveBeenCalled();
  view.unmount();
  expect(window.dispatchEvent(new Event("beforeunload", { cancelable: true }))).toBe(true);
});
