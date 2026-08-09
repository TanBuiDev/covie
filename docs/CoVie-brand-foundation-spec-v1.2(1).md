# CoVie — Brand Foundation Specification

> **Canonical visual and brand foundation for CoVie V1**

**Document:** CoVie Brand Foundation Specification  
**Version:** 1.2  
**Date:** 2026-08-09  
**Status:** Approved visual foundation — logo master locked; focus accessibility correction  
**Brand:** CoVie  
**Brand meaning:** **CoVie = Chợ Việt**  
**Primary use:** Figma Design System, Product UI, Marketing surfaces, Engineering implementation

---

# 1. Purpose

Tài liệu này định nghĩa nền tảng thương hiệu và visual language chính thức cho CoVie V1.

Nó là cầu nối giữa:

```text
Product positioning
      ↓
Brand identity
      ↓
Design foundations
      ↓
Figma Design System
      ↓
UI Components
      ↓
Buyer / Seller / Admin screens
      ↓
Production implementation
```

Tài liệu này không thay thế Product Plan, User Flow, IA/UI Flow, Database Design, API Design hoặc Technical Plans. Nó sở hữu các quyết định liên quan tới brand personality, logo direction, visual tone, colors, typography, spacing, radius, elevation, iconography, imagery, motion tone, brand voice, accessibility foundation và design token naming.

---

# 2. Brand definition

## 2.1 Brand name

Tên thương hiệu chính thức:

```text
CoVie
```

CoVie là viết tắt của:

```text
Chợ Việt
```

Tên thương hiệu phải luôn được viết **CoVie** trên các brand-facing surfaces. Không dùng `COVIE`, `Covie`, `coVie`, `covie` hoặc `Co Vie`, trừ technical identifier bắt buộc lowercase.

## 2.2 Brand idea

CoVie là:

> **Chợ Việt hiện đại — nơi người dùng tìm thấy hàng hóa, dịch vụ và nhu cầu gần mình trong một cộng đồng địa phương đáng tin cậy.**

Ba ý tưởng cốt lõi:

```text
CHỢ
→ nhiều người bán, nhiều nhu cầu, nhiều lựa chọn

VIỆT
→ gần gũi với cách người Việt mua, bán, trao đổi và hỗ trợ nhau

GẦN
→ ưu tiên tính hyperlocal, vị trí và cộng đồng quanh người dùng
```

---

# 3. Brand positioning

CoVie không được cảm nhận như một sàn thương mại điện tử xa cách, dashboard doanh nghiệp lạnh lẽo, app rao vặt cũ kỹ hoặc một sản phẩm “đậm chất truyền thống” theo kiểu trang trí văn hóa cliché.

CoVie phải được cảm nhận như:

```text
một khu chợ số hiện đại
sạch
dễ dùng
thân thiện
gần gũi
đáng tin
địa phương
có tổ chức
```

---

# 4. Locked design direction

| Dimension | Decision |
|---|---|
| Overall style | **Friendly Local Marketplace** |
| Primary color direction | **Emerald Green** |
| Typography direction | **Friendly Modern** |
| Density | **Balanced** |
| Card / surface style | **Soft Elevation** |
| Logo system | **Symbol + Wordmark** |
| Symbol direction | **Market Pin** |
| Vietnamese identity | **Modern Vietnamese / subtle cultural cue** |

Canonical shorthand:

```text
A / A1 / T1 / D2 / S2 / L1 / V2
```

---

# 5. Brand personality

## 5.1 Friendly

CoVie phải dễ tiếp cận, không quá corporate, không gây áp lực và không dùng jargon không cần thiết. UI ưu tiên clear labels, friendly empty states, helpful validation, soft corners và natural language.

## 5.2 Local

CoVie phải gợi cảm giác “quanh đây”, “gần bạn”, “người thật”, “địa điểm thật”, “cộng đồng thật”. Visual language nên ưu tiên location context, distance, neighborhood/area context, merchant location identity và community cues.

## 5.3 Trustworthy

Thân thiện không đồng nghĩa tùy tiện. CoVie phải thể hiện trạng thái, giá, nguồn bán, location, Order status, review/reputation và moderation rõ ràng. Tránh dark patterns, CTA gây hiểu lầm, semantic color sai nghĩa hoặc giấu thông tin quan trọng.

## 5.4 Modern

