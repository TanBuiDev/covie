# CoVie — UI Design System Specification

> **Canonical component-level UI specification for CoVie V1**

**Document:** CoVie UI Design System Specification  
**Version:** 1.0  
**Date:** 2026-08-08  
**Status:** Design specification for review  
**Brand:** CoVie — Chợ Việt  
**Primary target:** Figma page `02 — Components`  
**Secondary target:** `packages/ui` + `apps/web` implementation

---

# 1. Purpose

Tài liệu này chuyển **CoVie Brand Foundation Specification V1.0** thành một hệ thống UI component có thể:

```text
design consistently in Figma
        ↓
assemble Buyer / Seller / Admin screens
        ↓
handoff without visual guesswork
        ↓
implement as reusable frontend components
```

Tài liệu này sở hữu:

```text
component taxonomy
component anatomy
variants
states
sizes
interaction behavior
responsive presentation
accessibility behavior
Figma naming
composition rules
screen-to-component mapping
```

Tài liệu này không tự thay đổi:

```text
Product scope
User Flow
IA / UI Flow
API behavior
Order/Need lifecycle
authorization
backend business rules
```

---

# 2. Source hierarchy

UI Design System phải tuân theo:

```text
Product Plan
      ↓
User Flow
      ↓
IA / UI Flow
      ↓
Brand Foundation Spec
      ↓
UI Design System Spec
      ↓
Figma Components
      ↓
Figma Screens
      ↓
Production UI
```

Technical Plan 09 được dùng để bảo đảm component system đủ cho implementation, accessibility, loading/empty/error states và responsive behavior.

---

# 3. Locked product UI constraints

Các constraint sau được xem là immutable cho UI V1:

```text
V1 là Web App / PWA
Consumer UI mobile-first
Seller Center usable trên mobile/tablet
Admin desktop-first

Guest được browse
Guest không có cart
Cart / Checkout / Order / Need personal action yêu cầu login

Không internal chat
Không booking calendar
Không payment verification UI
Không dispute-arbitration UI
Không native app

Product và Service không trộn trong cùng cart/order
Service quantity luôn = 1

Negotiated shipping không hiển thị 0đ
Phải hiển thị "Thỏa thuận với người bán"

Destructive/irreversible action phải explicit confirmation
COMPLETED confirmation phải nói rõ không thể hoàn tác

UI không in raw backend enum cho người dùng
UI không tự suy Order status/action
```

---

# 4. Locked Brand Foundation

Design System kế thừa:

```text
CoVie = Chợ Việt
Friendly Local Marketplace
Emerald Green
Inter
Balanced Density
Soft Elevation
Market Pin
Modern Vietnamese identity
```

Primary:

```text
Emerald 600 = #059669
```

Base radius:

```text
12 px
```

Base spacing:

```text
4 px
```

Default consumer page edge:

```text
16 px
```

---

# 5. System design philosophy

Mỗi component phải tối ưu cho 5 nguyên tắc:

```text
Clarity
Local context
Trust
Touch usability
Operational reuse
```

Một component chỉ được tạo khi nó:

```text
xuất hiện nhiều nơi
hoặc
mang interaction/state contract quan trọng
hoặc
cần visual consistency cao
```

Không componentize mọi `div`.

---

# 6. Layer model

CoVie UI Design System chia thành 5 lớp:

```text
L0 — Foundations
L1 — Primitives
L2 — Controls
L3 — Patterns
L4 — Domain Components
```

## L0 — Foundations

```text
Color
Typography
Spacing
Radius
Elevation
Motion
Grid
Iconography
```

## L1 — Primitives

```text
Text
Icon
Divider
Surface
Avatar
Badge
Skeleton
```

## L2 — Controls

```text
Button
Icon Button
Input
Textarea
Select
Checkbox
Radio
Switch
Tabs
Chip
Stepper
```

## L3 — Patterns

```text
App Bar
Bottom Navigation
Sidebar
Search Bar
Filter Bar
Modal
Bottom Sheet
Dialog
Toast
Banner
Empty State
Error State
Pagination
Data Table
```

## L4 — Domain Components

```text
Product Card
Merchant Card
Location Card
Cart Item
Cart Group
Price Block
Shipping Method
Checkout Summary
Order Card
Order Timeline
Order Action Panel
Need Card
Need Response Card
Review Card
Reputation Summary
Notification Item
Seller Metric Card
Inventory Row
Moderation Queue Row
Audit Log Row
```

---

# 7. Figma page structure

Target page:

```text
02 — Components
```

Recommended section order:

```text
00 Component Index

01 Primitives
02 Actions
03 Inputs
04 Selection
05 Navigation
06 Search & Filters
07 Feedback
08 Overlay
09 Data Display
10 Marketplace
11 Cart & Checkout
12 Orders
13 Needs
14 Trust
15 Seller
16 Admin
17 Responsive Examples
18 Accessibility States
99 Deprecated
```

---

# 8. Figma naming rule

Canonical naming pattern:

```text
Category/Component
Category/Component/Part
```

Examples:

```text
Action/Button
Action/IconButton

Input/TextField
Input/SearchField
Input/Textarea

Navigation/BottomNav
Navigation/AppBar
Navigation/Sidebar

Marketplace/ProductCard
Order/OrderCard
Need/NeedCard
```

Variant properties phải mô tả behavior, không mô tả canvas position.

Good:

```text
Hierarchy=Primary
Size=LG
State=Default
Icon=Leading
```

Bad:

```text
Green
Big
Left
Version2
```

---

# 9. Component property convention

Recommended common Figma properties:

```text
Size
State
Hierarchy
Tone
Selected
Icon
Orientation
Density
```

Không phải component nào cũng cần tất cả.

---

# 10. Common state vocabulary

Canonical component state values:

```text
Default
Hover
Pressed
Focus
Disabled
Loading
Selected
Error
Success
```

Dùng đúng subset cần thiết.

Không tạo variant:

```text
State=Normal
State=Standard
State=Regular
```

song song với `Default`.

---

# 11. Size vocabulary

Canonical UI sizes:

```text
SM
MD
LG
```

Optional:

```text
XS
XL
```

chỉ khi thực sự cần.

Consumer control default:

```text
LG mobile
MD desktop
```

---

# 12. Primitive — Text

Component/style family:

```text
Typography/*
```

Không cần biến từng text layer thành Figma component.

Use styles:

```text
Display/LG
Display/SM
Heading/H1
Heading/H2
Heading/H3
Title/MD
Body/LG
Body/MD
Body/Strong
Label/MD
Caption/MD
Caption/Strong
```

---

# 13. Primitive — Icon

Canonical source:

```text
một rounded outline icon family duy nhất
```

Recommended implementation family:

```text
Lucide-style rounded outline
```

Custom icons:

