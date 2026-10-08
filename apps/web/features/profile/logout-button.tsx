"use client";

import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { logout } from "@/features/auth/auth-api";

export function LogoutButton() {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState<string>();
  const logoutPending = useRef(false);

  return (
    <div>
      <button
        type="button"
        disabled={pending}
        onClick={() => {
          if (logoutPending.current) return;
          logoutPending.current = true;
          setPending(true);
          setMessage(undefined);
          void logout((path) => router.replace(path)).catch(() => {
            logoutPending.current = false;
            setPending(false);
            setMessage("Không thể đăng xuất. Vui lòng thử lại.");
          });
        }}
      >
        {pending ? "Đang đăng xuất…" : "Đăng xuất"}
      </button>
      {message ? <p role="alert">{message}</p> : null}
    </div>
  );
}
