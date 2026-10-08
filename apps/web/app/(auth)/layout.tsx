import type { ReactNode } from "react";
import { GoogleAuthProvider } from "@/features/auth/google-auth-provider";

export default function AuthLayout({children}:{children:ReactNode}) {
  return <GoogleAuthProvider>{children}</GoogleAuthProvider>;
}
