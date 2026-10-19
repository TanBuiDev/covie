"use client";

import { useEffect } from "react";

const dirtySources = new Set<symbol>();
const warning = "Nội dung Social chưa được lưu. Bạn có muốn rời trang?";

export function confirmSocialDraftDiscard() {
  return dirtySources.size === 0 || window.confirm("Thao tác này sẽ tải lại dữ liệu và có thể bỏ caption/ảnh chưa lưu. Tiếp tục?");
}

function warnUnload(event: BeforeUnloadEvent) {
  event.preventDefault();
  event.returnValue = "";
}

function guardLink(event: MouseEvent) {
  if (event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
  const link = event.target instanceof Element ? event.target.closest("a[href]") : null;
  if (!(link instanceof HTMLAnchorElement) || link.hasAttribute("download")
      || link.target && link.target.toLowerCase() !== "_self") return;
  const destination = new URL(link.href, window.location.href);
  if (destination.origin !== window.location.origin || !["http:", "https:"].includes(destination.protocol)
      || destination.pathname === window.location.pathname && destination.search === window.location.search) return;
  if (!window.confirm(warning)) {
    event.preventDefault();
    event.stopImmediatePropagation();
  }
}

function guardTraversal(rawEvent: Event) {
  const event = rawEvent as Event & { navigationType: string; destination: { sameDocument: boolean; url: string } };
  if (event.defaultPrevented || !event.cancelable || event.navigationType !== "traverse" || !event.destination.sameDocument) return;
  const destination = new URL(event.destination.url);
  if (destination.origin !== window.location.origin
      || destination.pathname === window.location.pathname && destination.search === window.location.search) return;
  if (!window.confirm(warning)) event.preventDefault();
}

export function useSocialDraftNavigation(dirty: boolean) {
  useEffect(() => {
    if (!dirty) return;
    const source = Symbol();
    const navigation = (window as Window & { navigation?: EventTarget }).navigation;
    if (dirtySources.size === 0) {
      window.addEventListener("beforeunload", warnUnload);
      document.addEventListener("click", guardLink, true);
      navigation?.addEventListener("navigate", guardTraversal);
    }
    dirtySources.add(source);
    return () => {
      dirtySources.delete(source);
      if (dirtySources.size === 0) {
        window.removeEventListener("beforeunload", warnUnload);
        document.removeEventListener("click", guardLink, true);
        navigation?.removeEventListener("navigate", guardTraversal);
      }
    };
  }, [dirty]);
}
