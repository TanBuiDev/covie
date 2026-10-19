import { afterEach, describe, expect, it, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { SocialFeedProvider, useSocialFeed } from "@/features/marketplace-view/social-feed";

const api = vi.hoisted(() => ({ fetchSocialSummaries: vi.fn(), fetchSocialPreviews: vi.fn() }));
vi.mock("@/features/marketplace-view/social-api", () => api);

function Probe() {
  const state = useSocialFeed();
  return <>
    <output data-testid="preview">{JSON.stringify(state.previews)}</output>
    <output data-testid="preview-state">{state.previewsLoading ? "loading" : state.previewsFailed ? "failed" : "ready"}</output>
    <output data-testid="summary-count">{Object.keys(state.summaries).length}</output>
    <button onClick={state.retryPreviews}>Retry preview</button>
  </>;
}

afterEach(() => { vi.resetAllMocks(); vi.unstubAllEnvs(); });

describe("Social V2 preview batching", () => {
  it("keeps the default flag and Catalog mode free of preview requests", async () => {
    api.fetchSocialSummaries.mockResolvedValue({});
    vi.stubEnv("NEXT_PUBLIC_COVIE_SOCIAL_V2_PREVIEWS_ENABLED", "false");
    const view = render(<SocialFeedProvider itemIds={["item"]} enabled><Probe /></SocialFeedProvider>);
    await waitFor(() => expect(api.fetchSocialSummaries).toHaveBeenCalledTimes(1));
    expect(api.fetchSocialPreviews).not.toHaveBeenCalled();
    view.rerender(<SocialFeedProvider itemIds={["item"]} enabled={false} previewsEnabled><Probe /></SocialFeedProvider>);
    expect(api.fetchSocialPreviews).not.toHaveBeenCalled();
    expect(screen.getByTestId("preview")).toHaveTextContent("{}");
  });

  it("deduplicates and splits Social requests into 40-ID chunks", async () => {
    const ids = Array.from({ length: 41 }, (_, index) => `item-${index.toString().padStart(2, "0")}`);
    api.fetchSocialSummaries.mockResolvedValue({});
    api.fetchSocialPreviews.mockImplementation(async (batch: string[]) => Object.fromEntries(batch.map((id) =>
      [id, { itemId: id, socialCaption: `Caption ${id}`, media: [] }])));
    render(<SocialFeedProvider itemIds={[...ids, ids[0]!]} enabled previewsEnabled><Probe /></SocialFeedProvider>);
    await waitFor(() => expect(screen.getByTestId("preview-state")).toHaveTextContent("ready"));
    expect(api.fetchSocialPreviews.mock.calls.map(([batch]) => batch.length)).toEqual([40, 1]);
    expect(Object.keys(JSON.parse(screen.getByTestId("preview").textContent!))).toHaveLength(41);
    expect(api.fetchSocialSummaries).toHaveBeenCalledTimes(2);
  });

  it("retries failed previews without discarding successful summaries", async () => {
    api.fetchSocialSummaries.mockResolvedValue({ item: { itemId: "item", likes: 3, comments: 0, liked: false, saved: false } });
    api.fetchSocialPreviews.mockRejectedValueOnce(new Error("offline"))
      .mockResolvedValueOnce({ item: { itemId: "item", socialCaption: "Recovered", media: [] } });
    render(<SocialFeedProvider itemIds={["item"]} enabled previewsEnabled><Probe /></SocialFeedProvider>);
    await waitFor(() => expect(screen.getByTestId("preview-state")).toHaveTextContent("failed"));
    expect(screen.getByTestId("summary-count")).toHaveTextContent("1");
    fireEvent.click(screen.getByRole("button", { name: "Retry preview" }));
    await waitFor(() => expect(screen.getByTestId("preview")).toHaveTextContent("Recovered"));
    expect(api.fetchSocialSummaries).toHaveBeenCalledTimes(1);
    expect(api.fetchSocialPreviews).toHaveBeenCalledTimes(2);
  });

  it("ignores a pending response after switching to Catalog and then loads the new item scope", async () => {
    let finish!: (value: unknown) => void;
    api.fetchSocialSummaries.mockResolvedValue({});
    api.fetchSocialPreviews.mockImplementationOnce(() => new Promise((resolve) => { finish = resolve; }))
      .mockResolvedValue({ newer: { itemId: "newer", socialCaption: "New scope", media: [] } });
    const view = render(<SocialFeedProvider itemIds={["old"]} enabled previewsEnabled><Probe /></SocialFeedProvider>);
    await waitFor(() => expect(api.fetchSocialPreviews).toHaveBeenCalledTimes(1));
    view.rerender(<SocialFeedProvider itemIds={["old"]} enabled={false} previewsEnabled><Probe /></SocialFeedProvider>);
    await act(async () => finish({ old: { itemId: "old", socialCaption: "Stale", media: [] } }));
    expect(screen.getByTestId("preview")).toHaveTextContent("{}");
    view.rerender(<SocialFeedProvider itemIds={["newer"]} enabled previewsEnabled><Probe /></SocialFeedProvider>);
    await waitFor(() => expect(screen.getByTestId("preview")).toHaveTextContent("New scope"));
    expect(screen.getByTestId("preview")).not.toHaveTextContent("Stale");
  });

  it("uses the build-time flag and clears successful data before re-entering Social", async () => {
    vi.stubEnv("NEXT_PUBLIC_COVIE_SOCIAL_V2_PREVIEWS_ENABLED", "true");
    let finish!: (value: unknown) => void;
    api.fetchSocialSummaries.mockResolvedValue({});
    api.fetchSocialPreviews.mockResolvedValueOnce({ item: { itemId: "item", socialCaption: "Before hide", media: [] } })
      .mockImplementationOnce(() => new Promise((resolve) => { finish = resolve; }));
    const view = render(<SocialFeedProvider itemIds={["item"]} enabled><Probe /></SocialFeedProvider>);
    await waitFor(() => expect(screen.getByTestId("preview")).toHaveTextContent("Before hide"));
    view.rerender(<SocialFeedProvider itemIds={["item"]} enabled={false}><Probe /></SocialFeedProvider>);
    view.rerender(<SocialFeedProvider itemIds={["item"]} enabled><Probe /></SocialFeedProvider>);
    expect(screen.getByTestId("preview")).toHaveTextContent("{}");
    expect(screen.getByTestId("preview-state")).toHaveTextContent("loading");
    await act(async () => finish({}));
    await waitFor(() => expect(screen.getByTestId("preview-state")).toHaveTextContent("ready"));
    expect(screen.getByTestId("preview")).not.toHaveTextContent("Before hide");
  });
});
