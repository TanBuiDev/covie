import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { ProfileEditForm } from "@/features/profile/profile-edit-form";
import { CovieApiError } from "@/lib/api/api-error";
const api = vi.hoisted(() => ({ updateProfile: vi.fn() }));
const upload = vi.hoisted(() => vi.fn());
vi.mock("@/features/profile/profile-client-api", () => api);
vi.mock("@/features/media/direct-image-upload", () => ({ uploadImage: upload }));
const user = { id: "buyer", displayName: "Tên cũ", email: "buyer@covie.test", phone: "0901234567", avatarMediaId: "avatar-1", avatarUrl: "https://cdn.test/avatar.png" };
beforeEach(() => vi.clearAllMocks());
it("submits changed name and private phone only once and emits saved server profile", async () => {
  let resolve: ((value: unknown) => void) | undefined;
  api.updateProfile.mockImplementation(() => new Promise(done => { resolve = done; }));
  const saved = vi.fn();
  render(<ProfileEditForm user={user} onSaved={saved} onCancel={vi.fn()} />);
  fireEvent.change(screen.getByLabelText("Tên hiển thị"), { target: { value: "Tên mới" } });
  const form = screen.getByRole("form", { name: "Chỉnh sửa hồ sơ" });
  act(() => { fireEvent.submit(form); fireEvent.submit(form); });
  expect(api.updateProfile).toHaveBeenCalledTimes(1);
  expect(api.updateProfile).toHaveBeenCalledWith({ displayName: "Tên mới", phone: "0901234567", avatarMediaId: "avatar-1" });
  expect(saved).not.toHaveBeenCalled();
  await act(async () => resolve?.({ ...user, displayName: "Tên mới" }));
  expect(saved).toHaveBeenCalledWith({ ...user, displayName: "Tên mới" });
});
it("rejects blank names and invalid phone locally", async () => {
  render(<ProfileEditForm user={user} onSaved={vi.fn()} onCancel={vi.fn()} />);
  fireEvent.change(screen.getByLabelText("Tên hiển thị"), { target: { value: "  " } });
  fireEvent.submit(screen.getByRole("form", { name: "Chỉnh sửa hồ sơ" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("tên hiển thị");
  expect(api.updateProfile).not.toHaveBeenCalled();
});
it("retains edited fields on a failed save and permits explicit retry", async () => {
  api.updateProfile.mockRejectedValueOnce(new Error("private diagnostics")).mockResolvedValueOnce({ ...user, displayName: "Tên mới" });
  render(<ProfileEditForm user={user} onSaved={vi.fn()} onCancel={vi.fn()} />);
  fireEvent.change(screen.getByLabelText("Tên hiển thị"), { target: { value: "Tên mới" } });
  fireEvent.submit(screen.getByRole("form", { name: "Chỉnh sửa hồ sơ" }));
  expect(await screen.findByRole("alert")).not.toHaveTextContent("private diagnostics");
  expect(screen.getByLabelText("Tên hiển thị")).toHaveValue("Tên mới");
  fireEvent.submit(screen.getByRole("form", { name: "Chỉnh sửa hồ sơ" }));
  await waitFor(() => expect(api.updateProfile).toHaveBeenCalledTimes(2));
});
it("clears optional phone and avatar explicitly on save", async () => {
  api.updateProfile.mockResolvedValue({ ...user, phone: null, avatarMediaId: null, avatarUrl: null });
  render(<ProfileEditForm user={user} onSaved={vi.fn()} onCancel={vi.fn()} />);
  fireEvent.change(screen.getByLabelText("Số điện thoại"), { target: { value: "" } });
  fireEvent.click(screen.getByRole("button", { name: "Xóa ảnh đại diện" }));
  fireEvent.submit(screen.getByRole("form", { name: "Chỉnh sửa hồ sơ" }));
  await waitFor(() => expect(api.updateProfile).toHaveBeenCalledWith({ displayName: "Tên cũ", phone: null, avatarMediaId: null }));
});
it("uploads an owned avatar purpose and blocks save until verification finishes", async () => {
  let resolve: ((value: unknown) => void) | undefined;
  upload.mockImplementation(() => new Promise(done => { resolve = done; }));
  Object.defineProperty(URL, "createObjectURL", { configurable: true, value: vi.fn(() => "blob:verified-avatar") });
  Object.defineProperty(URL, "revokeObjectURL", { configurable: true, value: vi.fn() });
  render(<ProfileEditForm user={user} onSaved={vi.fn()} onCancel={vi.fn()} />);
  const file = new File(["image"], "avatar.png", { type: "image/png" });
  act(() => { fireEvent.change(screen.getByLabelText("Ảnh đại diện"), { target: { files: [file] } }); fireEvent.change(screen.getByLabelText("Ảnh đại diện"), { target: { files: [file] } }); });
  expect(upload).toHaveBeenCalledWith(file, "USER_AVATAR");
  expect(upload).toHaveBeenCalledTimes(1);
  expect(screen.getByRole("button", { name: "Lưu hồ sơ" })).toBeDisabled();
  await act(async () => resolve?.({ mediaId: "verified-avatar" }));
  expect(screen.getByRole("img", { name: "Xem trước ảnh đại diện" })).toHaveAttribute("src", "blob:verified-avatar");
});
it("does not accept a video as an avatar", async () => {
  render(<ProfileEditForm user={user} onSaved={vi.fn()} onCancel={vi.fn()} />);
  fireEvent.change(screen.getByLabelText("Ảnh đại diện"), { target: { files: [new File(["video"], "avatar.mp4", { type: "video/mp4" })] } });
  expect(await screen.findByRole("alert")).toHaveTextContent("PNG");
  expect(upload).not.toHaveBeenCalled();
});
it("replaces expired edit controls with exact profile login path", async () => {
  api.updateProfile.mockRejectedValue(new CovieApiError(401, "UNAUTHORIZED", "private"));
  render(<ProfileEditForm user={user} onSaved={vi.fn()} onCancel={vi.fn()} />);
  fireEvent.submit(screen.getByRole("form", { name: "Chỉnh sửa hồ sơ" }));
  expect(await screen.findByRole("link", { name: "Đăng nhập lại" })).toHaveAttribute("href", "/login?returnTo=%2Fme");
  expect(screen.queryByRole("button", { name: "Lưu hồ sơ" })).toBeNull();
});
it("rejects invalid phone without changing the profile", async () => {
  render(<ProfileEditForm user={user} onSaved={vi.fn()} onCancel={vi.fn()} />);
  fireEvent.change(screen.getByLabelText("Số điện thoại"), { target: { value: "phone-invalid" } });
  fireEvent.submit(screen.getByRole("form", { name: "Chỉnh sửa hồ sơ" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("7 đến 15 chữ số");
  expect(api.updateProfile).not.toHaveBeenCalled();
});
it("failed upload retains original avatar and allows selecting the file again", async () => {
  upload.mockRejectedValueOnce(new Error("storage secret"));
  render(<ProfileEditForm user={user} onSaved={vi.fn()} onCancel={vi.fn()} />);
  const file = new File(["image"], "avatar.png", { type: "image/png" });
  fireEvent.change(screen.getByLabelText("Ảnh đại diện"), { target: { files: [file] } });
  expect(await screen.findByRole("alert")).not.toHaveTextContent("storage secret");
  expect(screen.getByRole("img", { name: "Xem trước ảnh đại diện" })).toHaveAttribute("src", user.avatarUrl);
  expect(screen.getByRole("button", { name: "Lưu hồ sơ" })).toBeEnabled();
});
it("ignores a successful response after the editor unmounts", async () => {
  let resolve: ((value: unknown) => void) | undefined;
  api.updateProfile.mockImplementation(() => new Promise(done => { resolve = done; }));
  const saved = vi.fn();
  const view = render(<ProfileEditForm user={user} onSaved={saved} onCancel={vi.fn()} />);
  fireEvent.submit(screen.getByRole("form", { name: "Chỉnh sửa hồ sơ" }));
  view.unmount();
  await act(async () => resolve?.({ ...user, displayName: "Late" }));
  expect(saved).not.toHaveBeenCalled();
});