```text
CoVie Market Pin
special domain icon only when base family lacks semantic fit
```

Sizes:

```text
16
20
24
32
48
```

---

# 14. Primitive — Divider

Component:

```text
Primitive/Divider
```

Variants:

```text
Orientation=Horizontal|Vertical
Tone=Subtle|Default
```

Default:

```text
1 px
Neutral 200
```

Do not use decorative divider excessively in mobile card stacks.

---

# 15. Primitive — Surface

Component:

```text
Primitive/Surface
```

Properties:

```text
Elevation=0|1|2|3
Tone=Default|Subtle|BrandSubtle
Padding=None|SM|MD|LG
Radius=None|SM|MD|LG
```

Use this as conceptual base for Cards/Popovers; implementation may use utility primitives instead of rendering a generic `Surface` component everywhere.

---

# 16. Primitive — Avatar

Component:

```text
Identity/Avatar
```

Variants:

```text
Size=XS|SM|MD|LG|XL
Content=Image|Initials|Fallback
Status=None|OnlineLike|Warning
```

V1 does not imply realtime online presence.

If `Status=OnlineLike` is not backed by product data:

```text
do not use it
```

Preferred sizes:

```text
24
32
40
48
64
```

---

# 17. Primitive — Badge

Component:

```text
Data/Badge
```

Purpose:

```text
small count
notification count
compact metadata
```

Not for domain status; use `StatusChip`.

Variants:

```text
Tone=Neutral|Brand|Error
Size=SM|MD
```

---

# 18. Primitive — Skeleton

Component:

```text
Feedback/Skeleton
```

Patterns:

```text
TextLine
Avatar
Thumbnail
Card
TableRow
```

Motion:

```text
subtle shimmer/pulse
```

Respect reduced-motion preference.

---

# 19. Action — Button

Component:

```text
Action/Button
```

Properties:

```text
Hierarchy=Primary|Secondary|Tertiary|Destructive
Size=SM|MD|LG
State=Default|Hover|Pressed|Focus|Disabled|Loading
Icon=None|Leading|Trailing
Width=Hug|Fill
```

---

# 20. Button sizing

## LG — Mobile default

```text
Height: 48
Horizontal padding: 20
Gap: 8
Radius: 12
Label: 14/20 Semi Bold
Icon: 20
```

## MD — Desktop/default compact

```text
Height: 40
Horizontal padding: 16
Gap: 8
Radius: 12
Label: 14/20 Semi Bold
Icon: 18–20
```

## SM

```text
Height: 36
Horizontal padding: 12
Gap: 6
Radius: 10–12
Label: 13/18 Semi Bold
Icon: 16
```

---

# 21. Button hierarchy

## Primary

```text
BG Emerald 600
Text White
Hover Emerald 700
Pressed Emerald 800
```

## Secondary

```text
BG White
Border Neutral 200
Text Neutral 900
```

## Tertiary

```text
BG Transparent
Text Neutral 700
```

## Destructive

```text
BG semantic Error
Text White
```

Never style destructive as emerald.

---

# 22. Button loading

Loading button:

```text
preserve width
disable repeated click
show spinner
retain semantic label when possible
```

Example:

```text
Đang đặt đơn...
```

Do not turn label into only spinner for primary submit if width permits.

---

# 23. Action — Icon Button

Component:

```text
Action/IconButton
```

Properties:

```text
Hierarchy=Ghost|Secondary|Primary|Destructive
Size=SM|MD|LG
State=...
```

Hit target:

```text
>= 44 × 44 mobile
```

Visible icon may be:

```text
20–24 px
```

Every icon-only button requires accessible label.

---

# 24. Input — Text Field

Component:

```text
Input/TextField
```

Anatomy:

```text
Label
Control
Leading optional
Value/Placeholder
Trailing optional
Helper/Error
```

Properties:

```text
Size=MD|LG
State=Default|Hover|Focus|Filled|Disabled|Error
LeadingIcon=True|False
TrailingAction=True|False
Required=True|False
```

---

# 25. Text Field sizing

## LG mobile

```text
Control height: 48
Radius: 12
Horizontal padding: 14–16
```

## MD desktop

```text
Control height: 40
Radius: 12
Horizontal padding: 12–14
```

Textarea:

```text
minimum height: 104–120
```

---

# 26. Input validation

Validation hierarchy:

```text
field-level error
then form-level error when error is not attributable to a field
```

Error state must include:

```text
border
message
optional error icon
```

Never depend on red border alone.

---

# 27. Input — Search Field

Component:

```text
Input/SearchField
```

Anatomy:

```text
Search icon
Query
Clear action
optional filter action
```

Mobile minimum:

```text
48 px
```

Search placeholder should be contextual:

```text
Tìm món đồ, dịch vụ...
```

Not:

```text
Search...
```

---

# 28. Input — Select

Component:

```text
Input/Select
```

Desktop:

```text
popover/listbox
```

Mobile:

```text
bottom sheet preferred for long option sets
native-like select allowed for simple short lists
```

Do not design custom select that cannot keyboard navigate.

---

# 29. Input — Checkbox

Component:

```text
Selection/Checkbox
```

States:

```text
Unchecked
Checked
Indeterminate
Disabled
Focus
Error
```

Control:

```text
20 × 20 visual
>= 44 hit target mobile when standalone
```

---

# 30. Input — Radio

Component:

```text
Selection/Radio
```

Use for:

```text
single choice among visible alternatives
shipping method
pickup vs delivery when both available
```

Do not use segmented control when labels are long/multiline.

---

# 31. Input — Switch

Component:

```text
Selection/Switch
```

Use only for:

```text
immediate binary settings
```

Not for destructive or high-impact decisions that require confirmation.

---

# 32. Selection — Chip

Component:

```text
Selection/Chip
```

Properties:

```text
Type=Filter|Choice|Tag
State=Default|Selected|Disabled
Icon=None|Leading|Trailing
```

Sizing:

```text
28–32 px high
full radius
12–13 px label
```

---

# 33. Selection — Segmented Control

Component:

```text
Selection/SegmentedControl
```

Use for:

```text
2–4 compact mutually exclusive views
```

Example:

```text
Sản phẩm | Dịch vụ
```

Only when the underlying UX requires a mode switch.

Do not use it to replace primary navigation.

---

# 34. Selection — Quantity Stepper

Component:

```text
Commerce/QuantityStepper
```

Anatomy:

```text
Minus
Quantity
Plus
```

Rules:

```text
Product may use quantity
Service quantity always 1
```

Do not show this component for Service if there is no allowed quantity change.

---

# 35. Navigation — Mobile App Bar

Component:

```text
Navigation/AppBarMobile
```

Canonical marketplace anatomy:

```text
CoVie / Market Pin logo
Location trigger
Cart action + count
```

Optional page anatomy:

```text
Back
Title
Trailing action
```

