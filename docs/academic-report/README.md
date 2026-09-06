# Báo cáo đồ án CoVie — điểm tiếp tục

Ngày biên soạn ban đầu: 04/09/2026; cập nhật phần giới thiệu sản phẩm: 05/09/2026. Snapshot ứng dụng được đọc: `c477f30`.

## File chính

- `CoVie_BaoCaoDoAn.docx`: bản báo cáo Word 109 trang gồm 7 chương, tóm tắt Việt/Anh, 45 hình (14 sơ đồ và 31 hình nhóm 43 màn hình sản phẩm), 40 bảng, tài liệu tham khảo và phụ lục A–D.
- `CoVie_BaoCaoDoAn.md`: nội dung đồng hành để chỉnh sửa và tìm kiếm. Đây không phải bản dàn trang để nộp.
- `chapters/`: nội dung theo chương; `08-phu-luc-tu-dong.md` được sinh từ migration/OpenAPI.
- `assets/`: 14 sơ đồ tự dựng, logo HUTECH và bộ hình sản phẩm; bản Word dùng 29 ảnh ứng dụng và 14 hình thiết kế.

## Nội dung đã hoàn thành

1. Tổng quan đề tài, mục tiêu, phạm vi và phương pháp.
2. Cơ sở kỹ thuật và lựa chọn công nghệ.
3. Tác nhân, yêu cầu, quy tắc và 6 use case chi tiết.
4. Kiến trúc, ERD rút gọn, dữ liệu, API, checkout và vòng đời đơn.
5. Giới thiệu sản phẩm theo 27 mục, 43 màn hình và môi trường thử nghiệm.
6. Kiểm thử, 24 kịch bản nghiệm thu đề xuất, bằng chứng lịch sử, giới hạn.
7. Kết luận và lộ trình hoàn thiện.

Phụ lục kiểm kê 13 migration, 66 khai báo bảng, 12 bảng dữ liệu chọn lọc, 119 path / 144 operation HTTP của hợp đồng lưu. Đây là đọc tĩnh, không xác nhận schema runtime hoặc gọi thử mọi endpoint.

## Kiểm tra và giới hạn

- `verify_report.py` kiểm tra cấu trúc DOCX, XML/ZIP, chương, bảng, hình, liên kết mục lục, hình học bảng, danh mục API, trích dẫn và các nội dung bắt buộc.
- LibreOffice đã khả dụng. Renderer đóng gói `render_docx.py` đã xuất bản PDF kiểm tra 71 trang và ảnh từng trang; các trang thay đổi đã được xem trực quan, các vùng nội dung không đổi được đối chiếu checksum với bản đã xem. Không phát hiện nội dung tràn hoặc chồng lấn. Khoảng trắng cuối chương và trước hình lớn được giữ để tránh tách hình/chú thích.
- Đã chuẩn hóa phông Times New Roman, tiêu đề đen, bỏ đường kẻ từ mẫu Word, điều chỉnh cột mã, căn giữa theo chiều dọc và viền bảng xám nhạt. Hình đi cùng chú thích và dòng nguồn.
- Mục lục và danh mục hình/bảng có liên kết, dấu dẫn và số trang. Đã đối chiếu 163 đích tiêu đề/chú thích và 150 trường PAGEREF; 116 kiểm tra cấu trúc đạt. `render-verification.json` lưu checksum DOCX/PDF và ảnh trang để gắn kết quả với đúng phiên bản.
- PDF và ảnh trong `.local/report-editorial-verified/` là hiện vật QA nội bộ. Số trang có thể thay đổi khi mở bằng trình soạn thảo khác hoặc sửa nội dung; cần cập nhật trường và kiểm tra lại trước khi in.
- Các test ứng dụng chưa được chạy lại trong phiên viết báo cáo. Con số ngày 26/08 chỉ là bằng chứng lịch sử.

## Thông tin cần bổ sung trước khi nộp

Xác nhận tên đề tài chính thức; mẫu và quy định định dạng của khoa. Đã điền Bùi Lê Hoàng Tấn, MSSV 2280602879, lớp 22DTHH2, HUTECH, Hồ Chí Minh, 07/2026; bỏ dòng giảng viên theo yêu cầu. Khoa Công nghệ thông tin, chuyên ngành Công nghệ phần mềm đã được điền theo xác nhận của sinh viên. Đã thay trang hướng dẫn hoàn thiện hồ sơ bằng lời cam đoan, lời cảm ơn và nội dung ghi nhận công cụ hỗ trợ. Nội dung lưu tại `front-matter.md`; không còn trường thông tin bỏ trống trong bản Word.

