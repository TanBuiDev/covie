"use client";

import type { components } from "@covie/api-client";
import Image from "next/image";
import { useEffect, useRef, useState } from "react";
import { ArrowDown, ArrowUp, Star, Trash, VideoCamera } from "@phosphor-icons/react";
import { uploadImage, validateImageFile } from "@/features/media/direct-image-upload";
import styles from "./catalog-media-editor.module.css";
import { useSocialDraftNavigation } from "./use-social-draft-navigation";
import { createCatalogVideoUpload, validateCatalogVideoFile } from "@/features/media/direct-catalog-video-upload";

type Media = NonNullable<components["schemas"]["CatalogItemManagementDetailResponse"]["media"]>;
type Entry = { key: string; mediaId?: string; file?: File; url?: string; name: string; status?: string; contentType?: string };

export function useCatalogGallery(initial: Media = [], onChange?: () => void) {
  const videoEnabled = process.env.NEXT_PUBLIC_COVIE_SOCIAL_V2_VIDEO_ENABLED === "true";
  const [entries, setEntries] = useState<Entry[]>(() => initial.flatMap((media, index) => media.id
    ? [{ key: media.id, mediaId: media.id, url: media.url, name: `${media.contentType?.startsWith("video/") ? "Video" : "Ảnh"} ${index + 1}`, status: media.status, contentType: media.contentType }] : []));
  const [error, setError] = useState<string>();
  const uploads = useRef(new Map<string, string>());
  const videoUploads = useRef(new Map<string, ReturnType<typeof createCatalogVideoUpload>>());
  const previews = useRef(new Set<string>());
  useEffect(() => {
    const urls = previews.current;
    return () => { urls.forEach(url => URL.revokeObjectURL(url)); };
  }, []);
  const originalIds = initial.flatMap(media => media.id ? [media.id] : []);
  const changed = entries.map(entry => entry.key).join("\u0000") !== originalIds.join("\u0000");
  useSocialDraftNavigation(changed);

  function add(files: File[]) {
    if (entries.length + files.length > 10) { setError(`Tối đa 10 ${videoEnabled ? "media" : "ảnh"}. Gallery hiện có được giữ nguyên.`); return; }
    if (videoEnabled && entries.filter(entry => entry.contentType?.startsWith("video/")).length + files.filter(file => file.type.startsWith("video/")).length > 1) {
      setError("Tối đa 1 video. Gallery hiện có được giữ nguyên."); return;
    }
    try { files.forEach(file => videoEnabled && file.type.startsWith("video/") ? validateCatalogVideoFile(file) : validateImageFile(file)); }
    catch { setError(videoEnabled ? "Ảnh JPEG/PNG/WebP tối đa 10 MiB; video MP4/WebM tối đa 50 MiB, không được rỗng." : "Ảnh phải là JPEG, PNG hoặc WebP, tối đa 10 MiB mỗi ảnh."); return; }
    const added = files.map(file => {
      const url = URL.createObjectURL(file); previews.current.add(url);
      return { key: `upload:${crypto.randomUUID()}`, file, url, name: file.name, status: "Chưa tải", contentType: file.type };
    });
    setEntries(previous => [...previous, ...added]); setError(undefined); onChange?.();
  }
  function remove(index: number) {
    const entry = entries[index]; if (!entry) return;
    if (entry.file && entry.url) { URL.revokeObjectURL(entry.url); previews.current.delete(entry.url); }
    uploads.current.delete(entry.key);
    videoUploads.current.delete(entry.key);
    setEntries(previous => previous.filter(candidate => candidate.key !== entry.key)); setError(undefined); onChange?.();
  }
  function move(from: number, to: number) {
    if (from === to || from < 0 || to < 0 || from >= entries.length || to >= entries.length) return;
    setEntries(previous => {
      const next = [...previous]; const [entry] = next.splice(from, 1);
      if (entry) next.splice(to, 0, entry);
      return next;
    });
    onChange?.();
  }
  async function resolveMediaIds(progress?: (message: string) => void) {
    if (changed && entries.length > 10) throw new Error(`Tối đa 10 ${videoEnabled ? "media" : "ảnh"} khi thay đổi gallery.`);
    const ids: string[] = [];
    for (const [index, entry] of entries.entries()) {
      const existing = entry.mediaId ?? uploads.current.get(entry.key);
      if (existing) { ids.push(existing); continue; }
      if (!entry.file) throw new Error("Ảnh không có dữ liệu hợp lệ.");
      const video = entry.contentType?.startsWith("video/") === true;
      progress?.(`Đang tải ${video ? "video" : "ảnh"} ${index + 1}/${entries.length}…`);
      try {
        const updateProgress = (message: string) => {
          setEntries(previous => previous.map(candidate => candidate.key === entry.key ? { ...candidate, status: message } : candidate));
          progress?.(message);
        };
        if (video && !videoEnabled) throw new Error("MEDIA_TYPE_NOT_SUPPORTED");
        if (video && !videoUploads.current.has(entry.key)) videoUploads.current.set(entry.key, createCatalogVideoUpload(entry.file));
        const uploaded = video ? await videoUploads.current.get(entry.key)!(updateProgress) : await uploadImage(entry.file, "CATALOG_IMAGE");
        if (!uploaded.mediaId) throw new Error("MEDIA_UPLOAD_CONTRACT_INVALID");
        uploads.current.set(entry.key, uploaded.mediaId); ids.push(uploaded.mediaId);
        setEntries(previous => previous.map(candidate => candidate.key === entry.key ? { ...candidate, status: "Đã tải" } : candidate));
      } catch {
        setEntries(previous => previous.map(candidate => candidate.key === entry.key ? { ...candidate, status: `Lỗi tải ${video ? "video" : "ảnh"}; lưu lại để thử lại` } : candidate));
        throw new Error(video ? "Không thể tải hoặc xác minh video; lựa chọn được giữ lại. Video tối đa 60 giây, MP4 H.264/AAC hoặc WebM VP8/VP9. Có thể lưu lại để thử lại; nếu tệp bị từ chối, xóa và chọn tệp hợp lệ." : "Không thể tải ảnh; dữ liệu đã chọn được giữ lại để thử lại.");
      }
    }
    return ids;
  }
  return { entries, changed, error, add, remove, move, resolveMediaIds, videoEnabled };
}