Properties:

```text
Type=Marketplace|Page|Modal
Sticky=True|False
```

---

# 36. Marketplace App Bar behavior

Location trigger must expose current area in human language.

Example:

```text
S5.03
Vinhomes Grand Park
```

Do not show coordinates.

Cart count:

```text
small badge
only if > 0
```

Guest cart interaction:

```text
routes to login/register
```

No guest cart state is represented.

---

# 37. Navigation — Bottom Navigation

Component:

```text
Navigation/BottomNav
```

Canonical items:

```text
Trang chủ
Khám phá
Cần mua
Đơn
Tôi
```

Properties:

```text
ActiveItem=...
AuthState=Guest|User
```

Guest can see navigation but protected destination triggers login when appropriate.

---

# 38. Bottom Navigation layout

Recommended:

```text
5 equal items
height ~64 + safe-area
icon 24
label 11–12
active Emerald
inactive Neutral 500
```

Sticky CTA must never hide behind bottom navigation/safe area.

---

# 39. Navigation — Seller Sidebar / Tabs

Component families:

```text
Navigation/SellerSidebar
Navigation/SellerMobileNav
```

Canonical Seller sections:

```text
Tổng quan
Đơn hàng
Danh mục
Điểm bán
Cài đặt
```

Additional need/inventory surfaces may appear within approved Seller IA/routes.

Same account:

```text
Buyer ↔ Seller Center
```

Do not create a visually unrelated “second app”.

---

# 40. Navigation — Admin Sidebar

Component:

```text
Navigation/AdminSidebar
```

Desktop-first items include:

```text
Dashboard
Users
Seller / Provider
Shops & Locations
Listings
Orders
Cần mua
Reports
Communities / Zones
Categories
Settings
Audit Log
```

Visual behavior:

```text
neutral
compact
clear hierarchy
Emerald only for active/navigation emphasis
```

---

# 41. Navigation — Tabs

Component:

```text
Navigation/Tabs
```

Variants:

```text
Style=Underline|Pill
Density=Comfortable|Compact
```

Prefer underline for Seller/Admin content sections.

Pill tabs are suitable for small consumer mode/filter groups.

---

# 42. Search — Search Bar

Pattern:

```text
Search/SearchBar
```

Composition:

```text
SearchField
Filter button
optional location context
```

Search must lead naturally to:

```text
results
empty state
Need CTA when appropriate
```

---

# 43. Search — Filter Bar

Component:

```text
Search/FilterBar
```

Uses:

```text
category
distance
fulfillment
price
type
```

Mobile:

```text
horizontal scroll chips
+ Filters button
```

Desktop:

```text
inline controls/sidebar based on screen
```

---

# 44. Search — Filter Sheet

Component:

```text
Search/FilterSheet
```

Mobile:

```text
bottom sheet
```

Anatomy:

```text
Title
Reset
filter groups
Apply CTA
```

Use sticky footer CTA if content scrolls.

---

# 45. Feedback — Status Chip

Component:

```text
Feedback/StatusChip
```

This is the canonical domain status component.

Properties:

```text
Tone=Neutral|Info|Success|Warning|Error
Size=SM|MD
Icon=True|False
```

Always show text.

Do not infer tone from backend enum name in frontend component itself; caller supplies presentation mapping.

---

# 46. Status copy safety

Known backend statuses must map to human Vietnamese.

Unknown status fallback:

```text
Trạng thái đã được cập nhật
```

No guessed action buttons.

---

# 47. Feedback — Alert

Component:

```text
Feedback/Alert
```

Variants:

```text
Tone=Info|Success|Warning|Error
Action=None|TextButton|Button
Dismissible=True|False
```

Use for inline contextual messages.

---

# 48. Feedback — Warning Banner

Component:

```text
Feedback/WarningBanner
```

Use for:

```text
completion-rate warning
important workflow warning
deadline risk
```

Never use warning banner for routine informational copy.

---

# 49. Feedback — Toast

Component:

```text
Feedback/Toast
```

Use for:

```text
non-blocking success
minor failure
copy/action confirmation
```

Do not use Toast as only communication for:

```text
destructive confirmation
critical form errors
irreversible completion
```

---

# 50. Feedback — Empty State

Component:

```text
Feedback/EmptyState
```

Anatomy:

```text
Icon/Illustration
Title
Description
Primary action optional
Secondary action optional
```

Variants:

```text
Context=Generic|Search|Orders|Needs|Seller|Notifications
```

Search empty may include:

```text
Đăng nhu cầu
```

when product flow allows it.

---

# 51. Feedback — Error State

Component:

```text
Feedback/ErrorState
```

Anatomy:

```text
Title
plain-language explanation
Retry
optional alternate action
```

Do not expose:

```text
raw stack trace
HTTP code
backend enum
```

to consumer users.

---

# 52. Feedback — Loading State

Component:

```text
Feedback/LoadingState
```

Presentation preference:

```text
Skeleton for content
Spinner for bounded action
Progress label for submit
```

---

# 53. Overlay — Confirm Dialog

Component:

```text
Overlay/ConfirmDialog
```

Required for:

```text
destructive action
irreversible action
COMPLETED confirmation
```

Anatomy:

```text
Title
Impact explanation
Cancel
Confirm
```

`COMPLETED` confirmation must explicitly say:

```text
hành động không thể hoàn tác
```

---

# 54. Overlay — Modal

Component:

```text
Overlay/Modal
```

Desktop preferred for:

```text
focused form
preview
medium complexity task
```

Avoid deep navigation inside modal.

---

# 55. Overlay — Bottom Sheet

Component:

```text
Overlay/BottomSheet
```

Mobile preferred for:

```text
filters
location chooser
short selection
contextual actions
```

Properties:

```text
Height=Hug|Medium|Full
Footer=None|StickyAction
```

---

# 56. Overlay — Popover

Component:

```text
Overlay/Popover
```

Desktop uses:

```text
dropdown actions
compact filters
context menus
```

Must support outside click + Escape + keyboard focus return.

---

# 57. Data — Price

Component:

```text
Commerce/Price
```

Variants:

```text
Size=SM|MD|LG
State=Standard|Discounted
```

Rules:

```text
vi-VN currency display
no business arithmetic in component
```

Negotiated shipping is not represented as price `0 ₫`.

---

# 58. Data — Metadata Row

Component:

```text
Data/MetadataRow
```

Use for:

```text
distance
location
fulfillment
time
seller context
```

Anatomy:

```text
icon optional
label
value
```

Useful in cards/details to avoid ad hoc alignment.

---

# 59. Data — Rating

Component:

```text
Trust/Rating
```

Anatomy:

```text
Star
Score
Count optional
```

Example:

```text
4,8 · 96 đánh giá
```

Do not render reputation completion rate as star rating.

---

# 60. Data — Pagination

