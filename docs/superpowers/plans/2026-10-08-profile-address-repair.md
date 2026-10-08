# Profile and saved-address repair implementation plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox syntax for tracking.

**Goal:** Editable display name/avatar/phone, understandable GPS address entry and persisted default-address changes.

**Architecture:** Extend the existing authenticated profile API with private phone and owned READY USER_AVATAR media. Keep Google identity/email unchanged. Fix detached-entity address creation and honor explicit default=false. Reverse geocoding provides an editable address suggestion, with explicit delivery-pin confirmation and stale-result protection.

**Tech stack:** Spring Boot/JPA/PostGIS, generated OpenAPI TypeScript, Next.js/React, Vitest/Playwright.

### Backend profile and defaults
- [x] Add failing address service/database tests: repository.save returns managed copy; create selected default persists; edit false clears it, switch retains exactly one.
- [x] Use the saved managed entity; explicitly clear default on false; test with disposable PostGIS.
- [x] Add V016 profile phone, expand UpdateMeRequest/MeResponse, enforce ready owned USER_AVATAR; preserve omitted phone/avatar in legacy requests and support explicit null clearing.
- [x] Verify profile roundtrip/ownership/Google login preservation; export OpenAPI after geocoding API is ready.

### Profile UI
- [x] Retrieve verified Figma context for the existing host; reuse tokens/components for approved added editor.
- [x] Add generated-typed profile update, avatar image upload and phone/name editor; clear loading/error/success and duplicate-write protection.
- [x] Test validation, upload failure, failed mutation and successful response; integrate saved profile into overview.

### Address suggestions
- [x] Clarify text address versus delivery coordinates.
- [x] Verify external reverse-geocoding provider constraints; expose bounded configured server lookup with cache/rate protection and graceful failure.
- [x] Test GPS autofill, editable suggestion, stale GPS/reverse result protection, denied location and lookup failure.
- Retain existing saved-point selection and explicit delivery confirmation; text-copy expansion is outside this repair.

### Integration
- [x] Run focused backend tests, generated-client check, TypeScript/lint, frontend regression and fixture browser tests.
- [x] Document exact outcomes/runtime limitations; preserve dirty tree, credentials and recovered business data. No commit/merge requested.

Verification so far: 16 profile/address/OpenAPI backend tests plus 2 geocoding tests pass on isolated/stub infrastructure. Generated client/check and web TypeScript/scoped ESLint pass. Full web suite: 107 files, 441 tests pass on final rerun (initial run had an intermittent address selector and existing ad impression test failure). Focused final profile suite: 34 tests pass. Production build passes. Browser fixtures require a working local authenticated account; the stale buyer fixture returned 401. Runtime API/database have not been restarted or migrated; ignored `.env.local` contains the approved Nominatim endpoint. User can apply changes with Ctrl+C then `pnpm dev`.

Final browser evidence: Chrome profile fixture passed (1 test, 6.5 seconds) using the existing recovered Admin account at `/me`. At 390/1366 widths it verifies mocked avatar upload, duplicate-save prevention, failed-save retention/retry, canonical name/phone/avatar feedback, focus restoration and no horizontal overflow. All profile/media writes were intercepted. Four screenshots under `.local/profile-address/browser/` were visually inspected. Real GPS/provider lookup and real profile/avatar writes were not performed against the recovered runtime data.
