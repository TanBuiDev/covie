export function GoogleLoginButton({ intent = "LOGIN" }: { intent?: "LOGIN" | "PASSWORD_RESET" }) {
  const href = `/api/v1/auth/google/start?intent=${encodeURIComponent(intent)}`;
  return <a href={href}>Tiếp tục với Google</a>;
}