## Nếu người dùng gửi “tiếp tục”

Không bắt đầu lại từ dàn ý. Đọc `build-summary.json`, `verification.json`, `render-verification.json` và nội dung đã lưu. Bước dàn trang đã hoàn thành; tiếp theo là áp dụng mẫu khoa khi được cung cấp. Có thể thay bộ ảnh local bằng bằng chứng mới gắn commit nếu có yêu cầu; không biến ảnh cũ thành nghiệm thu mới. Không gọi các test đề xuất là đã đạt.

Khi muốn tái sinh báo cáo, dùng Python do workspace dependency loader cung cấp để chạy `build_report.py`; builder sẽ ghi lại nội dung phụ lục, DOCX và thống kê, làm mất hiệu lực QA cũ. Phần nội dung tự viết nằm trong `chapters/01` đến `07`; không chỉnh phụ lục tự động bằng tay. Chạy `table_layout.py` sau mỗi lần `refine_layout.py` để giữ định dạng bảng thống nhất. Sau đó chạy renderer chuẩn; chạy `refine_layout.py --pdf <PDF vừa render>` để chuẩn hóa bố cục và điền số trang. Render lại và lặp cập nhật số trang cho đến khi ổn định. Xem từng trang, rồi chạy `verify_rendered_report.py --pdf <PDF cuối> --reviewed-pages <các số trang thực sự đã xem, phân cách bằng dấu phẩy>` và `verify_report.py`. Không khai báo đã xem trang nếu chỉ chạy kiểm tra tự động. `refine_layout.py` giữ một bản sao đầu vào lần đầu trong `.local/report-qa-original/` và không tái sinh kiểm kê nguồn.

## Phát hiện mã nguồn được ghi vào báo cáo

CheckoutPreviewService truyền khoảng cách 0 vào ShippingEvaluator; đường revalidate đã đọc chưa đủ chứng minh coverage địa lý đầy đủ. Item/contact/shipping policy snapshot chưa đầy đủ như mô hình lịch sử mong muốn. Đây là phát hiện tĩnh; không sửa code ứng dụng trong tác vụ báo cáo, cần tái hiện bằng test trước khi triển khai thay đổi.

`evidence-manifest.json` lưu SHA-256 của các nguồn được đọc trước khi cập nhật các ghi chú báo cáo vào AGENTS/Project Status; không chứa cấu hình thật hay credential.

## Logo và thông tin bìa

