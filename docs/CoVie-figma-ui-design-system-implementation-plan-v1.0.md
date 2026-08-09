# CoVie Figma UI Design System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Triển khai Brand Foundation và UI Design System đã duyệt thành Figma variables, styles, components, responsive examples và P0-ready design assets trong file `CoVie — V1 UI Design`.

**Architecture:** Figma file được chia thành `01 — Foundations`, `02 — Components`, các page Buyer/Seller/Admin và Prototypes. Foundations là nguồn token/style; Components chỉ dùng semantic tokens và Auto Layout; các domain component không được tự suy business state mà chỉ nhận presentation state đã được xác định từ product/API behavior.

**Tech Stack:** Figma Design, Figma Variables, Components/Variants, Auto Layout, Inter, Emerald brand palette, WCAG 2.2 AA.

## Global Constraints

- Brand: `CoVie = Chợ Việt`.
- Direction: Friendly Local Marketplace.
- Primary: Emerald 600 `#059669`.
- Typography: Inter.
- Density: Balanced.
- Surface: Soft Elevation.
- Base spacing: `4 px`.
- Base radius: `12 px`.
- Consumer UI mobile-first.
- Seller Center mobile/tablet usable.
- Admin desktop-first.
- Consumer reference viewports: `360×800`, `390×844`; tablet `768×1024`; desktop `1440×900`.
- Minimum mobile interactive target: `44×44 px`.
- Target accessibility: WCAG 2.2 AA.
- No raw backend enums in user-facing copy.
- No component may infer authoritative Order/Need/review eligibility/business state.
- Negotiated shipping copy is `Thỏa thuận với người bán`, never `0 ₫`.
- Service quantity is always `1`; no QuantityStepper for Service.
- Destructive and irreversible actions use explicit confirmation.
- Completion confirmation explicitly states it cannot be undone.
- Reusable containers use Auto Layout unless an overlay/absolute treatment is intentional.
- Component masters use semantic variables instead of duplicated raw hex values.
- No dark mode, chat, payment QR, booking calendar, native-app-only system, loyalty, gamification or AI assistant components in V1.

---

## File / Page Map

### Existing Figma file

`CoVie — V1 UI Design`

### Pages

```text
00 — Cover & Index
01 — Foundations
02 — Components
03 — Buyer Mobile
04 — Seller Mobile
05 — Seller Desktop
06 — Admin Desktop
07 — Prototypes
99 — Archive
```

### Source artifacts

```text
CoVie-brand-foundation-spec-v1.0.md
CoVie-ui-design-system-spec-v1.0.md
CoVie-user-flow-v1.0.md
CoVie-information-architecture-ui-flow-v1.0.md
CoVie-technical-plan-09-web-completion-v1.0.md
```

---

### Task 1: Build Foundation Variable Collections

**Figma page:** `01 — Foundations`

**Consumes:**
- Brand Foundation Spec V1.0.
- Approved Emerald/Neutral/Semantic palettes.
- Approved spacing/radius/elevation system.

**Produces:**
- Primitive color variables.
- Semantic color variables.
- Spacing variables.
- Radius variables.
- Sizing variables where reusable.
- Motion duration variables where useful.

- [ ] **Step 1: Create primitive color collection**

Create:

```text
01 Primitive Colors
```

Variables:

```text
color/emerald/50   #ECFDF5
color/emerald/100  #D1FAE5
color/emerald/200  #A7F3D0
color/emerald/300  #6EE7B7
color/emerald/400  #34D399
color/emerald/500  #10B981
color/emerald/600  #059669
color/emerald/700  #047857
color/emerald/800  #065F46
color/emerald/900  #064E3B
color/emerald/950  #022C22

color/neutral/0    #FFFFFF
color/neutral/25   #FCFCFD
color/neutral/50   #F8FAFC
color/neutral/100  #F1F5F9
color/neutral/200  #E2E8F0
color/neutral/300  #CBD5E1
color/neutral/400  #94A3B8
color/neutral/500  #64748B
color/neutral/600  #475569
color/neutral/700  #334155
color/neutral/800  #1E293B
color/neutral/900  #0F172A
color/neutral/950  #020617
```