“Chợ Việt” không có nghĩa tái tạo hình ảnh chợ truyền thống theo kiểu hoài cổ. CoVie phải digital-first, mobile-first, clean, structured, fast-scanning và contemporary.

## 5.5 Human

CoVie phải cho cảm giác người đang mua từ người bán gần mình, người bán là một phần của cộng đồng và review/reputation có ý nghĩa thực. Không biến giao diện thành catalog vô danh hoặc dashboard thuần số liệu.

---

# 6. Vietnamese identity principle

Locked direction:

```text
V2 — Việt hiện đại
```

Bản sắc Việt được thể hiện qua brand meaning “Chợ Việt”, ngôn ngữ, cách tổ chức category gần với nhu cầu địa phương, community tone và một số cue hình học tinh tế trong logo.

Không mặc định dùng nón lá, tre, trống đồng, hoa sen, quốc kỳ, màu đỏ-vàng áp đảo hoặc font thư pháp, trừ campaign cụ thể thực sự cần.

---

# 7. Logo architecture

CoVie sử dụng:

```text
SYMBOL + WORDMARK
```

Canonical lockup:

```text
[Market Pin Symbol]  CoVie
```

---

# 8. Logo symbol concept

Locked concept:

```text
L1 — Market Pin
```

Symbol phải kết hợp hai ý:

```text
Location Pin
+
Market / Stall / Roof
```

Ý nghĩa:

```text
Pin
→ gần bạn
→ vị trí
→ hyperlocal

Market roof
→ chợ
→ gian hàng
→ mua bán
```

Modern Vietnamese cue có thể dùng một chi tiết hình học gợi mái chợ, mái hiên hoặc nhịp kiến trúc Việt hiện đại, nhưng không literal quá mức, không nhiều chi tiết và không phụ thuộc vào họa tiết nhỏ khó đọc khi thu icon.

---

# 9. Logo construction principles

Logo phải đọc được ở 16–24 px, nhận ra khi làm app icon, đẹp ở monochrome, không phụ thuộc gradient/shadow, không có đường quá mảnh và không có chi tiết mất khi favicon-size.

Recommended symbol geometry:

```text
rounded geometry
balanced negative space
simple roof silhouette
vertical pin orientation
optically centered inner mark
```

---

# 10. Logo variants

Tối thiểu cần có:

```text
Primary Horizontal
Symbol Only
Wordmark Only
Monochrome Dark
Monochrome Light
App Icon
Favicon
```

V1 không cần stacked ceremonial logo, complex seal, illustrated logo hoặc animated brand logo.

---

# 11. Logo clear space

Minimum clear space:

```text
x = approximate height of lowercase “o” in CoVie wordmark
clear space around logo >= x
```

Không đặt text, icon, border, photo edge hoặc container edge vào vùng clear space.

---

# 12. Minimum logo size

Recommended:

```text
Horizontal digital: minimum height 24 px
Symbol: minimum 20 × 20 px
Favicon: 16 × 16 px optimized variant
```

Nếu symbol mất chi tiết ở 16 px, dùng simplified small-size master.

---

# 13. Logo misuse

Không stretch, skew, rotate, thêm shadow không được duyệt, outline logo, thay đổi khoảng cách symbol/wordmark tùy ý, dùng combination thiếu tương phản hoặc đổi emerald sang green ngẫu nhiên.

---

# 14. Primary color system

Primary family:

```text
Emerald
```

Canonical **brand primary**:

```text
Emerald 600
#059669
```

Emerald 600 là màu nhận diện chính của CoVie, dùng cho brand symbol, selected/active brand emphasis và các bề mặt/điểm nhấn phù hợp. **Không dùng Emerald 600 làm nền cho primary button có chữ trắng cỡ UI thông thường**, vì tổ hợp này không đạt WCAG 2.2 AA cho normal text.

Approved accessibility correction:

```text
Brand primary:            Emerald 600 #059669
Primary action default:   Emerald 700 #047857
Primary action hover:     Emerald 800 #065F46
Primary action pressed:   Emerald 900 #064E3B
Primary action text:      White
```

White trên Emerald 700 có contrast khoảng `5.48:1`, đáp ứng baseline WCAG AA cho normal text.

---

# 15. Emerald scale