Logo gốc không chỉnh sửa được lưu tại `assets/hutech-logo.png`, lấy từ [website HUTECH](https://www.hutech.edu.vn/homepage/tin-t-c/ho-t-d-ng-sinh-vien/1744-y-nghia-logo-d-i-h-c-cong-ngh-tp-hcm-hutech). `cover-metadata.json` lưu nguồn, checksum và thông tin cá nhân. Mốc 07/2026 trên bìa không thay đổi ngày tổng hợp hoặc ngày bằng chứng kỹ thuật. Sau cập nhật, đã xem lại trang 2 và 72; ảnh trang 1 và 3–71 trùng SHA-256 với bản đã kiểm tra toàn bộ trước đó.

## Bố cục bìa đã thiết kế lại

Bìa tham khảo hai mẫu chính thức của Khoa CNTT HUTECH (ĐATN và ĐACN). Chọn logo nhỏ bên trái, khối đơn vị bên phải, tên đề tài ở giữa, chuyên ngành và thông tin sinh viên phía dưới, khung kép riêng trang bìa. `cover-design.json` lưu nguồn và các điều chỉnh; `cover_layout.py` áp dụng bố cục khi tái tạo báo cáo. Đã xem trang 1–2; trang 3–72 trùng checksum ảnh của bản đã kiểm tra.

Report header update: removed the running title from all page headers, retaining footer page numbers. Latest QA: `.local/report-noheader-final/`; 72 pages, with pixel changes confined to the removed header and all remaining regions unchanged.

Footer field repair: replaced empty simple PAGE field with begin/instruction/separate/cached-result/end structure. LibreOffice output verified sequential page numbers 2–72; all 72 page images unchanged. QA: `.local/report-pagefield-final/`. Static viewers that do not evaluate PAGE fields cannot guarantee per-page numbering; Word/LibreOffice must evaluate the dynamic field.


Academic report table redesign: all 40 tables restyled with dark gray headers, white header text, subtle alternating rows, fine borders and content-specific alignment. SQL and use-case column widths adjusted; table contents compared unchanged. Final QA: `.local/report-tables-verified/`, 72 pages, 163 navigation targets and 150 cached references verified; 116 structural checks pass. Changed pages visually inspected; unchanged pages carried forward by image SHA-256. Style reference and settings: `docs/academic-report/table-design.json`.

## Rà soát văn phong và nội dung báo cáo

Đã viết lại các đoạn mang tính hướng dẫn người biên soạn thành mô tả đề tài, phương pháp, kết quả và giới hạn. Phụ lục D chỉ còn phạm vi phiên bản và nguồn hình; bỏ thông tin trao đổi về bìa và lời nhắc hoàn thiện hồ sơ. Ghi nhận công cụ hỗ trợ tập trung ở phần đầu. Các nguồn chương và bản Markdown được đồng bộ. Bản hiện tại có 71 trang, 7 chương, 8 hình và 40 bảng; QA tại `.local/report-editorial-verified/`. Các mốc 72 trang ở phần lịch sử trên thuộc phiên bản trước.

`editorial_revision.py` lưu quy tắc biên tập và được áp dụng trong builder/refine; chạy lại sau các tiện ích cá nhân hóa cũ nếu cần dùng chúng. Sau khi dựng lại nguồn tự động, chạy tiện ích này để đồng bộ nội dung Markdown. `refresh_navigation.py --pdf <PDF>` chỉ cập nhật số trang mục lục sau sửa nội dung, không tái kiểm kê ứng dụng. Bất kỳ lần sửa DOCX nào đều cần render và xác minh lại. `editorial-review.json` lưu đối chiếu với bản trước biên tập. Không chạy lại kiểm thử ứng dụng trong lần chỉnh văn phong này.

Report chapter alignment: centered all seven body chapter titles (Heading 1 beginning CHƯƠNG), preserving TOC alignment and all text. QA `.local/report-centered-chapters/`: 71 pages; seven changed chapter-opening pages visually inspected, remaining 64 PNGs hash-identical to the reviewed editorial render. 163 navigation targets, 150 cached references and 116 structural checks pass. Builder/refine preserve the alignment through apply_editorial.


Academic report terminology formatting: retained English and technical terms now use curly double quotation marks in Vietnamese prose, headings, navigation, all 40 tables and five source-generated diagrams. English abstract, executable commands, URLs, literal paths/file names and original screenshots retain their syntax/content. All 1607 text paragraphs match the previous report after quote normalization (excluding cached page fields); seven chapter titles remain centered. Latest QA: `.local/report-terminology-complete/`, 71 pages, 163 navigation targets, 150 cached references, 116 structural checks passed. Per-page visual provenance: `docs/academic-report/terminology-visual-review.json`. No application code changed or regression rerun.


Academic report prose revision: rewrote 20 paragraphs/table cells covering screenshot attribution, evaluation methods, test scope, seller implementation and technical limitations. Screenshot sources now read “Nguồn: giao diện CoVie với dữ liệu thử nghiệm.” Chapter Markdown and regeneration rules are synchronized through academic_prose.py. Historical test dates and unverified runtime limitations remain explicit. Latest QA: .local/report-prose-final-v3/, 71 pages; 163 navigation targets, 150 cached references and 116 structural checks pass. Changed pages inspected; unchanged pages matched by full PNG SHA-256. Per-page provenance: docs/academic-report/academic-prose-visual-review.json. No application code changed or regression rerun.


## Academic report product expansion — 2026-09-05

Expanded Chapter 5 into 27 sections with 31 product figure groups containing 43 selected screens: 29 existing application screenshots with test data and 14 Figma design exports. Covers buyer, seller and administrator journeys on desktop/mobile plus empty, error and concurrent-change states. Figma images are explicitly attributed as designs, not runtime acceptance evidence. Added a product journey overview to Chapter 1 and synchronized Chapter 6, reference N9 and Appendix D. One overlapping shop screenshot was excluded from the final selection.

Current report: 99 pages, seven centered chapter titles, 36 figure captions (five diagrams plus 31 product figures), 40 tables. QA `.local/report-product-delivery/`: 209 navigation targets, 196 cached page references and 120 structural checks passed. Changed pages visually inspected; unchanged page bodies matched by pixel SHA-256 to previously reviewed pages and footers verified separately. Exact provenance: `docs/academic-report/product-visual-review.json`. Historical source/test dates and runtime gaps remain unchanged; no application code changed or application regression rerun. Sources and reproducible chapter assembly: `product-expansion-content.json`, `product-image-manifest.json`, `product-figma-inventory.json`, `product_expansion.py` under the report directory.


Bản bàn giao hiện tại là `CoVie_BaoCaoDoAn.docx` (99 trang); các số trang và số hình trong các mục lịch sử phía trên thuộc phiên bản cũ. Chương 5 hiện nằm ở trang 38–70. Nội dung mới tập trung vào mục đích sử dụng, thao tác và kết quả của từng nhóm chức năng.

Khi chỉnh riêng phần giới thiệu sản phẩm, sửa `product-expansion-content.json`, chạy `product_expansion.py`, render, cập nhật mục lục bằng `refresh_navigation.py`, rồi render và xác minh lại. Không chạy các bộ quy tắc biên tập cũ sau bước này mà không áp dụng lại phần mở rộng. Builder đã gọi `apply_product_expansion` trước khi lưu; việc tái sinh toàn bộ có thể kiểm kê lại nguồn và làm mất hiệu lực số liệu/QA đã đóng băng. Bản QA không phải tài liệu nộp; chỉ bàn giao file Word. `product-image-manifest.json` lưu nguồn và checksum của cả hình được xem xét; `product-expansion-summary.json` xác định chính xác 43 hình được chọn vào tài liệu.


## Academic report use-case expansion — 2026-09-05

Added nine use-case diagrams in Chapter 3 section 3.12 (pages 29–37): buyer, seller and administrator role views, followed by six views corresponding to UC-01 through UC-06. Each uses external actors, the CoVie system boundary, goal ellipses and undirected participation associations; the prose explains scope, conditional actions and links to the existing specifications. No sequence or include/extend dependency is implied by those associations. The original actor overview remains as Figure 3.1; new figures are 3.2–3.10.

Current DOCX: 109 pages, 45 figure captions (14 diagrams and 31 product figure groups containing 43 screens), 40 tables. QA `.local/report-usecase-final/`: 228 navigation targets, 206 cached references, 121 structural checks and all footer numbers verified. Nine diagram pages and changed navigation pages inspected; unchanged page bodies matched by exact pixel SHA-256 to reviewed pages. Provenance: `docs/academic-report/usecase-visual-review.json`. All 40 table contents compared unchanged. No application code changed or application tests rerun.

Reproduction: `docs/academic-report/usecase_expansion.py` stores the diagram models, drawing and chapter insertion; `usecase-diagrams.json` records the models and asset hashes. Run after product expansion, then render, refresh navigation, render again and verify. The main builder applies both expansion steps. Chapter Markdown and the combined Markdown are synchronized.

## Bìa theo mẫu tham khảo — 2026-09-05

Trang bìa được bố trí lại theo ảnh HUTECH do người dùng cung cấp: khung kép sát mép, logo nhỏ bên trái, tên Bộ và trường bên phải, tên báo cáo và đề tài căn giữa, hai khối thông tin thẳng cột, địa điểm–thời gian ở cuối trang. Nội dung dùng thông tin CoVie; thêm Ngành Công nghệ thông tin, giữ Chuyên ngành Công nghệ phần mềm và không hiển thị giảng viên hướng dẫn. Bản QA `.local/report-cover-reference-v2/` có 109 trang; trang 1–2 đã xem, trang 2–109 khớp SHA-256 ảnh với bản trước. Chi tiết tại `cover-design.json` và `cover-reference-visual-review.json`.


## Hình giải thích bổ sung — 2026-09-05

Báo cáo bổ sung bảy hình được dựng từ nội dung và nguồn nội bộ CoVie tại Chương 1, 2, 4, 6 và 7. Các hình mô tả hành trình giá trị, luồng sự kiện, vai trò công nghệ, lớp bảo vệ yêu cầu, chiến lược kiểm thử, bốn nhóm bằng chứng và lộ trình hoàn thiện. Chúng giúp ngắt các phần nhiều chữ nhưng không được dùng như bằng chứng thực thi mới. Bản hiện tại có 113 trang, 52 chú thích hình và 40 bảng. QA .local/report-explanatory-final/; mô hình và checksum nằm trong xplanatory-figures.json, hồ sơ xem hình nằm trong xplanatory-visual-review.json.