Component:

```text
Data/Pagination
```

Desktop:

```text
page/cursor-friendly visual navigation as UX requires
```

Consumer mobile:

```text
prefer infinite/load-more presentation if approved by implementation
```

The component must not assume backend offset pagination.

---

# 61. Data — Table

Component:

```text
Data/Table
```

Primary:

```text
Seller desktop
Admin
```

Properties:

```text
Density=Comfortable|Compact
Selection=None|Single|Multi
StickyHeader=True|False
```

Admin table must support horizontal scrolling without clipping row actions.

---

# 62. Data — Table Row Actions

Component:

```text
Data/RowActionMenu
```

Use:

```text
3-dot action
```

High-frequency primary action may remain directly visible.

Destructive menu action opens ConfirmDialog.

---

# 63. Marketplace — Product Card

Component:

```text
Marketplace/ProductCard
```

Anatomy priority:

```text
Image
Title
Price
Distance / location
Merchant context
Fulfillment cue
optional small trust cue
```

Variants:

```text
Layout=Grid|List
Type=Product|Resale|Service
Image=True|Fallback
```

---

# 64. Product Card visual rules

Grid mobile:

```text
2-column supported when content remains readable
```

List:

```text
horizontal image + body
```

Avoid more than:

```text
2 simultaneous badges
```

Primary card action:

```text
open detail
```

Do not overload card with full checkout controls.

---

# 65. Marketplace — Merchant Card

Component:

```text
Marketplace/MerchantCard
```

Anatomy:

```text
Logo/avatar
Merchant name
Location
Distance
Reputation summary
Fulfillment/service cue
```

Optional:

```text
open/availability presentation only if product data supports it
```

Do not invent “Đang mở cửa” without backend/source data.

---

# 66. Marketplace — Location Card

Component:

```text
Marketplace/LocationCard
```

Uses:

```text
merchant locations
pickup selection
seller management
```

Anatomy:

```text
Name
Address
Area
Delivery/Pickup capabilities
Status
```

---

# 67. Marketplace — Category Tile

Component:

```text
Marketplace/CategoryTile
```

Anatomy:

```text
Icon/image
Category label
```

Designed for fast mobile scanning.

Do not make Vietnamese identity dependent on culturally themed category icons.

---

# 68. Marketplace — Listing Header

Component:

```text
Marketplace/ListingHeader
```

For detail pages:

```text
Title
Type
Price
Merchant
Location/distance
Trust cue
```

Action area remains a separate purchase pattern.

---

# 69. Commerce — Fulfillment Option

Component:

```text
Commerce/FulfillmentOption
```

Variants:

```text
Type=Delivery|Pickup|ServiceCoordination
State=Default|Selected|Unavailable
```

Unavailable reason must be visible.

Frontend only shows Pickup if backend says eligible.

---

# 70. Commerce — Shipping Method

Component:

```text
Commerce/ShippingMethod
```

States:

```text
KnownPrice
Negotiated
Unavailable
```

Negotiated display:

```text
Thỏa thuận với người bán
```

Never:

```text
0 ₫
```

---

# 71. Commerce — Cart Item

Component:

```text
Commerce/CartItem
```

Anatomy:

```text
Thumbnail
Title
Variant
Unit price
Quantity control if applicable
Line amount
Remove
availability/error inline state
```

For Service:

```text
no editable quantity
```

---

# 72. Commerce — Cart Group

Component:

```text
Commerce/CartGroup
```

Represents:

```text
one MerchantLocation cart
```

Anatomy:

```text
Merchant/location header
CartItems
subtotal
checkout CTA
```

Multiple carts can coexist.

Checkout one cart must visually not imply other carts are deleted.

---

# 73. Commerce — Cart Overview

Pattern:

```text
Commerce/MultiCartOverview
```

Composition:

```text
CartGroup summary × N
```

Primary action belongs per cart/location.

No multi-seller single checkout UI.

---

# 74. Commerce — Checkout Section

Component:

```text
Checkout/Section
```

Anatomy:

```text
Title
Summary/action link
Content
```

Uses:

```text
Address
Fulfillment
Items
Notes
Summary
```

---

# 75. Commerce — Checkout Summary

Component:

```text
Checkout/Summary
```

Displays backend preview values.

Anatomy:

```text
Items subtotal
Shipping known/negotiated
Known total
Important note
Commit CTA
```

UI must not recompute authoritative totals.

---

# 76. Commerce — Checkout Commit Bar

Component:

```text
Checkout/CommitBar
```

Mobile:

```text
sticky above safe area / bottom nav conflict
```

Displays:

```text
known total
primary CTA
```

If shipping negotiated:

```text
label known amount clearly
do not imply final total
```

---

# 77. Commerce — Checkout Success

Pattern:

```text
Checkout/SuccessPanel
```

Anatomy:

```text
success icon
Order reference
status explanation
View Order CTA
Continue browsing
```

Do not imply seller confirmed when Order is only pending confirmation.

---

# 78. Order — Order Card

Component:

```text
Order/OrderCard
```

Anatomy:

```text
Order reference
Merchant/location
StatusChip
Item summary
Amount summary
Updated time
Contextual CTA
```

CTA is driven by backend-provided/approved presentation logic, not local state machine invention.

---

# 79. Order — Order Timeline

Component:

```text
Order/Timeline
```

Anatomy:

```text
event marker
human event title
timestamp
optional explanation
```

Events are append-only presentation.

Do not expose internal event code.

---

# 80. Order — Action Panel

Component:

```text
Order/ActionPanel
```

Shows only actions valid for current server state/permissions.

Examples may include:

```text
Confirm
Reject
Cancel
Request cancellation
Mark delivered
Ready for pickup
Confirm received/completed
Report issue
```

Component must not derive lifecycle by itself.

---

# 81. Order — Completion Confirmation

Pattern:

```text
Order/CompletionConfirm
```

Required copy includes:

```text
Bạn xác nhận giao dịch đã hoàn tất.
Sau khi xác nhận, trạng thái hoàn thành không thể hoàn tác.
```

Exact copy may be polished later but irreversibility must remain explicit.

---

# 82. Order — Issue Panel

Component:

```text
Order/IssuePanel
```

Only shown when server/product flow permits.

Anatomy:

```text
issue explanation
status
actions available
```

CoVie V1 does not imply admin arbitration.

---

# 83. Need — Need Card

Component:

```text
Need/NeedCard
```

Buyer-facing anatomy:

```text
Need title
Category/type
Area/distance context
Expiry
Response count
Status
```

Seller-facing variation may emphasize:

```text
matching relevance
buyer locality
response action
```

---

# 84. Need — Need Form

Pattern:

```text
Need/NeedForm
```

Core fields follow source flow.

Design requirements:

```text
clear expiry selection
structured category/type
location context
plain-language helper
```