- [ ] **Step 2: Create semantic color collection**

Create:

```text
02 Semantic Colors
```

Core mappings:

```text
semantic/bg/canvas           -> neutral/50
semantic/bg/subtle           -> neutral/100
semantic/bg/surface          -> neutral/0
semantic/bg/surface-hover    -> neutral/50
semantic/bg/brand-subtle     -> emerald/50

semantic/text/primary        -> neutral/900
semantic/text/secondary      -> neutral/600
semantic/text/tertiary       -> neutral/500
semantic/text/disabled       -> neutral/400
semantic/text/inverse        -> neutral/0
semantic/text/brand          -> emerald/700

semantic/border/default      -> neutral/200
semantic/border/subtle       -> neutral/100
semantic/border/strong       -> neutral/300
semantic/border/brand        -> emerald/300
semantic/border/focus        -> emerald/500

semantic/action/primary         -> emerald/600
semantic/action/primary-hover   -> emerald/700
semantic/action/primary-pressed -> emerald/800
```

Add semantic Success/Warning/Error/Info tokens from the approved Brand Foundation.

- [ ] **Step 3: Create spacing collection**

```text
space/0  = 0
space/1  = 4
space/2  = 8
space/3  = 12
space/4  = 16
space/5  = 20
space/6  = 24
space/8  = 32
space/10 = 40
space/12 = 48
space/16 = 64
space/20 = 80
space/24 = 96
```

- [ ] **Step 4: Create radius collection**

```text
radius/xs   = 4
radius/sm   = 8
radius/md   = 12
radius/lg   = 16
radius/xl   = 20
radius/full = 999
```

- [ ] **Step 5: Verify variable names and scopes**

Acceptance:

```text
no unnamed variables
no duplicate raw Emerald names
semantic tokens reference primitive values
spacing/radius values match spec exactly
```

---

### Task 2: Build Typography and Elevation Styles

**Figma page:** `01 — Foundations`

**Consumes:** Task 1 tokens.

**Produces:** Text styles and effect styles.

- [ ] **Step 1: Load and use Inter**

Required weights:

```text
Regular 400
Semi Bold 600
Bold 700
```

Use Medium only where a component later demonstrates a real need.

- [ ] **Step 2: Create text styles**

```text
Display/LG        40/48 700
Display/SM        32/40 700
Heading/H1        28/36 700
Heading/H2        24/32 700
Heading/H3        20/28 600
Title/MD          18/26 600
Body/LG           16/24 400
Body/MD           14/20 400
Body/Strong       14/20 600
Label/MD          13/18 600
Caption/MD        12/16 400
Caption/Strong    12/16 600
```

- [ ] **Step 3: Create elevation styles**

```text
Elevation/0 = none
Elevation/1 = 0 1 2 rgba(15,23,42,0.05)
Elevation/2 = 0 4 12 rgba(15,23,42,0.08)
Elevation/3 = 0 12 32 rgba(15,23,42,0.12)
```

- [ ] **Step 4: Build typography/elevation reference boards**

Include one sample for every style and a “when to use” label.

Acceptance:

```text
all text styles use Inter
no text smaller than 12 px
soft elevation remains border-first
```

---

### Task 3: Build Foundation Reference Boards

**Figma page:** `01 — Foundations`

**Produces sections:**

```text
01 Brand Direction
02 Logo System
03 Color Primitives
04 Semantic Colors
05 Typography
06 Spacing
07 Radius
08 Elevation
09 Iconography
10 Grid & Breakpoints
11 Motion
12 Accessibility
13 Voice & Microcopy
```

