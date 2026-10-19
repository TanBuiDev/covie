# Required UI gate: desktop header overflow

> Existing isolated Billing worktree, narrow CSS fix/read-only review, no commit,
> branch, payment, backend replay, recovered data or original E checkout build.

**Goal:** Close measured overflow blocking original G5 UI acceptance without hiding
controls or changing navigation, financial/API behavior.

**Architecture:** Preserve DOM/accessibility/mobile styles. Let existing desktop
flex layout wrap when genuine minimum widths cannot fit, with height following its
rows and current70/72px single-row minima. Never clip document overflow or collapse
search into an unusable control.

**Files:** apps/web/app/buyer-desktop.css; focused native regression
apps/web/e2e/buyer-header-layout.local.spec.ts. Existing component/unit tests retained.

- [x] Actual current production native RED:360/390/1024/1280/1366/1440, overflow,
  all named desktop controls visible/bounded/nonoverlapping, native keyboard navigation.
- [x] Browser-only CSS prototype, explicitly not production acceptance.
- [x] Apply only demonstrated declarations; read-only review; no hidden controls.
- [x] Stop owned stack, rebuild Next, verify actual production native GREEN and view
  1024/1280/1440 screenshots. No original E build or weakening overflow verifier.
- [ ] Affected header tests/typecheck/lint, stable full web once. Backend unchanged.
- [ ] Resume exact owned UI command and whole plan, never fake old Figma reviews.

Prototype confirmed a second constraint:115px wrapped header covered the existing
88px sticky preview at1024/1280. Header now publishes measured height via the same
ResizeObserver/resize fallback pattern already used in AdDiscoveryLayout; only the
two existing Need sticky offsets consume it. No navigation, API or financial change.
Observer regression actual RED empty-vs115px; first affected run1PASS/2FAIL because
jsdom lacks ResizeObserver, then existing optional-observer pattern gives3PASS.
Actual rebuilt production focused native1PASS8.9s/11.6s, fixture
`billing-owned-ui-d8474de9cf46`, all six widths, bounded/nonoverlapping original
controls, accessible names, native Tab, Need create actual scroll and both configured
sticky offsets.1024/1280/1440 screenshots viewed. Build/typecheck/scoped lint exit0.
Owned keys/processes/containers cleaned; unchanged current API JAR retained.

Next action: Finish current full Web and exact owned UI orchestration.
Required from you: Nothing
Completion condition: Current rebuilt header fits viewport widths with every original control visible, named, nonoverlapping and keyboard-operable.