Do not invent AI-matching configuration.

---

# 85. Need — Need Response Card

Component:

```text
Need/ResponseCard
```

Seller response must reference existing catalog item/LocationVariant.

Buyer anatomy:

```text
Merchant
Catalog item
Price
Location
Fulfillment
Response note if contract allows
Checkout CTA
```

Do not display arbitrary unstructured seller offer as equivalent to catalog response.

---

# 86. Need — Expiry Indicator

Component:

```text
Need/Expiry
```

Presentation states:

```text
Còn thời gian
Sắp hết hạn
Đã hết hạn
```

Countdown is presentation only.

At deadline:

```text
refetch
do not perform state transition
```

---

# 87. Trust — Review Summary

Component:

```text
Trust/ReviewSummary
```

Displays:

```text
rating
review count
optional distribution on detailed surface
```

No review CTA unless eligibility comes from approved server/API behavior.

---

# 88. Trust — Review Form

Component:

```text
Trust/ReviewForm
```

Anatomy:

```text
1–5 stars
optional comment
submit
```

Only available for eligible completed OrderItem.

If Review System disabled:

```text
do not show error banner
simply omit/disable relevant review affordance per product behavior
```

---

# 89. Trust — Reputation Summary

Component:

```text
Trust/ReputationSummary
```

Reputation and Review are separate.

Displays approved metrics such as:

```text
completion rate
warning if threshold reached
```

Do not expose detailed public transaction history.

---

# 90. Trust — Report Action

Component:

```text
Trust/ReportAction
```

Presentation:

```text
low-emphasis action
opens report form/dialog
```

Do not visually imply Report = transaction dispute arbitration.

---

# 91. Notification — Notification Item

Component:

```text
Notification/Item
```

Anatomy:

```text
Icon
Title
Body
Time
Unread indicator
```

SSE updates/refetches REST; UI component does not represent SSE transport directly.

---

# 92. Seller — Dashboard Metric

Component:

```text
Seller/MetricCard
```

Displays operational metrics only when supported by current APIs.

Anatomy:

```text
Label
Value
Trend/context optional
```

Do not invent revenue analytics not in V1.

---

# 93. Seller — Order Row

Component:

```text
Seller/OrderRow
```

Mobile:

```text
card/list row
```

Desktop:

```text
table-like row
```

Shows:

```text
Order reference
Buyer summary
Status
Amount
Time/deadline
Primary valid action
```

---

# 94. Seller — Catalog Item Row

Component:

```text
Seller/CatalogRow
```

Shows:

```text
Image
Title
Type
Location/offer summary
Price
Publish/moderation status
Actions
```

---

# 95. Seller — Inventory Row

Component:

```text
Seller/InventoryRow
```

Shows:

```text
Variant
stock on hand
reserved
available
status
action
```

Presentation must preserve invariant meaning:

```text
available = stock_on_hand - reserved
```

Frontend does not authoritatively recalculate inventory mutations.

---

# 96. Seller — Location Summary

Component:

```text
Seller/LocationSummary
```

Shows:

```text
Location name
Address/area
Pickup
Delivery
Coverage/config status
```

---

# 97. Seller — Need Match Row

Component:

```text
Seller/NeedMatchRow
```

Shows:

```text
Need
distance/area
expiry
response state
Respond CTA
```

One active response per MerchantLocation is business behavior, not component-local state.

---

# 98. Admin — Stat Card

Component:

```text
Admin/StatCard
```

Visual:

```text
compact
neutral
high legibility
```

Only display metrics backed by actual admin APIs.

---

# 99. Admin — Queue Row

Component:

```text
Admin/QueueRow
```

Generic composition for:

```text
moderation
reports
listing review
```

Anatomy:

```text
Entity
Reason/type
Status
Created time
Context
Actions
```

---

# 100. Admin — Entity Summary

Component:

```text
Admin/EntitySummary
```

Use in detail side panel/page:

```text
User
Merchant
Location
Listing
Need
Order read-only context
```

Admin must not receive force Order status control.

---

# 101. Admin — Settings Field

Component:

```text
Admin/SettingsField
```

Supports allowlisted system settings.

Anatomy:

```text
Setting name
Description
Value
Validation
Save state
```

High-impact setting changes may require ConfirmDialog.

---

# 102. Admin — Audit Row

Component:

```text
Admin/AuditRow
```

Displays:

```text
Actor
Action
Target
Timestamp
Metadata summary
```

Dense but readable.

---

# 103. Responsive density rules

## Buyer

```text
Mobile: Comfortable/Balanced
Desktop: Balanced
```

## Seller

```text
Mobile: Balanced
Tablet: Balanced
Desktop: Compact-Balanced
```

## Admin

```text
Desktop: Compact-Balanced
```

Do not globally shrink typography to create density.

Density comes from:

```text
spacing
row height
visible metadata
layout
```

---

# 104. Reference viewport matrix

Design + QA must cover:

```text
360 × 800
390 × 844
768 × 1024
1440 × 900
```

Optional additional visual review:

```text
430 mobile
1280 desktop
```

---

# 105. Mobile safe-area rule

Sticky bottom elements must account for:

```text
browser viewport
safe area inset
BottomNav
keyboard
```

Priority:

```text
content
then sticky CTA
then system safe area
```

No overlapping interaction targets.

---

# 106. Responsive card rule

Product grids:

```text
360: 2 columns only if card maintains readable price/title
otherwise 1 column/list
```

Desktop:

```text
adaptive columns
```

Never force fixed card width that creates horizontal consumer overflow.

---

# 107. Responsive Seller forms

Seller Catalog/Location forms:

```text
single-column mobile
two-column grouped desktop where fields remain semantically related
```

Tablet must remain fully usable.

---

# 108. Responsive Admin tables

Admin table:

```text
preserve columns required for decision
allow horizontal scroll
pin/keep action menu reachable where feasible
```

Do not collapse critical moderation context into inaccessible tooltip-only data.

---

# 109. Accessibility — Focus

Every interactive component requires visible focus.

Canonical:

```text
2 px Emerald 500 ring
2 px offset where supported
```

Focus should not be clipped by parent container.

---

# 110. Accessibility — Keyboard

Required desktop patterns:

```text
Tab navigation
Enter/Space activation
Escape closes overlay
focus trapped in Modal/Dialog
focus returns to trigger
arrow navigation where ARIA pattern requires it
```

---

# 111. Accessibility — Labeling

Icon-only buttons:

```text
accessible name required
```

Inputs:

```text
visible label preferred
```

Placeholder is not a label.

Status:

```text
text required
```

Color cannot be sole meaning.

---

# 112. Accessibility — Contrast

Target:

```text
WCAG 2.2 AA
```

Minimum baseline:

```text
normal text >= 4.5:1
large text >= 3:1
UI graphical boundary where applicable >= 3:1
```