- [ ] **Step 1: Add Brand Direction board**
- [ ] **Step 2: Add palette boards bound to variables**
- [ ] **Step 3: Add spacing/radius boards**
- [ ] **Step 4: Add mobile grid examples for 360/390**
- [ ] **Step 5: Add tablet example for 768**
- [ ] **Step 6: Add desktop grid example for 1440**
- [ ] **Step 7: Add iconography direction sample**
- [ ] **Step 8: Add accessibility contrast/focus examples**
- [ ] **Step 9: Add approved Vietnamese microcopy examples**

Acceptance:

```text
every board is readable without inspecting layers
all examples use actual styles/variables
no decorative Vietnamese cliché motifs
```

---

### Task 4: Explore and Lock Market Pin Logo

**Figma page:** `01 — Foundations`

**Produces:**
- Three concept candidates.
- One approved master after user review.
- Symbol-only, horizontal, monochrome and small-size variants.

- [ ] **Step 1: Draw 3 Market Pin concepts**

Each must combine:

```text
location pin
market/stall/roof
modern Vietnamese architectural cue
```

- [ ] **Step 2: Test each concept at 64, 32, 24, 20 and 16 px**
- [ ] **Step 3: Test monochrome dark and light**
- [ ] **Step 4: Present candidates side by side**
- [ ] **Step 5: User selects one candidate**
- [ ] **Step 6: Refine selected vector and wordmark spacing**
- [ ] **Step 7: Build Primary Horizontal / Symbol / Wordmark / App Icon / Favicon variants**

Acceptance:

```text
recognizable at 20–24 px
usable at 16 px with simplified master if needed
does not look restaurant-only
does not use literal national/cultural clichés
```

---

### Task 5: Build Actions and Basic Inputs

**Figma page:** `02 — Components`

**Produces:**

```text
Action/Button
Action/IconButton
Input/TextField
Input/SearchField
Input/Textarea
```

- [ ] **Step 1: Build Button master with semantic tokens**
- [ ] **Step 2: Add Primary/Secondary/Tertiary/Destructive hierarchy**
- [ ] **Step 3: Add SM/MD/LG sizing**
- [ ] **Step 4: Add Default/Hover/Pressed/Focus/Disabled/Loading states**
- [ ] **Step 5: Build IconButton with >=44 px mobile target**
- [ ] **Step 6: Build TextField anatomy and validation**
- [ ] **Step 7: Build SearchField with clear action**
- [ ] **Step 8: Build Textarea**
- [ ] **Step 9: Add long Vietnamese label stress tests**

Acceptance:

```text
no fixed button width that breaks long copy
focus visible
disabled readable
loading preserves width
destructive never emerald
```

---

### Task 6: Build Selection Controls

**Figma page:** `02 — Components`

**Produces:**

```text
Input/Select
Selection/Checkbox
Selection/Radio
Selection/Switch
Selection/Chip
Selection/SegmentedControl
Commerce/QuantityStepper
```

- [ ] **Step 1: Build Select**
- [ ] **Step 2: Build Checkbox**
- [ ] **Step 3: Build Radio**
- [ ] **Step 4: Build Switch**
- [ ] **Step 5: Build Chip**
- [ ] **Step 6: Build SegmentedControl**
- [ ] **Step 7: Build QuantityStepper**
- [ ] **Step 8: Add explicit note that QuantityStepper is not used for Service**

Acceptance:

```text
visible focus
color not sole selected indicator
selection controls meet touch target
```

---

### Task 7: Build Navigation

**Figma page:** `02 — Components`

**Produces:**

```text
Navigation/AppBarMobile
Navigation/BottomNav
Navigation/SellerSidebar
Navigation/SellerMobileNav
Navigation/AdminSidebar
Navigation/Tabs
```