export function CatalogMediaEditor({ gallery, disabled = false }: {
  gallery: ReturnType<typeof useCatalogGallery>; disabled?: boolean;
}) {
  const dragged = useRef<number | undefined>(undefined);
  const cover = gallery.entries.findIndex(entry => !entry.contentType?.startsWith("video/"));
  return <div className={styles.editor}>
    <p className="seller-contract-note">Ảnh đầu tiên là ảnh bìa. Kéo thả hoặc dùng các nút đổi thứ tự, rồi lưu thông tin.</p>
    <label><span className="seller-field-title">{gallery.videoEnabled ? "Ảnh và video Social" : "Ảnh Social"}</span><input type="file" multiple accept={`image/jpeg,image/png,image/webp${gallery.videoEnabled ? ",video/mp4,video/webm" : ""}`}
      disabled={disabled} onChange={event => { gallery.add(Array.from(event.target.files ?? [])); event.target.value = ""; }} /></label>
    <p role="status">{gallery.entries.length} / 10 {gallery.videoEnabled ? "media · tối đa 1 video" : "ảnh"}</p>
    {gallery.changed && <p className="seller-contract-note">Gallery chưa lưu.</p>}
    {gallery.error && <p role="alert" className="warning-banner">{gallery.error}</p>}
    <ol className={styles.grid}>{gallery.entries.map((entry, index) => <li className={styles.entry} key={entry.key}
      draggable={!disabled} onDragStart={event => { dragged.current = index; event.dataTransfer.setData("text/plain", entry.key); }} onDragEnd={() => { dragged.current = undefined; }}
      onDragOver={event => { if (!disabled) event.preventDefault(); }} onDrop={event => {
        event.preventDefault(); if (!disabled && dragged.current !== undefined) gallery.move(dragged.current, index); dragged.current = undefined;
      }}>
      {entry.contentType?.startsWith("video/") ? <VideoCamera size={40} aria-label="Video; không phải ảnh bìa" /> : entry.url && <Image src={entry.url} alt={entry.name} width={120} height={90} unoptimized />}
      <strong>{entry.name}{index === cover ? " · Ảnh bìa" : ""}</strong><small>{entry.status}</small>
      <div className={styles.actions}>
        <button type="button" className="seller-button seller-button--secondary" aria-label={`Đưa lên ${entry.name}`} disabled={disabled || index === 0} onClick={() => gallery.move(index, index - 1)}><ArrowUp aria-hidden="true" /></button>
        <button type="button" className="seller-button seller-button--secondary" aria-label={`Đưa xuống ${entry.name}`} disabled={disabled || index === gallery.entries.length - 1} onClick={() => gallery.move(index, index + 1)}><ArrowDown aria-hidden="true" /></button>
        <button type="button" className="seller-button seller-button--secondary" aria-label={`Làm bìa ${entry.name}`} disabled={disabled || index === cover || entry.contentType?.startsWith("video/")} onClick={() => gallery.move(index, 0)}><Star aria-hidden="true" /></button>
        <button type="button" className="seller-button seller-button--secondary" aria-label={`Xóa ${entry.name}`} disabled={disabled} onClick={() => gallery.remove(index)}><Trash aria-hidden="true" /></button>
      </div>
    </li>)}</ol>
  </div>;
}