| Token | Hex | Suggested use |
|---|---:|---|
| Emerald 50 | `#ECFDF5` | light brand surface |
| Emerald 100 | `#D1FAE5` | selected/tinted background |
| Emerald 200 | `#A7F3D0` | subtle accent border |
| Emerald 300 | `#6EE7B7` | secondary decorative accent |
| Emerald 400 | `#34D399` | hover/light emphasis |
| Emerald 500 | `#10B981` | bright brand accent |
| Emerald 600 | `#059669` | **primary brand identity** |
| Emerald 700 | `#047857` | **primary action default**, dark brand emphasis |
| Emerald 800 | `#065F46` | primary action hover |
| Emerald 900 | `#064E3B` | primary action pressed / deep emerald |
| Emerald 950 | `#022C22` | very dark brand surface |

Primary interaction:

```text
Default:  Emerald 700
Hover:    Emerald 800
Pressed:  Emerald 900
Text:     White
Focus:    Emerald 600 + focus ring
Disabled: semantic disabled tokens
```

---

# 16. Neutral color system

CoVie sử dụng neutral hơi ấm nhẹ nhưng vẫn gần Slate/Gray để UI không bị lạnh.

| Token | Hex |
|---|---:|
| Neutral 0 | `#FFFFFF` |
| Neutral 25 | `#FCFCFD` |
| Neutral 50 | `#F8FAFC` |
| Neutral 100 | `#F1F5F9` |
| Neutral 200 | `#E2E8F0` |
| Neutral 300 | `#CBD5E1` |
| Neutral 400 | `#94A3B8` |
| Neutral 500 | `#64748B` |
| Neutral 600 | `#475569` |
| Neutral 700 | `#334155` |
| Neutral 800 | `#1E293B` |
| Neutral 900 | `#0F172A` |
| Neutral 950 | `#020617` |

---

# 17. Semantic colors

## Success

```text
Success background: #ECFDF5
Success border:     #A7F3D0
Success text/icon:  #047857
```

## Warning

```text
Warning background: #FFFBEB
Warning border:     #FDE68A
Warning text/icon:  #B45309
```

## Error

```text
Error background: #FEF2F2
Error border:     #FECACA
Error text/icon:  #B91C1C
```

## Info

```text
Info background: #EFF6FF
Info border:     #BFDBFE
Info text/icon:  #1D4ED8
```

---

# 18. Semantic surface tokens

Components không nên gọi raw palette trực tiếp nếu semantic token tồn tại.

```text
color/bg/canvas
color/bg/subtle
color/bg/surface
color/bg/surface-hover
color/bg/brand-subtle

color/text/primary
color/text/secondary
color/text/tertiary
color/text/disabled
color/text/inverse
color/text/brand
color/text/error
color/text/warning
color/text/success

color/border/default
color/border/subtle
color/border/strong
color/border/brand
color/border/focus
color/border/error

color/action/primary
color/action/primary-hover
color/action/primary-pressed
color/action/disabled
```

---

# 19. Recommended semantic mappings

```text
color/bg/canvas              = Neutral 50
color/bg/subtle              = Neutral 100
color/bg/surface             = Neutral 0
color/bg/surface-hover       = Neutral 50
color/bg/brand-subtle        = Emerald 50

color/text/primary           = Neutral 900
color/text/secondary         = Neutral 600
color/text/tertiary          = Neutral 500
color/text/disabled          = Neutral 400
color/text/inverse           = Neutral 0
color/text/brand             = Emerald 700

color/border/default         = Neutral 200
color/border/subtle          = Neutral 100
color/border/strong          = Neutral 300
color/border/brand           = Emerald 300
color/border/focus           = Emerald 600

color/action/primary         = Emerald 700
color/action/primary-hover   = Emerald 800
color/action/primary-pressed = Emerald 900
```

---

# 20. Color usage rules

Emerald không dùng cho mọi thứ. Normal Buyer screen nên hướng tới khoảng 75–85% neutral/white surfaces, 10–15% brand emerald và 5–10% semantic/other accents.

Emerald dùng để guide attention, show brand, show selected state và drive primary action; không fill mọi card, không làm mọi heading xanh, không làm mọi icon xanh và không thay semantic error/warning colors.

---

# 21. Typography

Primary typeface:

```text
Inter
```

Reasons: excellent UI legibility, Vietnamese support tốt, clean modern tone, weight range rộng, web-friendly và Figma-friendly.

Fallback stack:

```css
Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif
```

---