- [ ] **Step 1: Build marketplace mobile AppBar**
- [ ] **Step 2: Build 5-item Buyer BottomNav**
- [ ] **Step 3: Build Seller desktop/tablet sidebar**
- [ ] **Step 4: Build Seller mobile navigation**
- [ ] **Step 5: Build Admin desktop sidebar**
- [ ] **Step 6: Build Tabs underline and pill variants**

Canonical Buyer BottomNav labels:

```text
Trang chủ
Khám phá
Cần mua
Đơn
Tôi
```

Acceptance:

```text
active state uses Emerald
inactive remains neutral
Buyer/Seller visual family remains related
Admin is more neutral/compact
```

---

### Task 8: Build Search, Feedback and Overlay Patterns

**Figma page:** `02 — Components`

**Produces:**

```text
Search/SearchBar
Search/FilterBar
Search/FilterSheet
Feedback/StatusChip
Feedback/Alert
Feedback/WarningBanner
Feedback/Toast
Feedback/EmptyState
Feedback/ErrorState
Feedback/LoadingState
Overlay/ConfirmDialog
Overlay/Modal
Overlay/BottomSheet
Overlay/Popover
```

- [ ] **Step 1: Build SearchBar and FilterBar**
- [ ] **Step 2: Build mobile FilterSheet**
- [ ] **Step 3: Build StatusChip**
- [ ] **Step 4: Build Alert and WarningBanner**
- [ ] **Step 5: Build Toast**
- [ ] **Step 6: Build Empty/Error/Loading states**
- [ ] **Step 7: Build ConfirmDialog**
- [ ] **Step 8: Build Modal/BottomSheet/Popover**
- [ ] **Step 9: Add completion confirmation sample containing irreversible warning**

Acceptance:

```text
unknown status fallback copy supported
destructive dialog hierarchy correct
focus/overlay behavior annotated
Search empty can compose a Need CTA
```

---

### Task 9: Build Generic Data Components

**Figma page:** `02 — Components`

**Produces:**

```text
Commerce/Price
Data/MetadataRow
Trust/Rating
Data/Pagination
Data/Table
Data/RowActionMenu
Identity/Avatar
Data/Badge
Primitive/Divider
Feedback/Skeleton
```

- [ ] **Step 1: Build Price**
- [ ] **Step 2: Build MetadataRow**
- [ ] **Step 3: Build Rating**
- [ ] **Step 4: Build Pagination visual pattern**
- [ ] **Step 5: Build responsive DataTable**
- [ ] **Step 6: Build RowActionMenu**
- [ ] **Step 7: Build Avatar and Badge**
- [ ] **Step 8: Build Skeleton patterns**

Acceptance:

```text
large VND amount stress-tested
Admin table horizontal-scroll example exists
rating is visually distinct from reputation
```

---

### Task 10: Build Marketplace Components

**Figma page:** `02 — Components`

**Produces:**

```text
Marketplace/ProductCard
Marketplace/MerchantCard
Marketplace/LocationCard
Marketplace/CategoryTile
Marketplace/ListingHeader
```

- [ ] **Step 1: Build ProductCard grid**
- [ ] **Step 2: Build ProductCard list**
- [ ] **Step 3: Add Product/Resale/Service content examples**
- [ ] **Step 4: Build MerchantCard**
- [ ] **Step 5: Build LocationCard**
- [ ] **Step 6: Build CategoryTile**
- [ ] **Step 7: Build ListingHeader**
- [ ] **Step 8: Stress-test missing image, long title, large price, long merchant name**

Acceptance:

```text
card scanning order = what / price / where / seller / fulfillment
maximum two simultaneous badges in normal card example
no invented "open now" state
```

---

### Task 11: Build Cart and Checkout Components

**Figma page:** `02 — Components`

**Produces:**

```text
Commerce/FulfillmentOption
Commerce/ShippingMethod
Commerce/CartItem
Commerce/CartGroup
Commerce/MultiCartOverview
Checkout/Section
Checkout/Summary
Checkout/CommitBar
Checkout/SuccessPanel
```