Emerald combinations must be checked in actual Figma values before freeze.

---

# 113. Accessibility — Touch

Minimum:

```text
44 × 44 px
```

for mobile interactive target.

Visual icon/control may be smaller inside a larger hit area.

---

# 114. Accessibility — Reduced Motion

Components with:

```text
spinner
skeleton shimmer
sheet/modal animation
toast
```

must have reduced-motion-safe behavior.

---

# 115. Error handling contract

Component layer must distinguish:

```text
field error
form error
section read error
full-page read error
action mutation error
unknown state fallback
```

Do not use one generic error UI for every case.

---

# 116. Unknown enum/state rule

If backend returns unknown status/type:

```text
show safe generic presentation
do not infer valid actions
```

Canonical fallback:

```text
Trạng thái đã được cập nhật
```

Developer-facing logging may preserve raw value outside consumer copy.

---

# 117. Confirmation matrix

Explicit confirmation required for at least:

```text
destructive remove/delete when consequential
reject/cancel when workflow requires confirmation
irreversible COMPLETED
admin high-impact moderation/settings action
session revoke if destructive context warrants
```

Low-impact undoable interactions should not be over-confirmed.

---

# 118. Loading strategy matrix

## Page initial load

```text
Skeleton
```

## Small action

```text
Button loading
```

## Background refetch

```text
retain content
subtle updating state if necessary
```

## SSE invalidation

```text
refetch REST
do not flash full-page loader
```

---

# 119. Empty-state matrix

## Search

```text
No results
suggest adjust query/filter
Need CTA when applicable
```

## Cart

```text
No carts
Browse CTA
```

## Orders

```text
No orders
Browse CTA
```

## Needs

```text
No needs
Create Need CTA
```

## Notifications

```text
No notifications
no forced CTA required
```

## Seller Catalog

```text
No listings
Create listing CTA
```

---

# 120. P0 component coverage — Guest/Buyer

The system must cover:

```text
Home
Search
Category
Listing detail
Merchant detail
Login/Register
Onboarding
Cart overview
Cart detail
Checkout
Checkout success
Orders list
Order detail
Needs list
Need create
Need detail
Profile
Addresses
Notifications
Security sessions
```

---

# 121. P0 component coverage — Seller/Provider

Must cover:

```text
Seller dashboard
Orders
Order detail
Needs/matches
Catalog
Create/Edit catalog
Inventory
Locations
Location detail
Delivery config
Settings/profile surfaces
```

Provider uses same Seller Center visual system.

---

# 122. P0 component coverage — Admin

Must cover:

```text
Dashboard
Users
Seller/Provider
Shops/Locations
Listings
Orders read-only control plane context
Needs
Reports
Communities/Zones
Categories
Settings
Audit Log
```

---

# 123. Component-to-screen mapping — Home

Home uses:

```text
Navigation/AppBarMobile
Input/SearchField
Marketplace/CategoryTile
Marketplace/ProductCard
Marketplace/MerchantCard optional
Navigation/BottomNav
Feedback/Skeleton
Feedback/ErrorState
```

---

# 124. Component-to-screen mapping — Search

Search uses:

```text
Input/SearchField
Search/FilterBar
Search/FilterSheet
Marketplace/ProductCard
Feedback/EmptyState
Feedback/Skeleton
Navigation/BottomNav
```

Search empty may expose Need CTA.

---

# 125. Component-to-screen mapping — Listing Detail

Uses:

```text
Navigation/AppBarMobile
Marketplace/ListingHeader
Commerce/Price
Data/MetadataRow
Trust/Rating
Trust/ReputationSummary
Commerce/FulfillmentOption
Action/Button
Feedback/Alert
Overlay/BottomSheet as needed
```

---

# 126. Component-to-screen mapping — Cart

Uses:

```text
Commerce/MultiCartOverview
Commerce/CartGroup
Commerce/CartItem
Commerce/QuantityStepper
Commerce/Price
Feedback/Alert
Action/Button
```

---

# 127. Component-to-screen mapping — Checkout

Uses:

```text
Checkout/Section
Commerce/FulfillmentOption
Commerce/ShippingMethod
Checkout/Summary
Checkout/CommitBar
Input/*
Selection/*
Feedback/Alert
Overlay/ConfirmDialog when required
```

---

# 128. Component-to-screen mapping — Order Detail

Uses:

```text
Feedback/StatusChip
Order/Timeline
Order/ActionPanel
Order/IssuePanel
Trust/ReviewSummary/Form when eligible
Overlay/ConfirmDialog
Feedback/WarningBanner
```

---

# 129. Component-to-screen mapping — Need Detail

Uses:

```text
Need/NeedCard/detail header
Need/Expiry
Need/ResponseCard
Feedback/StatusChip
Action/Button
Feedback/EmptyState
```

---

# 130. Component-to-screen mapping — Seller Orders

Uses:

```text
Navigation/SellerSidebar or SellerMobileNav
Navigation/Tabs
Search/FilterBar
Seller/OrderRow
Feedback/StatusChip
Data/Pagination
Feedback/EmptyState
```

---

# 131. Component-to-screen mapping — Seller Catalog

Uses:

```text
Seller/CatalogRow
Search/FilterBar
Action/Button
Feedback/StatusChip
Overlay/ConfirmDialog
Input/* on create/edit
```

---

# 132. Component-to-screen mapping — Admin

Uses:

```text
Navigation/AdminSidebar
Admin/StatCard
Data/Table
Admin/QueueRow
Admin/EntitySummary
Feedback/StatusChip
Data/RowActionMenu
Overlay/ConfirmDialog
Feedback/Alert
```

---

# 133. Figma component documentation

Every important component master should include description:

```text
Purpose
When to use
When not to use
Key behavior
Accessibility requirement
Engineering mapping if known
```

Example:

```text
Order/ActionPanel

Purpose:
Displays server-authorized actions for an Order.

Do not:
derive Order lifecycle locally.

Accessibility:
Action labels must remain explicit; destructive actions use ConfirmDialog.
```

---

# 134. Variant explosion rule

Avoid Cartesian explosion.

Instead of:

```text
4 hierarchy
× 3 size
× 8 state
× 3 icon
× 2 width
= 576 variants
```

use:

```text
component properties
boolean icon slots
text properties
instance swap
nested subcomponents
```

Create only meaningful visual variants.

---

# 135. Slot composition rule

Prefer composable slots for complex domain components.

Example ProductCard slots:

```text
Media
Badge
Title
Price
Meta
Trust
```

But lock layout enough to keep marketplace consistency.

---

# 136. Design token usage

Components consume semantic tokens:

```text
semantic/action/primary
semantic/text/primary
semantic/text/secondary
semantic/bg/surface
semantic/border/default
semantic/status/*
radius/*
space/*
```