# 22. Typography personality

Typography phải clear, friendly, không quá geometric, không playful, không condensed và không decorative. CoVie V1 không cần display font riêng.

---

# 23. Typography scale

| Style | Size / Line | Weight | Use |
|---|---|---|---|
| Display LG | 40 / 48 | 700 | rare marketing hero |
| Display SM | 32 / 40 | 700 | major page hero |
| H1 | 28 / 36 | 700 | page title |
| H2 | 24 / 32 | 700 | major section |
| H3 | 20 / 28 | 600 | card/section title |
| Title | 18 / 26 | 600 | strong local title |
| Body LG | 16 / 24 | 400 | important body |
| Body | 14 / 20 | 400 | default UI text |
| Body Strong | 14 / 20 | 600 | emphasized UI text |
| Label | 13 / 18 | 600 | inputs/buttons/chips |
| Caption | 12 / 16 | 400 | metadata/help |
| Caption Strong | 12 / 16 | 600 | compact metadata |

---

# 24. Typography rules

Avoid font size dưới 12 px, body text mặc định màu Emerald, ALL CAPS cho label thường, hơn ba font weights trên cùng một screen hoặc bold quá mức.

Use:

```text
400 Regular
500 Medium where needed
600 Semi Bold
700 Bold
```

Primary UI preference:

```text
400 / 600 / 700
```

---

# 25. Numbers and commerce data

Giá nên dùng tabular numbers khi có thể, weight 600 hoặc 700, ký hiệu `₫` secondary nhưng rõ ràng.

Example:

```text
125.000 ₫
```

Distance/time metadata dùng format nhất quán:

```text
12 phút
850 m
2,4 km
```

---

# 26. Density model

Locked:

```text
D2 — Balanced
```

Buyer: comfortable, visual, touch-friendly, more whitespace.  
Seller: balanced, higher information density, touch-safe trên mobile.  
Admin: compact-balanced, data-focused, table hierarchy rõ.

---

# 27. Base spacing system

Base unit:

```text
4 px
```

| Token | Value |
|---|---:|
| space/0 | 0 |
| space/1 | 4 |
| space/2 | 8 |
| space/3 | 12 |
| space/4 | 16 |
| space/5 | 20 |
| space/6 | 24 |
| space/8 | 32 |
| space/10 | 40 |
| space/12 | 48 |
| space/16 | 64 |
| space/20 | 80 |
| space/24 | 96 |

Default UI rhythm:

```text
4 / 8 / 12 / 16 / 24 / 32 / 48
```

---

# 28. Spacing guidance

```text
icon ↔ label:           8 px
label ↔ helper:         4–8 px
field ↔ field:          16 px
card internal padding:  16 px
large card padding:     20–24 px
section gap mobile:     24–32 px
section gap desktop:    32–48 px
page edge mobile:       16 px
```

---

# 29. Radius system

Locked visual feel:

```text
Friendly Modern
```

| Token | Value | Use |
|---|---:|---|
| radius/xs | 4 | tiny badges |
| radius/sm | 8 | compact controls |
| radius/md | 12 | default card/input |
| radius/lg | 16 | modal/sheet/large card |
| radius/xl | 20 | hero/community surfaces |
| radius/full | 999 | pill/avatar |

Recommended defaults:

```text
Button: 12 px
Input: 12 px
Card: 12 px
Large card: 16 px
Modal: 16 px
Bottom sheet: 16 px top corners
Chip: full
Avatar: full
```

---

# 30. Surface style

Locked:

```text
S2 — Soft Elevation
```

Core principle:

```text
border first
shadow second
```

Default card:

```text
Background: White
Border: Neutral 200
Radius: 12
Shadow: subtle
```

---

# 31. Elevation system

## Elevation 0

```text
none
```

Use cho flat sections, tables, nested surfaces.

## Elevation 1

```text
0 1px 2px rgba(15, 23, 42, 0.05)
```

Use cho default cards.

## Elevation 2

```text
0 4px 12px rgba(15, 23, 42, 0.08)
```

Use cho popover, dropdown, sticky floating elements.

## Elevation 3

```text
0 12px 32px rgba(15, 23, 42, 0.12)
```

Use cho modal và major overlay.

Avoid heavy shadows.

---

# 32. Borders

