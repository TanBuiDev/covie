> Cập nhật cấu trúc repo 2026-10-11: V017 đã nằm trong Flyway của dự án. Không sao chép/áp lại migration. Các hướng dẫn gói độc lập bên dưới là lịch sử; trong repo hiện tại chạy từ thư mục gốc:
>
> `psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f infra/seed/barcode/import_vn_barcode.sql`
>
> CSV được nạp là `infra/seed/barcode/vn_barcode_reference.csv`. Chỉ trỏ DATABASE_URL tới database test/đích được duyệt. Không chạy import trên dữ liệu phục hồi khi chưa có scope riêng. ETL local: `python infra/seed/barcode/build_barcode_csv.py --input <export> --output <csv-test>`.
# Covie Barcode Reference Vietnam — starter + ETL + PostgreSQL import

**Phiên bản:** v1 thử nghiệm, 2026-10-10. **Chưa deploy/chỉnh sửa repo GitHub.**

## Trong gói có gì?

- `data/vn_barcode_reference.csv`: **4 dòng seed thật**, đối chiếu tên/mã/định lượng từ trang FoodClock công khai có ghi nguồn Open Food Facts. Đây là dữ liệu thử nghiệm `NEEDS_REVIEW`, **không phải toàn bộ dataset Việt Nam hoặc kết quả Verified by GS1**. 4 dòng chưa được kiểm tra trực tiếp `countries_tags` trên server OFF, nên `market_signal=VN_CANDIDATE_MIRROR`.
- `data/starter_sources.json`: metadata seed.
- `scripts/build_barcode_csv.py`: chương trình tạo CSV từ **OFF bulk CSV/TSV(.gz)** hoặc API search v2, chọn sản phẩm **được gắn quốc gia bán Việt Nam**, kiểm tra GTIN check digit, bỏ bản ghi trùng, giữ nguồn/giấy phép.
- `sql/V017__barcode_reference_products.sql`: migration **đề xuất** cho repo Spring Boot/Flyway, tách bảng tham chiếu khỏi bảng listing.
- `sql/import_vn_barcode.sql`: load CSV bằng PostgreSQL `psql`, kiểm tra hợp lệ trước và upsert an toàn khi chạy lại.
- `tests/test_builder.py`: unit test filter, check digit, streaming import, dedup.

## 1. Chạy nhanh với seed 4 dòng

Từ thư mục chứa file này:

```bash
python3 -m unittest discover -s tests -v
# Chỉ khi chắc chắn V017 chưa tồn tại trong main:
cp sql/V017__barcode_reference_products.sql /path/to/covie/apps/api/src/main/resources/db/migration/
# Chạy Flyway qua quy trình migration hiện có của Spring Boot Covie, trên database test.
# Sau khi migration thành công và DATABASE_URL trỏ đúng database test:
export DATABASE_URL='postgresql://USER:PASSWORD@localhost:5432/DB'
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f sql/import_vn_barcode.sql
```

**Không áp dụng migration thủ công vào production**. Repo đang ở V016 tại thời điểm kiểm tra; nếu có V017 mới, chọn migration kế tiếp. `psql` sử dụng file `data/vn_barcode_reference.csv` tương đối theo thư mục hiện tại; phải đứng trong thư mục gói.

Kiểm tra:

```sql
SELECT gtin, gtin14, product_name, brand, quality_status, source_record_url
FROM barcode_reference_products
ORDER BY gtin14 LIMIT 10;
```

## 2. Tạo dataset lớn hơn: ưu tiên OFF bulk export

Nguồn OFF chính thức: https://world.openfoodfacts.org/data và hướng dẫn https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/.

Tải bản export CSV/TSV(.gz) mới nhất từ **trang export chính thức** bằng máy có Internet. Export có thể rất lớn; chương trình dưới đây đọc **streaming**, không giữ toàn bộ file trong RAM:

```bash
python3 scripts/build_barcode_csv.py \
  --input /path/to/en.openfoodfacts.org.products.csv.gz \
  --output data/vn_barcode_reference.csv
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f sql/import_vn_barcode.sql
```

Nếu export có ngôn ngữ khác, cần bảo đảm cột `code`, `product_name`, `countries_tags`, `brands`, `quantity`. Chương trình tự nhận tab/comma delimiter.

## 3. Pilot API nhỏ, không dùng API để hút toàn bộ dữ liệu

OFF ghi rõ API Search giới hạn **10 requests/phút/IP**, với nhu cầu hơn vài trăm mã nên tải bulk export thay vì quét API.

```bash
python3 scripts/build_barcode_csv.py \
  --api --contact 'your-real-email@example.com' \
  --max-pages 3 --page-size 50 --delay 7 \
  --output data/vn_barcode_reference.csv
```

Để không đánh mất seed ban đầu, nên dùng `--output data/vn_barcode_pilot.csv` trước rồi sao chép sang tên import khi đã kiểm tra. Không dùng server-side API Search cho tính năng search-as-you-type hoặc barcode theo từng khách trong production. Đọc barcode trực tiếp bằng lookup tại DB; API v3 của OFF chỉ để enrichment theo chính sách của OFF nếu cần.

## 4. Ý nghĩa trường dữ liệu

| Trường | Ý nghĩa |
|--|--|
| `gtin` | **Text** 8/12/13/14 ký tự, kiểm check digit. Giữ số 0 đầu. |
| `product_name`, `brand`, `quantity`, `category_label` | Metadata tham chiếu, **không** tự định nghĩa giá/tồn kho. |
| `market_country` | `VN`, chỉ ra phạm vi triển khai, **không phải xuất xứ**. |
| `market_signal` | `OFF_MARKET_TAG` nếu OFF gắn `countries_tags=en:vietnam`; `VN_CANDIDATE_MIRROR` cho seed cần rà soát. |
| `source`, `source_record_url`, `evidence_url`, `license_id` | Truy xuất nguồn + nghĩa vụ ODbL. |
| `quality_status` | `COMMUNITY_UNVERIFIED` hoặc `NEEDS_REVIEW`, **không** tương đương GS1 verified. |
| `source_modified_at` | Mốc sửa gần nhất nếu source có; để trống nếu không biết. |

