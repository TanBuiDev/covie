"use client";

import Link from "next/link";
import { useState } from "react";
import {
  addSocialComment, fetchSocialComments, isSignInRequired, removeSocialComment,
  type SocialComment,
} from "./social-api";
import { useSocialFeed } from "./social-feed";
import styles from "./marketplace-view.module.css";

function errorLabel(error: unknown): string {
  return isSignInRequired(error) ? "Đăng nhập để gửi bình luận." : "Không thể thực hiện. Vui lòng thử lại.";
}

export function SocialComments({ itemId, onClose }: { itemId: string; onClose: () => void }) {
  const [comments, setComments] = useState<SocialComment[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [text, setText] = useState("");
  const [message, setMessage] = useState("");
  const [requiresLogin, setRequiresLogin] = useState(false);
  const { summaries, updateSummary } = useSocialFeed();

  async function load(before?: string) {
    if (loading) return;
    setLoading(true);
    setMessage("");
    try {
      const page = await fetchSocialComments(itemId, before);
      setComments((old) => before
        ? [...old, ...page.items.filter((c) => !old.some((o) => o.id === c.id))]
        : page.items);
      setCursor(page.nextCursor);
      setLoaded(true);
    } catch {
      setMessage("Không tải được bình luận. Hãy thử lại.");
    } finally {
      setLoading(false);
    }
  }

  // Mounts only after the user opens the comment drawer (lazy, no feed N+1).

  async function submit() {
    const body = text.trim();
    if (body.length < 2 || body.length > 1000 || busy) return;
    setBusy(true);
    setMessage("");
    setRequiresLogin(false);
    try {
      const comment = await addSocialComment(itemId, body);
      setComments((old) => [comment, ...old]);
      setLoaded(true);
      const summary = summaries[itemId];
      if (summary) updateSummary({ ...summary, comments: summary.comments + 1 });
      setText("");
    } catch (error) {
      setMessage(errorLabel(error));
      setRequiresLogin(isSignInRequired(error));
    } finally { setBusy(false); }
  }

  async function remove(commentId: string) {
    if (busy) return;
    setBusy(true);
    setMessage("");
    try {
      await removeSocialComment(itemId, commentId);
      setComments((old) => old.filter((c) => c.id !== commentId));
      const summary = summaries[itemId];
      if (summary) updateSummary({ ...summary, comments: Math.max(0, summary.comments - 1) });
    } catch (error) {
      setMessage(errorLabel(error));
      setRequiresLogin(isSignInRequired(error));
    } finally { setBusy(false); }
  }

  return (
    <section className={styles.commentPanel} aria-label="Bình luận sản phẩm">
      <div className={styles.commentHeader}>
        <strong>Bình luận</strong>
        <button type="button" onClick={onClose}>Đóng</button>
      </div>
      {!loaded && <button type="button" disabled={loading} onClick={() => void load()}>
        {loading ? "Đang tải…" : "Xem bình luận"}
      </button>}
      {loaded && comments.length === 0 && <p className={styles.commentHint}>Chưa có bình luận nào.</p>}
      {loaded && (
        <div className={styles.commentList}>
          {comments.map((c) => (
            <div key={c.id} className={styles.comment}>
              <div className={styles.commentMeta}>
                <strong>{c.authorName}</strong>
                <time dateTime={c.createdAt}>{new Date(c.createdAt).toLocaleDateString("vi-VN")}</time>
              </div>
              <p>{c.body}</p>
              {c.own && <button type="button" disabled={busy}
                onClick={() => void remove(c.id)} aria-label="Xóa bình luận của bạn">Xóa</button>}
            </div>
          ))}
          {cursor && <button type="button" disabled={loading} onClick={() => void load(cursor)}>
            {loading ? "Đang tải…" : "Xem thêm bình luận"}
          </button>}
        </div>
      )}
      <form className={styles.commentForm} onSubmit={(event) => { event.preventDefault(); void submit(); }}>
        <label htmlFor={"social-comment-" + itemId}>Viết bình luận</label>
        <textarea id={"social-comment-" + itemId} value={text} maxLength={1000} rows={2}
          onChange={(e) => setText(e.target.value)} placeholder="Chia sẻ câu hỏi hoặc nhận xét…" />
        <button type="submit" disabled={busy || text.trim().length < 2}>Gửi bình luận</button>
      </form>
      {message && <p role="alert" className={styles.commentError}>{message}
        {requiresLogin && <> <Link href="/login">Đến trang đăng nhập</Link></>}
      </p>}
    </section>
  );
}