Default `1 px`. Use Neutral 200 default, Neutral 300 stronger, Emerald 300 brand selection và semantic Error border. Tránh 2 px borders khắp nơi và dark card outlines.

---

# 33. Iconography

Recommended style:

```text
rounded outline icons
1.75–2 px visual stroke
simple geometry
friendly corners
```

Sizes:

```text
16 px compact
20 px default
24 px primary
32–48 px illustrative
```

---

# 34. Icon color

Default `Neutral 600`; active `Emerald 700`. Không mặc định tất cả icon là brand green. Semantic icons phải theo semantic colors.

---

# 35. Icon family rule

Chỉ dùng một icon family nhất quán. Không mix filled Material, thin Lucide, bold custom glyph và emoji trên cùng product surface. Custom Market Pin phải optically match icon family cơ sở.

---

# 36. Illustration direction

CoVie illustrations nên simple, warm, flat/lightly dimensional, community-oriented, clean và không cartoonish.

Allowed themes: local market, merchant stall, delivery/pickup, neighbors, products/services, location, community.

Avoid overly childish characters, hyper-detailed 3D, generic corporate blobs và traditional Vietnamese clichés.

---

# 37. Photography direction

Product photography: object rõ, natural light, true-to-item color, minimal clutter.  
Merchant photography: real location, storefront, human context, đủ sạch để tạo trust.  
Community photography: tự nhiên, phù hợp Vietnamese urban/suburban context, tránh stock-photo corporate posing.

---

# 38. Image radius

```text
thumbnail: 8–12
product card image: 12
hero image: 16
avatar: full
merchant logo: 12 or full depending asset
```

---

# 39. Layout principles

Primary Buyer experience là mobile-first.

Reference width:

```text
390 px
```

Validate thêm:

```text
360 px
430 px
```

---

# 40. Mobile grid

Recommended:

```text
4 columns
16 px page margin
12 px gutter
```

Cards có thể span 2 hoặc 4 columns tùy nội dung.

---

# 41. Desktop grid

Reference:

```text
1440 px
```

Content max-width:

```text
1200–1280 px
```

Grid:

```text
12 columns
24 px gutters
responsive outer margins
```

Seller/Admin có thể dùng operational canvas rộng hơn.

---

# 42. Touch target

Minimum interactive target:

```text
44 × 44 px
```

Preferred primary mobile control:

```text
48 px height
```

Compact desktop control có thể 36–40 px khi pointer-oriented và vẫn accessible.

---

# 43. Buttons

Primary button baseline:

```text
height: 48 mobile
height: 40 desktop standard
radius: 12
weight: 600
```

Primary: Emerald 700 + white text; hover Emerald 800; pressed Emerald 900.  
Secondary: white + Neutral 200 border + Neutral 900 text.  
Tertiary: transparent + Neutral 700 text.  
Destructive: red semantic.

Không dùng Emerald cho destructive action.

---

# 44. Inputs

Default:

```text
height: 48 mobile
radius: 12
border: Neutral 300
surface: White
```

Focus: Emerald 600 ring/border.  
Error: Error border + Error helper text.  
Placeholder: Neutral 400–500.

---

# 45. Cards

Default marketplace card:

```text
White surface
12 radius
1 px subtle border
Elevation 1
16 px padding
```

Product card ưu tiên image, title, price, location/distance và seller/reputation context khi cần. Tránh quá nhiều badges.

---

# 46. Chips and tags

Use chips cho filter, status, category, distance và fulfillment. Chip không thay body text.

Recommended:

```text
height 28–32
radius full
12–13 px label
```

---

# 47. Status colors

Status là semantic, không phải decoration.

```text
success/completed → green
attention/pending → amber
problem/cancelled/error → red
informational/in-progress → blue or neutral
```

Order status không dựa vào color alone. Luôn pair color + text; khi cần thêm icon.

---

# 48. Navigation visual language

Buyer mobile bottom navigation: simple, tối đa 5 items ưu tiên, icon + label, active = Emerald, inactive = Neutral.

Seller/Admin: operational navigation rõ ràng, ít playful hơn nhưng dùng cùng token system.

---

# 49. Motion personality

Motion phải fast, soft, confident, không playful.

```text
micro interaction: 120–180 ms
standard transition: 180–240 ms
overlay: 220–280 ms
```

Use ease-out khi enter, ease-in khi leave. Tránh bouncy spring everywhere, 500ms transitions và decorative motion chặn hành động.

