# AI rules for CoVie

These rules apply to every AI agent working in this repository.

## Required context

1. Read root `AGENTS.md` before inspecting or modifying project code.
2. Read any closer-scoped `AGENTS.md` before working in that directory.
3. Treat the Figma file linked in `AGENTS.md` and its FROZEN frames as the visual source of truth.
4. Read the relevant technical plan, API contract, and operational documentation before making a material change.

## Truthful delivery status

1. Never call a plan, feature, UI route, release, or project “complete” solely because it compiles, tests pass, a branch merges, or a document says closed.
2. Separate these claims explicitly: implemented, tested locally, visually reviewed against Figma, deployed to staging, and deployed to production.
3. Do not claim staging or production deployment without direct environment evidence.
4. Update `AGENTS.md` in the same change when delivery status or a known gap changes.

## UI and Figma

1. Use the verified Figma page map in root `AGENTS.md` to enter the correct design area. Before editing a screen, select and use its exact production frame URL with `node-id`; a page node or production-matrix card is not a substitute, and a frame must not be guessed.
2. Before every Figma `get_design_context` call, read the `figma:figma-design-to-code` skill and pass `skillNames: "figma-design-to-code"`. Use the connected Figma tool directly; do not treat a browser tab or a page-level URL as proof that an exact frame is unavailable.
3. Keep Figma requests narrow because connector quota is limited: retrieve only the exact frame(s) for the route currently being implemented, never scan the whole file speculatively.
4. Reuse the project’s design tokens and shared components. Do not substitute a generic scaffold, arbitrary colors, fonts, icons, or layouts for frozen Figma decisions.
5. Implement the full required state set: loading, empty, error, unauthorized, success, responsive/mobile and desktop behavior where applicable.
6. Validate rendered UI against the Figma frame before marking a screen complete. Functional unit/E2E tests alone are insufficient; screenshot automation must assert the destination URL and a route-unique exact landmark before capturing.
7. Preserve accessibility: semantic controls, keyboard access, visible focus, labels, contrast and responsive behavior.
8. **UI Icons & Visual System:** Tuyệt đối không dùng emoji Unicode làm UI icon cố định (danh mục, nút bấm, badge, trạng thái, vị trí, giao hàng, dịch vụ, thanh lý, hành động...). Mặc định sử dụng Phosphor Icons (`@phosphor-icons/react`): ưu tiên `weight="duotone"` kết hợp nền pastel tone-sur-tone cho categories/features mang tính trực quan để tạo cảm giác sinh động, cao cấp; dùng `regular`, `bold`, hoặc `fill` cho các biểu tượng chức năng (search, filter, close, navigation, location...). Emoji chỉ áp dụng cho nội dung do người dùng tự nhập.
9. **Desktop Sticky Sidebar & Layout Integrity:** Khi áp dụng bố cục Desktop chia cột có một cột cố định (`position: sticky`), các khối nội dung cuộn (reviews, danh mục, bình luận, chi tiết...) bắt buộc phải nằm trọn vẹn bên trong cột nội dung (content column). Tuyệt đối không để phần tử con dùng `grid-column: 1 / -1` hoặc span ngang cắt qua cột đang sticky, vì khi cuộn trang, nội dung cuộn sẽ trượt lên và đè chồng chéo (overlap) làm vỡ giao diện. Luôn kiểm tra hành vi cuộn trên màn hình Desktop trước khi bàn giao.
10. **Pagination & In-Place Load More UX:** Trên các giao diện danh sách, catalog, khám phá, feed hoặc hồ sơ công khai (Public Catalog / Merchant Profile), nút "Xem thêm" (Load More) bắt buộc phải thực hiện tải nối tiếp (Append in-place) và giữ nguyên các sản phẩm/mục đã tải trước đó mà không làm mới trang. Tuyệt đối không sử dụng URL navigation làm mất toàn bộ danh sách trang trước và bắt người dùng phải F5 lại trang để xem lại dữ liệu cũ.

## Architecture and contracts

1. Web code consumes generated `@covie/api-client` types; do not hand-copy API DTOs or enums.
2. Spring Boot owns authorization, pricing, stock, checkout, order transitions, need matching, reputation and moderation decisions.
3. UI renders server-provided permissions/`allowedActions`; it must not infer legal business transitions from status.
4. Regenerate and verify OpenAPI/client artifacts after any API contract change.
5. Do not store auth, refresh, checkout, or other sensitive tokens in URLs or localStorage.

## Engineering discipline

1. Preserve user changes and work carefully in a dirty tree.
2. Use `apply_patch` for source/document edits. Do not use destructive Git commands unless explicitly authorized.
3. Write or update tests for behavior changes. Run proportionate verification before reporting success.
4. Investigate failures to root cause before changing code; do not stack speculative fixes.
5. Keep secrets, PEM files, OAuth credentials, `.env.local`, and runtime-generated files out of commits.
6. Do not alter infrastructure, external services, branches, remote repositories, staging, or production unless the user has authorized that action.
7. Use `http://localhost:3000` consistently for local browser/E2E verification. Mixing `127.0.0.1` with `localhost` can change the origin and prevent Next.js client chunks or auth cookies from hydrating correctly.

## Handoff standard

Every handoff must state:

- What changed and where.
- What verification actually ran and its result.
- What remains incomplete or unverified.
- Any environment, credential, Figma-frame, or stakeholder input required next.

## Mandatory next-step format

Every substantive progress report and final handoff must finish with all three lines below, even when no work is blocked:

```text
Next action: <one concrete action the AI will take next>
Required from you: <exact required input, or "Nothing">
Completion condition: <observable result that ends the next action>
```

The agent must continue automatically when the next action is read-only or already within the user's authorization. If it cannot continue, it must say exactly why the input is necessary and provide the shortest way for the user to supply it. A report that merely lists findings without these next steps is incomplete.
