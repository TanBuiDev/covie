# Social V2 — perf contracts và full-body measurement

Ngày: 2026-10-16. Giai đoạn: G3/G4/G6 preparation theo11.5, không nghiệm thu load/staging. Base803b25b; working HEADbb2e06b41282c8e9205c35a13e7b6adec68ceaa2/main plus dirty changes; no new PR. Lượt trước là progress: actual Create merchant-suspension contention confirmed existing correct guard, no production fix.

## Khoảng trống và RED có căn cứ

Đã đối chiếu current SocialPreview/Activity controllers và named perf artifacts trong11.5:

- Existing scenarios chỉ có old summary/comments; chưa có new preview/activity endpoints.
- `run-load.mjs` gọi `response.body.cancel()` trước khi ghi successful/latency. Hai real loopback HTTP fixtures xác nhận tool false-PASS: HTTP200 có body còn chờ700ms (>existing400ms experiment target), và HTTP200 Content-Length2048 nhưng connection cắt trước khi hoàn tất. Runner cũ đều exit0. Đây là defect của **measurement/acceptance tool**, không phải bằng chứng API/BE chậm.
- New tests RED: **4FAIL/3PASS/0skip** `.local/social-v2-perf-preparation-red.log`: hai transport false-PASS và hai missing scenario contracts. Không chạy API/storage thật, không tạo load trên app thường hoặc staging.

## Thay đổi tối thiểu

- `infra/perf/scenarios.mjs`: thêm `public-social-previews` với existing strict1–40distinctUUIDs/repeated itemIds. Thêm3private read scenarios LIKED/SAVED/COMMENTED, actual type/limit20, required existing `COVIE_LOAD_AUTH_COOKIE`, không userId. Giữ nguyên old scenario names/routes/auth/thresholds.
- New V2 scenarios dùng existing provisional Phase4 social-read experiment400ms/<1%, **không** gọi đó là V2 production SLO được owner ký duyệt. Normal flags không tự bật; cookie phải từ disposable trusted staging config.
- `infra/perf/run-load.mjs`: drain streamed body thay vì cancel, chỉ hoàn tất sample khi body complete. Truncated/timeout body đi vào existing network-error/failure path, không HTTP200-success giả. Không giữ payload trong memory.
- Additive summary field `responseBodyBytes`: completed-response count, total/min/max/rounded mean. Size bao gồm completed non-200 bodies; partial failed bodies không tính vào totals. Đây là decoded Fetch bytes, không compressed wire/CDN/media bandwidth. Existing sample/threshold/status fields giữ nguyên; elapsed semantics nay là full transfer nên old header-only baseline phải đo lại.
- `social-scenarios.test.mjs` mở rộng2contract checks; new `run-load.test.mjs` có đúng2real loopback transfer regressions. `.github/workflows/ci.yml` existing perf contract step chạy cả hai file; không dispatch/push hoặc khẳng định GitHub CI đã PASS.
- `docs/operations/performance.md` ghi cách chạy/scenario/auth, representative20–40caption/media/active-comment fixture, full-body metric semantics, limits và separate staging/browser/DB observations. Old Phase4 runner vẫn chỉ load summary/comments, không giả làm toàn V2 runner.

## Kiểm chứng thực sự chạy

```text
node --test infra/perf/social-scenarios.test.mjs infra/perf/run-load.test.mjs
node --check infra/perf/run-load.mjs
node --check infra/perf/scenarios.mjs
git diff --check -- .github/workflows/ci.yml infra/perf/scenarios.mjs infra/perf/run-load.mjs infra/perf/social-scenarios.test.mjs docs/operations/performance.md
```

GREEN **7PASS/0FAIL/0skip,1.055s**: `.local/social-v2-perf-preparation-green.log`. Byte totals match actual UTF-8 buffer; delayed body observed>=600ms/exits1; truncated body successful0/networkErrors=total/errorRatio1/completed byte samples0/exits1. Contract tests exercise40IDs/invalid/duplicate/oversize, missing auth refusal, actual3types; no real user cookie supplied/logged. Existing3scenario tests included because shared scenario/measurement source changed. Syntax/diff checks PASS. No formatter/linter was added for infra scripts. Exact local CI command passed; remote workflow/YAML parser execution not claimed.

Files changed: the four perf source/test files above, existing CI command, performance runbook and this checkpoint/AGENTS/audit. Migration/apply, application API/OpenAPI/client/BE/FE changes: **none**. Full BE/web tests=NOT RUN because unchanged; old successful browser/PG races not repeated. No recovered/runtime DB, normal service start/restart, remote Git, activation or deploy. Tests own random loopback ports and close child/server in finally; normal3000/8081 untouched. Whole V2 ACTIVE; normal V2/video flags OFF.

## Gate and limits

Local perf preparation/measurement regressions PASS, **actual11.5/12.4performance gate UNVERIFIED**. Neither contract tests nor a synthetic server prove new V2 APIs return nonempty correct fixture data under load, rich previews/media payload budget, actor-heavy history, DB pool/query count, browser long tasks/LCP/CLS/memory, staging latency/error distribution or production SLO. Must independently validate fixture and exact candidate deployment before load; tiny/empty200 is not representative proof. No unnecessary BE/index optimization follows from this tool fix.

Current frontend final build remains the identified pre-G1-screen-fix gap. No frontend source changed in this perf checkpoint; a single isolated current-source production build is justified by the earlier screen change, not another unchanged broad-suite run.

Next action: Build the current frontend production image in a refreshed whitelisted isolated context, without touching normal/browser `.next` or supplying secrets.
Required from you: Nothing
Completion condition: Current source hashes match the context and configured production build/runtime asset smoke passes; no deployment claim.