---

# 50. Loading behavior

Prefer skeleton cho content surfaces, spinner cho action nhỏ và button progress cho submit. Không blank toàn màn hình hoặc full-screen spinner cho mọi request.

---

# 51. Empty states

Tone: helpful, short, actionable.

Structure:

```text
simple icon/illustration
title
one-sentence explanation
primary next action
```

Avoid sad hoặc blame-oriented language.

---

# 52. Error states

Error message trả lời được:

```text
What happened?
What can user do next?
```

Example:

```text
Không thể tải danh sách sản phẩm.
Thử lại hoặc kiểm tra kết nối của bạn.
```

Không dùng `Error 500` hoặc `Something went wrong!!!` ngoài diagnostic surface dành cho Admin/Dev.

---

# 53. Brand voice

Voice:

```text
thân thiện
ngắn gọn
rõ ràng
tôn trọng
thực tế
```

CoVie nên giống một helpful local marketplace, không giống ngân hàng, cổng hành chính, meme account hay pushy sales bot.

---

# 54. Vietnamese microcopy

Prefer:

```text
Mua ngay
Thêm vào giỏ
Xem cửa hàng
Gần bạn
Nhận tại cửa hàng
Giao tận nơi
Tìm quanh đây
Đăng nhu cầu
```

Tránh từ ngữ quá formal khi tiếng Việt đời thường rõ hơn.

---

# 55. Pronouns and address

Default product language tránh đại từ không cần thiết.

Prefer:

```text
Đơn hàng của bạn
Cửa hàng của bạn
Bạn muốn nhận hàng bằng cách nào?
```

Avoid overly casual như `bạn ơi`, `nè`, `nhéee` trong product UI chuẩn.

---

# 56. Trust microcopy

Trust information phải specific, verifiable, không exaggerated.

Good:

```text
Cách bạn 850 m
Đã hoàn thành 124 đơn
4,8 từ 96 đánh giá
```

Avoid:

```text
Siêu uy tín
Tốt nhất khu vực
100% đáng tin
```

nếu không có bằng chứng.

---

# 57. Accessibility baseline

CoVie V1 target:

```text
WCAG 2.2 AA
```

Foundational rules:

```text
normal text contrast >= 4.5:1
large text contrast >= 3:1
interactive UI boundary/graphic contrast >= 3:1 where applicable
keyboard focus visible
touch target >= 44 px mobile
no critical meaning by color alone
```

---

# 58. Focus state

Canonical focus style:

```text
2 px Emerald 600 ring
2 px offset where supported
```

Focus phải rõ trên white, neutral và brand-tinted surfaces.

---

# 59. Dark mode

V1 decision:

```text
Dark mode is not a required design baseline unless Product scope explicitly adds it.
```

Vẫn build semantic tokens để có thể thêm dark mode sau này mà không redesign component APIs.

---

# 60. Responsive behavior

Design reference breakpoints:

```text
Mobile:       < 640
Tablet:       640–1023
Desktop:      1024–1439
Wide desktop: >= 1440
```

Đây là design references, không nhất thiết là exact CSS breakpoints.

---

# 61. Buyer visual behavior

Buyer ưu tiên discovery, visual browsing, nearby context, clear price, clear merchant, clear fulfillment và quick action. Buyer có thể dùng imagery lớn hơn, whitespace nhiều hơn và brand-tinted section nhẹ.

---

# 62. Seller visual behavior

Seller ưu tiên inventory, orders, catalog, location, status và actions. Dùng cùng brand foundation nhưng ít decorative branding hơn, structured sections hơn và density cao hơn.

---

# 63. Admin visual behavior

Admin ưu tiên risk, moderation, settings, user/merchant context, auditability và decision clarity. Admin không được trông như consumer shopping surface.

Use neutral surfaces, clear tables, strong hierarchy, semantic status; Emerald chủ yếu cho navigation và safe primary actions.

---

# 64. Brand consistency across roles

Tất cả role share color tokens, type system, radius, spacing, icons, focus states và semantic colors.

```text
Buyer  = friendliest
Seller = operational
Admin  = densest / most neutral
```

---

# 65. Figma variable collections

Recommended collections:

```text
01 Primitive Colors
02 Semantic Colors
03 Spacing
04 Radius
05 Sizing
06 Typography
07 Elevation
08 Motion
```

---

