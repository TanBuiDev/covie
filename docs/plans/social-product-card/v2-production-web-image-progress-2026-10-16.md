# Social V2 — production web build/image local gate

Ngày: 2026-10-16. Phạm vi: production build và runtime packaging của web hiện tại, không phải deploy hoặc nghiệm thu toàn bộ Social V2. Dùng UI dự án hiện tại; không chờ Figma.

## Cách kiểm chứng

- Tạo context mới `.local/social-v2-web-production-build-20261016` từ root manifests, `apps/web`, `packages/api-client`, `packages/config`. Không copy `.env*`, `.local`, `node_modules`, `.next`, browser artifacts hay runtime/recovered data. Không sửa `.next` của web thường hoặc browser fixture.
- Chạy đúng `apps/web/Dockerfile`: Node24, `pnpm install --frozen-lockfile`, `pnpm --filter @covie/web build` → `next build` mặc định Turbopack, standalone runner. Không đổi sang webpack để né build failure, không cung cấp secret hoặc bật V2 flags.
- Production source copy sau sửa có **577 files SHA256 khớp**, không mismatch: `.local/social-v2-web-production-source-match.log`.

## Hai lỗi có bằng chứng và sửa tối thiểu

### Public assets thiếu trong runner

Build ban đầu exit0, nhưng chạy image riêng trên random localhost port: `/login`200 còn `/icon.svg`, `/figma/covie-market-pin.svg`, `/sw.js`, `/offline.html` đều404. Log `.local/social-v2-web-production-smoke-red.log`; không phải lỗi BE. Source Dockerfile chỉ copy standalone và static, thiếu public. Installed Next `output.md` xác nhận standalone không tự copy public; current UI tham chiếu market-pin trực tiếp.

Thêm đúng một `COPY --from=build --chown=10001:10001 /workspace/apps/web/public ./apps/web/public`. Không sửa React, CSS, API, auth hoặc nghiệp vụ. Mở rộng smoke gate hiện có `infra/scripts/verify-web-image.sh` để bắt buộc bốn asset200 thay vì chỉ homepage.

### Nested local configuration bị gửi vào web build context

Release workflow build từ repository root với `-f apps/web/Dockerfile`; file ignore có hiệu lực là `apps/web/Dockerfile.dockerignore`. Existing `.env`/`.env.*` không loại nested web env và chưa có `.local` rule.

Probe BuildKit `FROM scratch; COPY apps/web /payload`, dùng đúng bản ignore và **chỉ fake sentinels**, đã export cả `apps/web/.env.local` và `apps/web/.local/fixture.json`: `.local/social-v2-docker-context-probe-red.log`, assertion exit1. Không đọc/copy actual credentials. Actual local web có `.env.local` và `.local`; đây là rủi ro context/layer thực, không phải chứng cứ secret đã bị phát hành.

Chỉ thêm `**/.env`, `**/.env.*`, `.local`, `**/.local` vào web Dockerfile-specific ignore. Probe green exit0: nested env/runtime sentinel đều absent, legitimate `app/page.tsx` còn nguyên: `.local/social-v2-docker-context-probe-green.log`. Không sửa API Dockerfile hoặc các ignore khác vì ngoài phạm vi COPY đã tái hiện.

## Kết quả cuối

```text
docker build --progress=plain -f <isolated-context>/apps/web/Dockerfile -t covie-web:social-v2-local-verification-20261016 <isolated-context>
bash -n infra/scripts/verify-web-image.sh
bash infra/scripts/verify-web-image.sh covie-web:social-v2-local-verification-20261016
```

- Final build **exit0**, Next16.4.0/Turbopack compile5.0s, TypeScript12.0s, static37/37, build step19.9s: `.local/social-v2-web-production-build-final.log`. Initial build log retained separately; rebuild justified by changed Docker packaging inputs.
- Existing verifier + added public asset assertions **exit0**, `Web image runtime contract OK (user=10001:10001)`: `.local/social-v2-web-image-verifier-green.log`. Existing forbidden-path checks retained, not a claim of exhaustive secret/image audit. Two auxiliary attempts to address the superseded initial image by ID could not resolve it; they are harness lookup failures, not another product RED. HTTP404 capture above is the product RED.
- Local final image ID `sha256:71fe12429afaf3b5ee8d4ff76f7a1a0cd1cbd20ec8b7ee4eb570dabda469aaf6`,405943210bytes, user10001:10001. This is a local artifact from a dirty source snapshot, **not** an approved/pushed/signed release digest.
- `git diff --check` for the three changed production/gate files PASS. Owned smoke containers removed by finally/trap. Normal3000/8081 had no listeners at final observation; no normal service started/restarted/stopped.

## Giới hạn và bước kế tiếp

Không chạy lại full BE/web tests, old browser scenarios hoặc client generation vì không có BE/API/schema/client/React change. Không truy cập recovered/runtime DB, remote Git, activation, registry push hay deployment. Local build/runtime packaging gate đạt phạm vi trên, không thay thế production browser/auth flows, CI scanner/signing, CDN/object privacy, load, backup/rollback hoặc staging. Whole V2 vẫn ACTIVE, normal V2/video flags OFF. Không có staging thì ghi UNVERIFIED, không biến owner approval thành technical evidence.

Next action: Đối chiếu source/coverage các trạng thái lỗi, thu hồi quyền và xung đột của G1 Admin để chọn đúng một missing gate có cơ sở, không lặp happy path đã đạt.
Required from you: Nothing
Completion condition: Xác định contract và bằng chứng hiện có cho gate G1 kế tiếp; chỉ tái hiện/sửa khi có khoảng trống hoặc lỗi cụ thể.