- [ ] **Step 1: Build FulfillmentOption**
- [ ] **Step 2: Build ShippingMethod with Known/Negotiated/Unavailable**
- [ ] **Step 3: Build Product CartItem**
- [ ] **Step 4: Build Service CartItem without quantity control**
- [ ] **Step 5: Build CartGroup**
- [ ] **Step 6: Build MultiCartOverview**
- [ ] **Step 7: Build CheckoutSection**
- [ ] **Step 8: Build CheckoutSummary**
- [ ] **Step 9: Build sticky CheckoutCommitBar**
- [ ] **Step 10: Build CheckoutSuccessPanel**

Acceptance:

```text
Negotiated shipping displays "Thỏa thuận với người bán"
one MerchantLocation per CartGroup
no multi-seller checkout CTA
Success does not imply Seller confirmed
```

---

### Task 12: Build Order Components

**Figma page:** `02 — Components`

**Produces:**

```text
Order/OrderCard
Order/Timeline
Order/ActionPanel
Order/CompletionConfirm
Order/IssuePanel
```

- [ ] **Step 1: Build OrderCard**
- [ ] **Step 2: Build Timeline**
- [ ] **Step 3: Build ActionPanel composition**
- [ ] **Step 4: Build completion confirmation**
- [ ] **Step 5: Build IssuePanel**
- [ ] **Step 6: Add safe unknown-state example**

Acceptance:

```text
no raw enum
no locally inferred lifecycle
COMPLETED copy says irreversible
Issue UI does not imply Admin arbitration
```

---

### Task 13: Build Need, Trust and Notification Components

**Figma page:** `02 — Components`

**Produces:**

```text
Need/NeedCard
Need/NeedForm
Need/ResponseCard
Need/Expiry
Trust/ReviewSummary
Trust/ReviewForm
Trust/ReputationSummary
Trust/ReportAction
Notification/Item
```

- [ ] **Step 1: Build NeedCard**
- [ ] **Step 2: Build NeedForm**
- [ ] **Step 3: Build NeedResponseCard**
- [ ] **Step 4: Build NeedExpiry**
- [ ] **Step 5: Build ReviewSummary/Form**
- [ ] **Step 6: Build ReputationSummary**
- [ ] **Step 7: Build ReportAction**
- [ ] **Step 8: Build NotificationItem**

Acceptance:

```text
Review and Reputation are visually distinct
Need response visually references real catalog offer
expiry component is presentation-only
report does not imply dispute arbitration
```

---

### Task 14: Build Seller Components

**Figma page:** `02 — Components`

**Produces:**

```text
Seller/MetricCard
Seller/OrderRow
Seller/CatalogRow
Seller/InventoryRow
Seller/LocationSummary
Seller/NeedMatchRow
```

- [ ] **Step 1: Build MetricCard**
- [ ] **Step 2: Build OrderRow mobile and desktop**
- [ ] **Step 3: Build CatalogRow**
- [ ] **Step 4: Build InventoryRow**
- [ ] **Step 5: Build LocationSummary**
- [ ] **Step 6: Build NeedMatchRow**
- [ ] **Step 7: Add tablet density example**

Acceptance:

```text
Seller remains usable at 768 width
available/reserved/stock semantics remain clear
no unapproved revenue analytics
```

---

### Task 15: Build Admin Components

**Figma page:** `02 — Components`

**Produces:**

```text
Admin/StatCard
Admin/QueueRow
Admin/EntitySummary
Admin/SettingsField
Admin/AuditRow
```

- [ ] **Step 1: Build StatCard**
- [ ] **Step 2: Build QueueRow**
- [ ] **Step 3: Build EntitySummary**
- [ ] **Step 4: Build SettingsField**
- [ ] **Step 5: Build AuditRow**
- [ ] **Step 6: Compose admin table + row actions example**

Acceptance:

```text
neutral/compact visual language
no force Order status action
high-impact settings can compose ConfirmDialog
```