# 66. Primitive color naming

```text
color/emerald/50
color/emerald/100
...
color/emerald/950

color/neutral/0
color/neutral/25
...
color/neutral/950
```

Không dùng `green1`, `green2`, `lightgray`, `gray-dark-ish`.

---

# 67. Semantic token naming

```text
semantic/bg/canvas
semantic/bg/surface
semantic/bg/subtle
semantic/bg/brand-subtle

semantic/text/primary
semantic/text/secondary
semantic/text/tertiary
semantic/text/inverse
semantic/text/brand

semantic/border/default
semantic/border/subtle
semantic/border/brand
semantic/border/focus

semantic/action/primary
semantic/action/primary-hover
semantic/action/primary-pressed

semantic/status/success/bg
semantic/status/success/text
semantic/status/warning/bg
semantic/status/error/bg
```

---

# 68. Spacing variable naming

```text
space/0
space/1
space/2
space/3
space/4
space/5
space/6
space/8
space/10
space/12
space/16
space/20
space/24
```

---

# 69. Radius variable naming

```text
radius/xs
radius/sm
radius/md
radius/lg
radius/xl
radius/full
```

---

# 70. Component naming direction

```text
Button/Primary
Button/Secondary
Button/Tertiary
Button/Destructive

Input/Text
Input/Search
Input/Textarea

Card/Product
Card/Merchant
Card/Order
Card/Need

Navigation/Bottom
Navigation/Sidebar

Feedback/Alert
Feedback/Toast
Feedback/EmptyState
```

Không dùng `Button 2`, `Card copy 7`, `New component`.

---

# 71. Component state model

Where relevant:

```text
default
hover
pressed
focus
disabled
loading
error
selected
```

Không tạo variant chỉ vì Figma hỗ trợ variant.

---

# 72. Auto Layout rule

Reusable components dùng Auto Layout ở mọi nơi children có structural relationship. Chỉ dùng absolute positioning cho intentional overlays, badges, floating affordances, media decoration.

---

# 73. Foundation page structure in Figma

Page:

```text
01 — Foundations
```

Recommended sections:

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

---

# 74. Component page structure

Page:

```text
02 — Components
```

Recommended sequence:

```text
Primitives
Actions
Inputs
Navigation
Cards
Commerce
Status
Feedback
Overlay
Data Display
Seller
Admin
```

---

# 75. Design QA checklist

```text
[ ] Works at mobile scale
[ ] Works at desktop scale
[ ] Accessible contrast
[ ] Does not overuse Emerald
[ ] Feels Friendly Local Marketplace
[ ] Does not look culturally cliché
[ ] Buyer can be warm
[ ] Seller can be operational
[ ] Admin can be neutral/compact
[ ] Token can be implemented in CSS/design system
```

---

# 76. Logo QA checklist

```text
[ ] Symbol communicates place + market
[ ] Works as app icon
[ ] Works at 16–24 px
[ ] Works monochrome
[ ] Roof cue is subtle
[ ] Does not resemble generic map pin too closely
[ ] Does not resemble restaurant-only icon
[ ] Does not overuse Vietnamese cultural symbols
[ ] Wordmark reads clearly as “CoVie”
```

---

# 77. Color QA checklist

```text
[ ] Primary CTA contrast passes
[ ] Primary hover visibly differs
[ ] Error cannot be mistaken for brand state
[ ] Success is not identical to primary without context
[ ] Neutral hierarchy is readable
[ ] Disabled state is visibly disabled but legible
```

Vì Emerald vừa là brand color vừa thường gợi success, success phải dựa trên context + icon/text, không chỉ green.

---

# 78. Brand do / don't

## Do

```text
use white space
use Emerald intentionally
show nearby/local context
use soft but restrained elevation
write short Vietnamese microcopy
surface trust information clearly
use real merchant/product imagery
```

## Don't

```text
turn every surface green
use red/yellow as default “Vietnamese” branding
use traditional motifs as decoration
use giant shadows
use overly rounded playful UI
use too many badges
use marketing claims as trust evidence
```

---

# 79. Canonical visual summary

CoVie phải visually feel like:

> **Một khu chợ số Việt Nam hiện đại, sạch, gần gũi và đáng tin — thân thiện như một ứng dụng cộng đồng, nhưng đủ cấu trúc để vận hành thương mại thật.**

Visual formula:

