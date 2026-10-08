"use client";
import Image from "next/image";
import Link from "next/link";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { uploadImage } from "@/features/media/direct-image-upload";
import { CovieApiError } from "@/lib/api/api-error";
import { updateProfile, type Profile, type ProfileUpdate } from "./profile-client-api";
import styles from "./profile-edit-form.module.css";
export function ProfileEditForm({ user, onSaved, onCancel }: { user: Profile; onSaved: (user: Profile) => void; onCancel: () => void }) {
  const [name, setName] = useState(user.displayName ?? "");
  const [phone, setPhone] = useState(user.phone ?? "");
  const [avatarId, setAvatarId] = useState<string | null>(user.avatarMediaId ?? null);
  const [avatarUrl, setAvatarUrl] = useState(user.avatarUrl ?? "");
  const [avatarBroken, setAvatarBroken] = useState(false);
  const [pending, setPending] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState("");
  const [expired, setExpired] = useState(false);
  const nameInput = useRef<HTMLInputElement>(null);
  const saveLock = useRef(false);
  const uploadLock = useRef(false);
  const active = useRef(true);
  const busy = pending || uploading;
  useEffect(() => { active.current = true; nameInput.current?.focus(); return () => { active.current = false; }; }, []);
  useEffect(() => () => { if (avatarUrl.startsWith("blob:")) URL.revokeObjectURL(avatarUrl); }, [avatarUrl]);
  function failure(cause: unknown, fallback: string) {
    if (!active.current) return;
    if (cause instanceof CovieApiError && cause.status === 401) setExpired(true);
    else if (cause instanceof CovieApiError && cause.status === 403) setError("Bạn chưa thể cập nhật hồ sơ trong phiên này. Vui lòng đăng nhập lại.");
    else setError(fallback);
  }
  async function upload(file?: File) {
    if (!file || uploadLock.current || saveLock.current) return;
    if (!["image/png", "image/jpeg", "image/webp"].includes(file.type) || file.size <= 0 || file.size > 10 * 1024 * 1024) { setError("Chọn ảnh PNG, JPG hoặc WebP, dung lượng tối đa 10 MB."); return; }
    uploadLock.current = true; setUploading(true); setError("");
    try {
      const media = await uploadImage(file, "USER_AVATAR");
      if (!media.mediaId) throw new Error("UNVERIFIED_AVATAR");
      if (active.current) { setAvatarId(media.mediaId); setAvatarUrl(URL.createObjectURL(file)); setAvatarBroken(false); }
    } catch (cause) { failure(cause, "Chưa thể tải ảnh đại diện. Ảnh đã lưu được giữ nguyên; bạn có thể chọn lại tệp để thử lại."); }
    finally { uploadLock.current = false; if (active.current) setUploading(false); }
  }
  async function save(event: FormEvent) {
    event.preventDefault();
    if (saveLock.current || uploadLock.current || expired) return;
    const displayName = name.trim(), normalizedPhone = phone.trim();
    if (!displayName || displayName.length > 120) { setError("Nhập tên hiển thị từ 1 đến 120 ký tự."); nameInput.current?.focus(); return; }
    const digits = normalizedPhone.replace(/\D/g, "").length;
    if (normalizedPhone && (!/^\+?[\d\s()-]+$/.test(normalizedPhone) || digits < 7 || digits > 15 || normalizedPhone.length > 30)) { setError("Nhập số điện thoại hợp lệ với 7 đến 15 chữ số."); return; }
    saveLock.current = true; setPending(true); setError("");
    const input: ProfileUpdate = { displayName, phone: normalizedPhone || null, avatarMediaId: avatarId };
    try { const saved = await updateProfile(input); if (active.current) onSaved(saved); }
    catch (cause) { failure(cause, "Chưa thể lưu hồ sơ. Thông tin bạn nhập được giữ lại để thử lại."); }
    finally { saveLock.current = false; if (active.current) setPending(false); }
  }
  if (expired) return <section className={styles.form}><h2>Chỉnh sửa hồ sơ</h2><p role="alert">Phiên đăng nhập đã hết hạn.</p><Link href="/login?returnTo=%2Fme">Đăng nhập lại</Link></section>;
  return <form className={styles.form} aria-label="Chỉnh sửa hồ sơ" aria-busy={busy} onSubmit={save}>
    <h2>Chỉnh sửa hồ sơ</h2>
    <p className={styles.description}>Cập nhật tên và ảnh hiển thị của bạn. Số điện thoại được lưu trong hồ sơ riêng.</p>
    {error && <p role="alert" className={styles.error}>{error}</p>}
    <div className={styles.preview}>{avatarUrl && !avatarBroken ? <Image src={avatarUrl} alt="Xem trước ảnh đại diện" width={80} height={80} unoptimized onError={() => setAvatarBroken(true)} /> : <span aria-label="Chưa có ảnh đại diện">{name.trim().charAt(0).toUpperCase() || "C"}</span>}</div>
    <fieldset disabled={busy}><legend className="sr-only">Thông tin cá nhân</legend>
      <label>Ảnh đại diện<input type="file" aria-describedby="avatar-upload-hint" accept="image/png,image/jpeg,image/webp" onChange={e => { void upload(e.target.files?.[0]); e.target.value = ""; }} /></label><small id="avatar-upload-hint">PNG, JPG hoặc WebP · tối đa 10 MB</small>
      {avatarId && <button type="button" className="button-link" onClick={() => { setAvatarId(null); setAvatarUrl(""); setAvatarBroken(false); }}>Xóa ảnh đại diện</button>}
      <label>Tên hiển thị<input ref={nameInput} autoComplete="name" required maxLength={120} value={name} onChange={e => setName(e.target.value)} /></label>
      <label>Số điện thoại<input type="tel" aria-describedby="profile-phone-hint" autoComplete="tel" maxLength={30} value={phone} onChange={e => setPhone(e.target.value)} /></label><small id="profile-phone-hint">Để trống nếu bạn muốn xóa số điện thoại đã lưu.</small>
      <p className={styles.description}>Email đăng nhập: {user.email ?? "Chưa có email"}</p>
    </fieldset>
    {uploading && <p role="status">Đang tải và xác minh ảnh đại diện…</p>}
    <div className={styles.actions}><button className="button-primary" disabled={busy} type="submit">{pending ? "Đang lưu…" : "Lưu hồ sơ"}</button><button className="button-secondary" type="button" disabled={busy} onClick={onCancel}>Hủy</button></div>
  </form>;
}
