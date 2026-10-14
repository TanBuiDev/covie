"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import {
  addSocialComment, fetchSocialComments, isSignInRequired, removeSocialComment, reportSocialComment,
  socialErrorMessage, type SocialComment,
} from "./social-api";
import { useSocialFeed } from "./social-feed";
import styles from "./marketplace-view.module.css";

export function SocialComments({ itemId, onClose }: { itemId: string; onClose: () => void }) {
  const [comments, setComments] = useState<SocialComment[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [loadingMore, setLoadingMore] = useState(false);
  const [refresh, setRefresh] = useState(0);
  const [busy, setBusy] = useState(false);
  const [text, setText] = useState("");
  const [message, setMessage] = useState("");
  const [requiresLogin, setRequiresLogin] = useState(false);
  const closeRef = useRef<HTMLButtonElement>(null);
  const requestInFlight = useRef(false);
  const { summaries, updateSummary } = useSocialFeed();

  // Mount only after the comment button is clicked. The first 5 comments are
  // fetched automatically; none are fetched for cards the user never opens.
  useEffect(() => {
    let live = true;
    void fetchSocialComments(itemId)
      .then((page) => {
        if (!live) return;
        setComments((existing) => [
          ...existing.filter((local) => !page.items.some((server) => server.id === local.id)),
          ...page.items,
        ]);
        setCursor(page.nextCursor);
        setLoaded(true);
        setLoadError("");
      })
      .catch(() => { if (live) { setLoaded(true); setLoadError("Không tải được bình luận."); } });
    return () => { live = false; };
  }, [itemId, refresh]);

  useEffect(() => {
    closeRef.current?.focus();
  }, []);

  async function loadMore() {
    if (!cursor || requestInFlight.current) return;
    requestInFlight.current = true;
    setLoadingMore(true);
    setLoadError("");
    try {
      const page = await fetchSocialComments(itemId, cursor);
      setComments((existing) => [
        ...existing,
        ...page.items.filter((candidate) => !existing.some((entry) => entry.id === candidate.id)),
      ]);
      setCursor(page.nextCursor);
    } catch {
      setLoadError("Không tải thêm được bình luận. Hãy thử lại.");
    } finally {
      requestInFlight.current = false;
      setLoadingMore(false);
    }
  }

  async function submit() {
    const body = text.trim();
    if (body.length < 2 || body.length > 1000 || busy) return;
    setBusy(true);
    setMessage("");
    setRequiresLogin(false);
    try {
      const comment = await addSocialComment(itemId, body);
      setComments((existing) => [comment, ...existing.filter((entry) => entry.id !== comment.id)]);
      const summary = summaries[itemId];
      if (summary) updateSummary({ ...summary, comments: summary.comments + 1 });
      setText("");
      setLoaded(true);
      setMessage("Đã đăng bình luận.");
    } catch (error) {
      setMessage(socialErrorMessage(error));
      setRequiresLogin(isSignInRequired(error));
    } finally { setBusy(false); }
  }

  async function report(commentId: string) {
    if (busy) return;
    setBusy(true);
    setMessage("");
    setRequiresLogin(false);
    try {
      await reportSocialComment(commentId);
      setMessage("Đã gửi báo cáo bình luận tới đội ngũ kiểm duyệt.");
    } catch (error) {
      setMessage(socialErrorMessage(error));
      setRequiresLogin(isSignInRequired(error));
    } finally { setBusy(false); }
  }

  async function remove(commentId: string) {
    if (busy) return;
    setBusy(true);
    setMessage("");
    try {
      await removeSocialComment(itemId, commentId);
      setComments((existing) => existing.filter((entry) => entry.id !== commentId));
      const summary = summaries[itemId];
      if (summary) updateSummary({ ...summary, comments: Math.max(0, summary.comments - 1) });
      setMessage("Đã xóa bình luận.");
    } catch (error) {
      setMessage(socialErrorMessage(error));
      setRequiresLogin(isSignInRequired(error));
    } finally { setBusy(false); }
  }

  return (
    <section className={styles.commentPanel} aria-label="Bình luận sản phẩm"
      onKeyDown={(event) => { if (event.key === "Escape") onClose(); }}>
      <div className={styles.commentHeader}>
        <strong>Bình luận</strong>
        <button ref={closeRef} type="button" onClick={onClose}>Đóng</button>
      </div>
      {!loaded && <p className={styles.commentHint} role="status">Đang tải bình luận…</p>}
      {loaded && loadError && <p className={styles.commentError} role="alert">{loadError}{" "}
        {!cursor && <button type="button" onClick={() => { setLoaded(false); setRefresh((n) => n + 1); }}>
          Tải lại
        </button>}
      </p>}
      {loaded && comments.length === 0 && !loadError && <p className={styles.commentHint}>Chưa có bình luận nào.</p>}
      {loaded && comments.length > 0 && (
        <div className={styles.commentList}>
          {comments.map((comment) => (
            <div key={comment.id} className={styles.comment}>
              <div className={styles.commentMeta}>
                <strong>{comment.authorName}</strong>
                <time dateTime={comment.createdAt}>{new Date(comment.createdAt).toLocaleDateString("vi-VN")}</time>
              </div>
              <p>{comment.body}</p>
              {comment.own
                ? <button type="button" disabled={busy} onClick={() => void remove(comment.id)}
                    aria-label="Xóa bình luận của bạn">Xóa</button>
                : <button type="button" disabled={busy} onClick={() => void report(comment.id)}
                    aria-label="Báo cáo bình luận">Báo cáo</button>}
            </div>
          ))}
          {cursor && <button type="button" disabled={loadingMore} onClick={() => void loadMore()}>
            {loadingMore ? "Đang tải…" : "Xem thêm bình luận"}
          </button>}
        </div>
      )}
      <form className={styles.commentForm} onSubmit={(event) => { event.preventDefault(); void submit(); }}>
        <label htmlFor={"social-comment-" + itemId}>Viết bình luận</label>
        <textarea id={"social-comment-" + itemId} value={text} maxLength={1000} rows={2}
          onChange={(event) => setText(event.target.value)}
          aria-describedby={"social-comment-hint-" + itemId}
          placeholder="Chia sẻ câu hỏi hoặc nhận xét…" />
        <div className={styles.commentFormActions}>
          <small id={"social-comment-hint-" + itemId}>{text.length}/1000 ký tự</small>
          <button type="submit" disabled={busy || text.trim().length < 2}>
            {busy ? "Đang gửi…" : "Gửi bình luận"}
          </button>
        </div>
      </form>
      {message && <p role={requiresLogin ? "alert" : "status"} className={requiresLogin ? styles.commentError : styles.commentHint}>
        {message} {requiresLogin && <Link href="/login">Đăng nhập</Link>}
      </p>}
    </section>
  );
}