```text
White / Neutral surfaces
+
Emerald 600 brand
+
Inter
+
12–16 px radius
+
soft borders
+
subtle elevation
+
clear local/location cues
+
Vietnamese language and community context
```

---

# 80. Logo master lock and remaining open brand work

Logo artwork của CoVie đã được khóa cho V1 sau visual exploration trong Figma.

Approved master:

```text
Concept B — Mái cong Việt
Market Pin = location pin + curved market roof + simplified stall
Brand symbol = Emerald 600 #059669
Wordmark = CoVie
Descriptor = Chợ Việt trong Primary Horizontal lockup
```

Production variants đã khóa:

```text
PrimaryHorizontal
Symbol
Wordmark
AppIcon
Favicon
MonoDark
MonoLight
```

Small-size rule:

```text
Symbol minimum recommended digital size = 20 px
Favicon = dedicated simplified 16 px master
```

Clear-space rule:

```text
x = approximate height of lowercase “o” in CoVie wordmark
clear space around logo >= x
```

Các phần vẫn intentionally open:

```text
custom illustration set
marketing campaign system
photography library
brand sonic identity
animated logo
dark theme
```

---

# 81. Figma implementation order

```text
1. Build primitive variables
2. Build semantic variables
3. Create typography styles
4. Create elevation styles
5. Create spacing/radius reference
6. Explore Market Pin logo candidates
7. Lock logo master
8. Build primitive components
9. Build marketplace components
10. Build Buyer P0 screens
11. Build Seller surfaces
12. Build Admin surfaces
13. Build prototypes
14. Accessibility review
15. Visual consistency review
```

---

# 82. Engineering handoff

Engineering không copy arbitrary Figma values.

Preferred flow:

```text
Figma semantic tokens
        ↓
packages/ui tokens
        ↓
CSS variables / Tailwind or equivalent mapping
        ↓
components
```

Example:

```css
--color-bg-canvas
--color-bg-surface
--color-text-primary
--color-text-secondary
--color-action-primary
--color-border-default
--radius-md
--space-4
```

---

# 83. Source-of-truth relationship

Cho visual decisions:

```text
Brand Foundation Spec
        ↓
Figma variables/styles
        ↓
Figma components
        ↓
Figma screens
        ↓
production UI
```

Nếu production cần thay foundation token:

```text
update Brand Foundation
update Figma
update code
```

Không silently diverge code khỏi Figma.

---

# 84. Approval state

Logo master decision:

```text
APPROVED: Concept B — Mái cong Việt
Exploration only: Concept A — Mái chợ ba gian
Exploration only: Concept C — V Market Roof
```

Các direction sau đã được chọn trong quá trình design:

```text
CoVie = Chợ Việt
Friendly Local Marketplace
Emerald Green
Friendly Modern
Balanced Density
Soft Elevation
Symbol + Wordmark
Market Pin
Modern Vietnamese identity
```

Các numeric/color/token values và logo master trong tài liệu hiện là **canonical V1 foundation** đã được review trực quan trong Figma; các component/screen vẫn tiếp tục qua các checkpoint riêng trước final product design freeze.

---

## Accessibility correction — approved during Figma implementation

The V1 brand identity remains `Emerald 600 #059669`. For normal-size white button text, the action semantic ramp is intentionally darker:

```text
semantic/action/primary         → Emerald 700 #047857
semantic/action/primary-hover   → Emerald 800 #065F46
semantic/action/primary-pressed → Emerald 900 #064E3B
```

This correction supersedes any earlier V1 wording that maps the primary action background to Emerald 600.

---


## Focus accessibility correction — approved during Task 18

Canonical focus presentation for UI controls is:

```text
semantic/border/focus → Emerald 600 #059669
focus ring             → 2 px, visible and unclipped
```

This correction supersedes earlier V1/V1.1 wording that mapped focus rings/borders to Emerald 500. Emerald 600 provides approximately `3.77:1` contrast on White and satisfies the locked `>= 3:1` UI boundary/graphic contrast baseline while preserving CoVie brand identity.

---

# 85. Final design principle

Khi phân vân giữa hai quyết định visual, chọn phương án làm CoVie:

```text
more local
more clear
more trustworthy
more usable
```

mà không làm nó:

```text
more cluttered
more decorative
more culturally literal
```

---

**End of CoVie Brand Foundation Specification V1.1**
