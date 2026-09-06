import Image from "next/image";

export function GoogleLoginButton({ intent = "LOGIN" }: { intent?: "LOGIN" | "PASSWORD_RESET" }) {
  const href = `/api/v1/auth/google/start?intent=${encodeURIComponent(intent)}`;
  return (
    <a className="button-secondary auth-google" href={href}>
      <Image src="/figma/icons/google.svg" width={20} height={20} alt="" aria-hidden="true" unoptimized />
      <span>Tiếp tục với Google</span>
    </a>
  );
}
