# Social Product Card 2.0 — Admin caption readback

Local additive API/UI implementation. G1/G2/V2 are not complete; all feature flags
remain OFF and staging is unavailable.

## Implemented

- Optional socialCaption in AdminCatalogItemDetailResponse, read from the actual
  V020 column independently of description. The existing content query now reads
  both fields; no extra per-item query, new endpoint, status write or PII field.
- Existing AdminAccessPolicy, default-OFF read switch, private/no-store responses,
  media/case authorization and hidden/deleted Admin visibility remain unchanged.
  Null caption remains omitted under the existing JSON inclusion policy.
- Admin detail separately labels Commercial description and content displayed on
  Discovery. Caption uses ordinary React text interpolation and the existing
  pre-wrap class; it is not parsed as HTML/Markdown. Missing caption has an explicit
  empty state. There is no fallback copying description into caption.
- Uses generated client types and the existing Admin detail components/tokens.
  The retained exact frozen Figma frame 399:1413 context/screenshot was reviewed;
  no new icon/static asset or sidebar redesign. Actual rendered browser/Figma
  comparison is still unverified.

## Verification

| Check | Actual evidence |
| --- | --- |
| Backend RED | `.local/social-v2-g2-admin-caption-red.log`: actual Admin HTTP detail lacks socialCaption |
| Backend/read + export GREEN | `.local/social-v2-g2-admin-caption-green.log`: 3 tests pass, 36.906 s |
| UI RED | `.local/social-v2-g2-admin-caption-ui-red.log`: caption absent, one fail/other six pass |
| UI GREEN | `.local/social-v2-g2-admin-caption-ui-green.log`: 7 pass, 5.92 s |
| Empty caption assertion | `.local/social-v2-g2-admin-caption-empty-ui.log`: targeted one pass, six unselected, 5.09 s |
| Client | regenerated client and typecheck exit 0 |
| Web typecheck | `.local/social-v2-g2-admin-caption-typecheck.log`: exit 0 |
| Targeted ESLint | `.local/social-v2-g2-admin-caption-lint.log`: exit 0 |

The existing hidden/deleted read regression now seeds distinct real captions and
checks both issued-Admin-JWT HTTP responses. Description remains distinct;
newlines and HTML-looking text are returned exactly. Complete item/media rows
remain unchanged by reads and existing media privacy field assertions still pass.
No whole backend or unchanged business gate was rerun.

The UI component test renders a server-provided HTML-looking caption as literal
text, preserves its newline in textContent and verifies no script element exists.
It also retains commercial description and actual case navigation. Component
transport/navigation are mocked: no real browser, storage-object privacy or
end-to-end web-to-backend claim.

OpenAPI comparison confirms pre-V2 operations/original fields remain unchanged
except approved caption additions already tracked in G2; new Admin caption is
optional. No manually patched generated schema. Backend builds run in the isolated
API copy with disposable PostgreSQL; no recovered-data write, original restart,
migration, feature activation, commit or deployment occurred.

## Remaining full objective

G2 Seller Studio and media limit/order/preview/concurrency acceptance are pending.
G1 browser/query/storage privacy and G3 feed batch preview/gallery, G4 activity,
G5 verified video and G6 operational/release gates remain unfinished. The original
whole objective remains active; no staging/production completion is claimed.