**Lưu ý:** `893` là tiền tố do GS1 Vietnam cấp, **không chứng minh xuất xứ hay nơi bán**; đây là lý do bộ lọc dùng `countries_tags`, không dùng `LIKE '893%'`.

## 5. Kiến trúc đề xuất cho repo Covie

Repo tại ngày 2026-10-10:

- `apps/api/src/main/resources/db/migration/V005__catalog_and_search.sql` có `catalog_items`, `variants`, `location_variants` nhưng không có barcode reference.
- `apps/api/src/main/java/vn/covie/catalog/api/dto/create/CreateCatalogItemRequest.java` có các trường `type`, `listingKind`, `categoryId`, `name`, `description`, `attributes`, `tags`, `mediaIds`, `options`, `variants`, `locationOffers`. **Không có GTIN.**
- Bảng tham chiếu thêm bằng Flyway mới, **không** ghi đè `catalog_items` và **không** tạo listing từ dữ liệu bên ngoài.

Thiết kế API tương lai (CHƯA IMPLEMENT):

1. `GET /api/v1/barcodes/{gtin}`: validate 8/12/13/14 + check digit; query bằng `gtin14 = lpad(:gtin,14,'0')`; trả metadata tham chiếu + cờ `needsReview`, `sourceAttribution`, **không** gửi thông tin seller khác.
2. Seller mở form tạo catalog item → quét barcode bằng camera → gọi endpoint → hiện **gợi ý điền** `name`/`brand`/`quantity`/danh mục.
3. Người bán chọn `categoryId` thực tế thuộc CoVie, kiểm tra lại tên/quy cách; nhập price, location offer, tồn kho thủ công. Dữ liệu barcode không qua đường tắt bỏ qua xác thực backend.
4. Lưu GTIN vào metadata seller item hoặc mapping riêng sau khi yêu cầu sản phẩm và quyền chỉnh sửa được xác nhận; **không sửa DTO tạo sản phẩm ngay khi chưa quyết định GTIN nằm ở item hay variant** (một sản phẩm có thể có nhiều GTIN theo quy cách đóng gói).
5. Telemetry: lookup hit/miss, tỷ lệ seller chấp nhận đề xuất, mã bị báo sai. Không tự lưu PII không cần thiết.

### Lưu ý về giấy phép dữ liệu

- **OFF database:** ODbL 1.0 (được thương mại hóa có điều kiện: ghi công, chia sẻ tương tự cơ sở dữ liệu phái sinh). **Ảnh OFF:** CC BY-SA và quyền hình ảnh có thể phức tạp; **CSV này không chứa ảnh**, không copy ảnh vào MinIO.
- Dữ liệu OFF được cách ly trong `barcode_reference_products` — đây là một biện pháp kiến trúc giúp giảm phạm vi dữ liệu lẫn nhau, **không phải cam kết loại trừ mọi nghĩa vụ share-alike**. Phải rà soát cách tái sử dụng/public service và công bố bản dữ liệu phái sinh cần thiết.
- **GS1/GS1 Vietnam:** dùng để kiểm chứng có điều kiện; **không scrape / export hàng loạt** khi chưa có quyền. Nếu ký hợp đồng, xây kho nguồn riêng và logic ưu tiên source trên một tầng dịch vụ tổng hợp.
- **Dữ liệu người bán Covie** (giá, tồn kho, đơn hàng, PII): không nhập vào CSV OFF; không công bố theo giấy phép OFF.
- Ghi nguồn mẫu trong UI: “Một phần thông tin sản phẩm lấy từ Open Food Facts (ODbL 1.0). Thông tin do cộng đồng đóng góp, người bán cần xác nhận.”
- Ghi chép phiên bản snapshot, ngày import, bản chỉnh sửa và kênh xử lý yêu cầu gỡ/sửa dữ liệu. Trước triển khai thương mại, xin rà soát pháp lý riêng.

Nguồn: https://opendatacommons.org/licenses/odbl/1-0/ ; https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/tutorials/license-be-on-the-legal-side/ ; https://support.openfoodfacts.org/help/en-gb/12-donnees-api/94-y-a-t-il-des-conditions-pour-utiliser-l-api ; https://www.gs1.org/standards/id-keys/company-prefix ; https://www.gs1.org/services/verified-by-gs1

## 6. Tiêu chí trước khi dùng trong production

- [ ] Confirm nguồn + license + attribution/share-alike bằng reviewer pháp lý.
- [ ] Tạo snapshot từ OFF bulk đầy đủ, kiểm tỷ lệ VN, số bản ghi không tên, định dạng GTIN, trùng normalized GTIN14.
- [ ] QA thủ công ít nhất vài chục barcode bán phổ biến tại khu vực launch; không xác nhận bằng check digit một mình.
- [ ] Viết migration+rollback/test Flyway tương thích clean-migration của Covie.
- [ ] Viết API with OpenAPI, generated @covie/api-client, rate limit, kiểm thử Unicode/GTIN xấu.
- [ ] Seller UI: camera permission/mobile/không camera, xác nhận trước khi lưu.
- [ ] Tách bản ghi bị sửa bởi seller khỏi kho ODbL; thiết kế duy trì attribution/derivative obligations.
- [ ] Triển khai staging và đo hit rate trước khi production.

