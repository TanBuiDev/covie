"use client";

import Image from "next/image";
import { useId, type ReactNode } from "react";
import { useGoogleAuthStatus } from "./google-auth-provider";

type Intent = "LOGIN" | "PASSWORD_RESET";

export function GoogleAuthLink({intent="LOGIN",children,className}:{intent?:Intent;children:ReactNode;className?:string}) {
  const {state} = useGoogleAuthStatus();
  return state === "configured" ? <a className={className} href={`/api/v1/auth/google/start?intent=${intent}`}>{children}</a>
    : <span className={className} aria-disabled="true">{children}</span>;
}

export function GoogleLoginButton({ intent = "LOGIN" }: { intent?: "LOGIN" | "PASSWORD_RESET" }) {
  const {state,retry} = useGoogleAuthStatus();
  const descriptionId = useId();
  const href = `/api/v1/auth/google/start?intent=${encodeURIComponent(intent)}`;
  const content = <><Image src="/figma/icons/google.svg" width={20} height={20} alt="" aria-hidden="true" unoptimized /><span>Tiếp tục với Google</span></>;
  if (state !== "configured") return <>
    <button className="button-secondary auth-google" type="button" disabled aria-busy={state === "loading"} aria-describedby={descriptionId}>{content}</button>
    <p id={descriptionId} className="auth-card__hint" role="status">{state === "loading" ? "Đang kiểm tra đăng nhập Google…" : state === "error" ? "Chưa kiểm tra được đăng nhập Google. Vui lòng thử lại." : "Google hiện chưa được cấu hình. Nếu đã có tài khoản, bạn có thể đăng nhập bằng email và mật khẩu."}</p>
    {state === "error" ? <button className="button-secondary" type="button" onClick={retry}>Thử lại Google</button> : null}
  </>;
  return (
    <a className="button-secondary auth-google" href={href}>
      {content}
    </a>
  );
}
