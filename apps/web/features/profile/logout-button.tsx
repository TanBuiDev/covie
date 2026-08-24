"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { logout } from "@/features/auth/auth-api";

export function LogoutButton() {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState<string>();

  return (
    <div>
      <button
        type="button"
        disabled={pending}
        onClick={() => {
          if (pending) return;
          setPending(true);
          setMessage(undefined);
          void logout((path) => router.replace(path)).catch(() => {
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