Avoid raw `Emerald 600` inside component except where semantic variable is deliberately bound to it.

---

# 137. Engineering naming mapping

Recommended code families:

```text
Button
IconButton
TextField
SearchField
Select
Checkbox
RadioGroup
Switch
Chip
Tabs
StatusChip
Alert
Toast
EmptyState
ErrorState
ConfirmDialog
BottomSheet
ProductCard
MerchantCard
CartItem
OrderCard
NeedCard
```

Domain composites may live under feature folders rather than generic `packages/ui`.

---

# 138. Shared vs domain component boundary

`packages/ui` should favor:

```text
generic visual primitives
accessible controls
generic feedback/navigation primitives
```

Feature modules should own:

```text
OrderActionPanel
NeedResponseCard
CheckoutSummary with domain data
SellerInventoryRow
AdminModerationRow
```

Do not put backend-specific business semantics deep into generic UI library.

---

# 139. Consumer language rule

UI labels use simple Vietnamese, not backend enums.

Examples:

```text
PENDING_CONFIRMATION
→ Chờ người bán xác nhận

READY_FOR_PICKUP
→ Sẵn sàng nhận hàng
```

Exact full mapping remains implementation/presentation contract.

Do not expose enum strings.

---

# 140. Money presentation

Display:

```text
Intl.NumberFormat("vi-VN", ...)
```

Design should accommodate:

```text
long VND values
discount/old price
known subtotal vs negotiated shipping
```

Never parse displayed string back into arithmetic.

---

# 141. Time presentation

Use human copy:

```text
Còn 2 giờ
Sắp hết hạn
Đã tới hạn, đang cập nhật...
```

Provide absolute timestamp where user needs certainty.

---

# 142. Trust visualization rule

Trust evidence should be factual.

Good:

```text
4,8 · 96 đánh giá
Hoàn thành 97%
Cách bạn 850 m
```

Avoid:

```text
Siêu uy tín
Top seller
100% đảm bảo
```

without source support.

---

# 143. Brand accent budget

Consumer normal screen:

```text
75–85% neutral/white
10–15% emerald
5–10% semantic/other accent
```

Seller/Admin generally reduce decorative brand coverage further.

---

# 144. Soft Elevation rule

Default Card:

```text
White
Neutral 200 border
12 radius
Elevation 1
```

Nested card:

```text
prefer Elevation 0
```

Popover:

```text
Elevation 2
```

Modal:

```text
Elevation 3
```

---

# 145. Consumer card density

Card should not become mini detail page.

Primary scanning order:

```text
What is it?
How much?
Where?
Who sells it?
Can I get it?
```

Secondary metadata moves to detail.

---

# 146. Seller density

Seller surfaces prioritize:

```text
what needs action
current status
deadline/time
inventory/order context
```

Use tighter row/card spacing than Buyer while keeping touch usability.

---

# 147. Admin density

Admin prioritizes:

```text
decision context
auditability
queue scanning
status
safe action placement
```

Use less illustration, fewer brand-tinted surfaces and more structured tables/panels.

---

# 148. Component visual freeze process

A component is visually frozen only after:

```text
Default state reviewed
Interactive states reviewed
Mobile reviewed where applicable
Desktop reviewed where applicable
Accessibility reviewed
Dark-mode assumption not accidentally embedded
Content stress test reviewed
```

---

# 149. Content stress testing

Test component with:

```text
short Vietnamese label
long Vietnamese label
large price
long merchant name
missing image
zero/large count
unknown status fallback
error/helper copy
```

No design should depend on ideal demo text.

---

# 150. Internationalization resilience

V1 primary UI is Vietnamese.

Still avoid components that break when text expands by:

```text
~30–50%
```

Do not hard-code narrow button widths around current Vietnamese label.

---

# 151. Image fallback

Marketplace image components require:

```text
neutral fallback
category/product icon optional
no broken-image browser artifact
```

Fallback should not overpower real imagery.

---

# 152. Disabled-state rule

Disabled action must:

```text
remain legible
appear unavailable
not rely on opacity so low text fails readability
```

If user needs to know why:

```text
show reason/helper
```

rather than disabled control with no explanation.

---

# 153. Destructive-action hierarchy

Destructive actions generally:

```text
not primary page CTA
```

Use:

```text
secondary/tertiary destructive
or dialog confirm action
```

unless the screen's sole purpose is the destructive action.

---

# 154. Mobile modal preference

On small screens:

```text
BottomSheet for contextual choice
Full-screen route/sheet for long forms
Dialog only for short confirmation
```

Avoid large desktop-style centered form modal on 360 px width.

---

# 155. Form layout rule

Field grouping:

```text
identity
location
commerce
fulfillment
policy
```

Use section headings for long Seller/Admin forms.

Do not place 15 unrelated fields in one undifferentiated card.

---

# 156. Form action rule

Primary submit:

```text
one dominant action
```

Secondary:

```text
Cancel/Back
```

Avoid multiple emerald buttons with equal weight.

---

# 157. Notification count rule

Count badge:

```text
1–99
99+ if larger
```

Only if actual count exists.

Do not invent notification severity from count.

---

# 158. PWA install/offline UI

If exposed by Plan 09 implementation:

```text
PWA install prompt should be low-pressure
offline state should explain limited connectivity
```

Offline UI must not pretend mutation succeeded before server confirmation.

---

# 159. No frontend business source of truth

Design components may visually present:

```text
price
availability
status
eligibility
actions
```

but final values/actions come from server/API state.

No design spec should encourage frontend to:

```text
recompute shipping
force Order status
infer review eligibility
invent pickup eligibility
```

---

# 160. Figma construction standard

Reusable components:

```text
Auto Layout by default
semantic variables
text properties
boolean properties
instance swap
component descriptions
```

Avoid:

```text
absolute-positioning core text
detached copies
manual color hex duplication
```

---

# 161. Figma organization standard

Inside each section:

```text
Component title
Usage note
Main component set
State examples
Do/Don't example where useful
Responsive example if relevant
```

Master components should be separated from demo compositions.

---

# 162. Engineering handoff annotations

Important components should annotate:

```text
Figma component
Suggested code component
Props/state semantics
Domain ownership
Accessibility contract
```

Example:

```text
Figma: Feedback/StatusChip
Code: StatusChip
Ownership: packages/ui
Input: presentation tone + human label
Must not parse backend enum internally
```

---

# 163. Component QA checklist

For each component:

```text
[ ] Correct semantic tokens
[ ] Correct type style
[ ] Auto Layout
[ ] No unnecessary fixed width
[ ] Default state
[ ] Focus state
[ ] Disabled if applicable
[ ] Loading if applicable
[ ] Error if applicable
[ ] 44 px mobile target if interactive
[ ] Long text tested
[ ] Vietnamese copy tested
[ ] Component description added
```