---

### Task 16: Responsive and Accessibility QA Boards

**Figma page:** `02 — Components`

**Produces sections:**

```text
17 Responsive Examples
18 Accessibility States
```

- [ ] **Step 1: Build 360×800 component composition**
- [ ] **Step 2: Build 390×844 composition**
- [ ] **Step 3: Build 768×1024 Seller composition**
- [ ] **Step 4: Build 1440×900 Admin composition**
- [ ] **Step 5: Review 44×44 touch targets**
- [ ] **Step 6: Review focus states**
- [ ] **Step 7: Review critical contrast pairs**
- [ ] **Step 8: Add reduced-motion annotations**
- [ ] **Step 9: Stress-test long Vietnamese copy**

Acceptance:

```text
no consumer horizontal overflow
sticky CTA is not hidden by BottomNav
Seller remains usable on tablet
Admin row actions remain reachable with horizontal table scroll
```

---

### Task 17: Build Component Index and Engineering Handoff Notes

**Figma pages:** `00 — Cover & Index`, `02 — Components`

**Produces:**
- Component inventory/index.
- Status markers.
- Handoff annotations.

- [ ] **Step 1: Add cover with CoVie / Chợ Việt identity**
- [ ] **Step 2: Add page index**
- [ ] **Step 3: Add component inventory**
- [ ] **Step 4: Mark components as Draft / Review / Frozen**
- [ ] **Step 5: Add Figma → code naming annotations for core shared components**
- [ ] **Step 6: Add domain-ownership notes for business composites**

Acceptance:

```text
a new designer/developer can find the relevant component without canvas hunting
generic UI vs feature/domain ownership is clear
```

---

### Task 18: Component System Final Gate Before P0 Screens

**Review:** Full Figma file.

- [ ] **Step 1: Verify Foundations against Brand Foundation Spec**
- [ ] **Step 2: Verify component inventory against UI Design System Spec**
- [ ] **Step 3: Verify no detached duplicate is being treated as a master**
- [ ] **Step 4: Verify semantic variable usage**
- [ ] **Step 5: Verify component descriptions**
- [ ] **Step 6: Verify responsive/accessibility boards**
- [ ] **Step 7: Record unresolved visual issues**
- [ ] **Step 8: Freeze core components only when all required checks pass**

Final acceptance:

```text
Foundation visually reviewed
Core actions/inputs reviewed
Navigation reviewed
Feedback/overlay reviewed
Marketplace reviewed
Commerce reviewed
Order/Need/Trust reviewed
Seller/Admin base system reviewed
Responsive matrix reviewed
Accessibility baseline reviewed
No unresolved source-contract conflict
```

Only then begin P0 screen production on:

```text
03 — Buyer Mobile
04 — Seller Mobile
05 — Seller Desktop
06 — Admin Desktop
07 — Prototypes
```

---

## Execution Checkpoints

Recommended user review checkpoints:

```text
Checkpoint A
Task 1–3
Foundations

Checkpoint B
Task 4
Logo concepts

Checkpoint C
Task 5–9
Core UI components

Checkpoint D
Task 10–13
Marketplace / Commerce / Order / Need / Trust

Checkpoint E
Task 14–16
Seller / Admin / Responsive / Accessibility

Checkpoint F
Task 17–18
Index + final component-system gate
```

Do not wait until all 18 tasks are finished before showing progress.

---

## Final Handoff

After Task 18 passes:

```text
Brand Foundation: APPROVED
UI Design System Spec: APPROVED
Figma Foundations: FROZEN
Core Figma Components: FROZEN
P0 Screen Design: READY
```

Next design plan:

```text
CoVie P0 Screen Design Plan
```

starting with:

```text
Buyer Home
Search Results
Listing Detail
Cart
Checkout
Order Detail
Need Create / Detail
```

---

**End of CoVie Figma UI Design System Implementation Plan V1.0**
