# CoVie — Banner quảng cáo mẫu

Trang xem thử: **`/banner-demo`** (Next.js App Router, được bọc trong `(public)` layout).

## Các mẫu có sẵn

| Variant | Kích thước dự kiến | Hiệu ứng |
|---|---:|---|
| `flash` | 160 × 600 | Viền LED dịu, túi quà nổi |
| `nearby` | 160 × 600 | Vòng định vị lan tỏa, ghim nổi |
| `spotlight` | 300 × 600 | Ánh sáng lướt qua, gian hàng nổi |
| `hero` | Chiều ngang container, cao tối thiểu 320 | Card nổi và nền sáng |

Các mẫu là **React + CSS thuần**, không phải ảnh động tải sẵn; không cần GIF, thư viện animation, video hoặc script bên thứ ba.

## Cách dùng

```tsx
import { AdBanner } from "@/components/ads/ad-banner";

<AdBanner variant="flash" href="/search" />
<AdBanner variant="hero" href="/search?type=SERVICE" />
```

- Toàn bộ banner là một liên kết có nhãn truy cập rõ ràng.
- Hiển thị nhãn **“Quảng cáo · Mẫu”** để phân biệt với nội dung thật.
- Không chứa giá, phần trăm giảm hoặc lợi ích thương mại chưa được xác thực.
- Có hỗ trợ `prefers-reduced-motion: reduce`; hiệu ứng sẽ dừng khi người dùng yêu cầu.
- Đừng nhúng HTML/JS tùy ý do nhà bán hàng gửi lên.

## Tích hợp sau này

Các mẫu **chưa được tự động gắn vào trang chủ hoặc kết nối API quảng cáo**. Khi có ad-serving backend, dùng dữ liệu đã được kiểm duyệt (vị trí, thời hạn, đích đến, creative, disclosure) để lựa chọn mẫu; không cho nội dung HTML tùy ý chạy trong trang.

Với banner dọc, chỉ bật ở màn hình đủ rộng, để ngoài container sản phẩm và ẩn khi viewport không còn khoảng trống phù hợp. Tránh overlay che nội dung, tự phát âm thanh hoặc hiệu ứng nhấp nháy nhanh.

## Thử nghiệm

```bash
pnpm --filter @covie/web typecheck
pnpm --filter @covie/web test
pnpm --filter @covie/web lint
```