---

# 164. Domain component QA checklist

Additional:

```text
[ ] Does not infer backend state
[ ] Does not show invalid action
[ ] Does not expose enum
[ ] Handles missing/unknown state
[ ] Handles server error
[ ] Handles loading/refetch
[ ] Responsive
```

---

# 165. Buyer screen readiness gate

Before Buyer P0 screen design begins:

```text
Button
IconButton
TextField
SearchField
Chip
AppBar
BottomNav
ProductCard
MerchantCard
StatusChip
Alert
EmptyState
ErrorState
Skeleton
BottomSheet
ConfirmDialog
Price
Rating
```

must have at least reviewed base variants.

---

# 166. Checkout readiness gate

Before checkout screen freeze:

```text
CartItem
CartGroup
QuantityStepper
FulfillmentOption
ShippingMethod
CheckoutSection
CheckoutSummary
CommitBar
Alert
ConfirmDialog
```

must be reviewed.

---

# 167. Order readiness gate

Before Order detail freeze:

```text
StatusChip
Timeline
ActionPanel
IssuePanel
WarningBanner
ConfirmDialog
ReviewSummary
```

must be reviewed.

---

# 168. Seller readiness gate

Before Seller Center screen freeze:

```text
Seller navigation
Tabs
Data table/list
OrderRow
CatalogRow
InventoryRow
LocationSummary
NeedMatchRow
StatusChip
Forms
ConfirmDialog
```

must be reviewed.

---

# 169. Admin readiness gate

Before Admin screen freeze:

```text
AdminSidebar
DataTable
RowActionMenu
QueueRow
EntitySummary
SettingsField
AuditRow
StatusChip
ConfirmDialog
Alert
```

must be reviewed.

---

# 170. Design System V1 non-goals

Do not build now:

```text
native iOS/Android component library
dark theme component variants
chat bubbles
video/live-commerce components
payment card/payment QR components
booking calendar
advanced data visualization suite
AI assistant components
loyalty/gamification
complex drag-and-drop builder
```

---

# 171. Design review sequence

Recommended review batches:

```text
Batch 1 — Primitives + Actions + Inputs
Batch 2 — Navigation + Search + Feedback + Overlays
Batch 3 — Marketplace + Commerce
Batch 4 — Orders + Needs + Trust
Batch 5 — Seller + Admin
```

Reviewing everything at once is discouraged.

---

# 172. Figma build order

Canonical build order:

```text
1. Foundations variables/styles
2. Button/IconButton
3. Input/selection controls
4. Navigation
5. Feedback/Overlay
6. Generic data display
7. Marketplace cards
8. Cart/Checkout components
9. Order components
10. Need components
11. Trust components
12. Seller components
13. Admin components
14. Responsive examples
15. Accessibility audit
```

---

# 173. Visual prototype order

After component review:

```text
1. Buyer Home
2. Search Results
3. Listing Detail
4. Cart
5. Checkout
6. Order Detail
7. Need Create/Detail
8. Seller Dashboard/Orders
9. Seller Catalog
10. Admin Queue/Detail
```

This sequence exercises the system from consumer primitives into operational surfaces.

---

# 174. Component source of truth

After design freeze:

```text
Brand Foundation
       ↓
Design System Spec
       ↓
Figma component master
       ↓
code component
```

A screen-local detached component does not become source of truth.

---

# 175. Change management

If a global UI pattern changes:

```text
update Design System Spec if contract changes
update Figma master
update affected screens
update code component
```

Do not patch 20 screens independently.

---

# 176. Proposed design freeze criteria

UI Design System V1 is ready for P0 screen production when:

```text
Foundation visually reviewed
Core components built
Consumer navigation built
Marketplace cards built
Commerce primitives built
Order/Need status patterns built
Seller/Admin base navigation built
WCAG AA checks pass for core combinations
Responsive examples pass
No unresolved component contract conflict
```

---

# 177. Open visual exploration intentionally deferred

The following remain visual exploration tasks, not missing specification:

```text
exact Market Pin vector
exact wordmark spacing
final icon family selection/optical tuning
final card image ratio per screen
final illustration artwork
micro-animation easing polish
```

They must stay within the approved Brand Foundation.

---

# 178. Canonical component inventory

## Foundations / primitives

```text
Typography styles
Icon
Divider
Surface
Avatar
Badge
Skeleton
```

## Actions

```text
Button
IconButton
```

## Inputs / selection

```text
TextField
SearchField
Textarea
Select
Checkbox
Radio
Switch
Chip
SegmentedControl
QuantityStepper
```

## Navigation

```text
AppBarMobile
BottomNav
SellerSidebar
SellerMobileNav
AdminSidebar
Tabs
```

## Search

```text
SearchBar
FilterBar
FilterSheet
```

## Feedback / Overlay

```text
StatusChip
Alert
WarningBanner
Toast
EmptyState
ErrorState
LoadingState
ConfirmDialog
Modal
BottomSheet
Popover
```

## Data

```text
Price
MetadataRow
Rating
Pagination
Table
RowActionMenu
```

## Marketplace

```text
ProductCard
MerchantCard
LocationCard
CategoryTile
ListingHeader
```

## Commerce

```text
FulfillmentOption
ShippingMethod
CartItem
CartGroup
MultiCartOverview
CheckoutSection
CheckoutSummary
CheckoutCommitBar
CheckoutSuccessPanel
```

## Orders

```text
OrderCard
OrderTimeline
OrderActionPanel
CompletionConfirm
IssuePanel
```

## Needs

```text
NeedCard
NeedForm
NeedResponseCard
NeedExpiry
```

## Trust / notification

```text
ReviewSummary
ReviewForm
ReputationSummary
ReportAction
NotificationItem
```

## Seller

```text
MetricCard
OrderRow
CatalogRow
InventoryRow
LocationSummary
NeedMatchRow
```

## Admin

```text
StatCard
QueueRow
EntitySummary
SettingsField
AuditRow
```

---

# 179. Final design principle

When deciding whether to add a new component:

```text
Does it reduce inconsistency?
Does it encode an important interaction contract?
Does it make Buyer/Seller/Admin reuse clearer?
```

If not:

```text
compose existing primitives
```

When deciding between visual cleverness and clarity:

```text
choose clarity
```

When deciding between generic marketplace styling and CoVie identity:

```text
use local context + Emerald + human language
not decorative Vietnamese clichés
```

---

# 180. Approval gate

This document is ready for user review.

After approval:

```text
Brand Foundation Spec = approved
UI Design System Spec = approved
        ↓
build Foundations in Figma
        ↓
build Components in Figma
        ↓
review component batches
        ↓
design P0 screens
```

No Figma component implementation should be treated as final until this specification is approved.

---

**End of CoVie UI Design System Specification V1.0**
