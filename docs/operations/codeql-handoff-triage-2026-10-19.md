# CodeQL handoff triage — 2026-10-19

Reviewed exact PR39 head `df58a3e508bb20af7c8509f27abc5b8cf12743ca`, not an
uncommitted prototype. Downloaded the actual JavaScript SARIF analysis1980850210
and inspected every reported codeFlow and its matching Git blob. The analysis has9
raw results;7 are open annotations in this PR. The2 historical Ads results are outside
this review and their disposition is not changed.

| Alert | Current source flow | Actual sink | Reviewed disposition |
| --- | --- | --- | --- |
|6 | Catalog create File input562/563 → File[]213/214 → objectURL219 | image src579 | False positive: browser-created URL, not HTML text parsing |
|7 | Catalog edit File input470/471 → File[]161/162 → objectURL167 | image src486 | Same bounded File→objectURL→image flow |
|8 | Merchant logo input398/434/450 → handler159 → objectURL170 → state138 | image src336 | False positive: property/JSX image source |
|9 | Same logo flow | image src383 | Same exact reviewed value transformation |
|10 | Merchant cover input479/515/531 → handler174 → objectURL185 → state139 | image src464 | False positive: property/JSX image source |
|11 | Barcode image File input83 → objectURL89 | img.src102 | DOM image URL assignment, no HTML interpretation |
|12 | Catalog names/barcode selections → Social preview → SocialPostContent name | Link aria-label67, column78 | JSX string accessibility attribute, not HTML or href interpolation |

For12, the reported column is inside `aria-label={\`Xem ${name}\`}`. The href is a
separate value; the cited name dataflow is not used as an HTML string or URL scheme.
For6–11 the byte data is selected through a File and a browser-generated blob URL;
the source DOM text/file name is not concatenated into HTML. Merchant handlers also
restrict type and size, but that is not the basis of this XSS classification. Catalog
accept hints alone are not security validation, and no broader upload/SVG/storage
security conclusion follows from this review.

No innerHTML, outerHTML, insertAdjacentHTML, dangerouslySetInnerHTML or document.write
appears in the five reviewed source files. React's standard children/ARIA attributes
are distinct from its explicit raw-HTML escape hatch; see the [React DOM reference](https://react.dev/reference/react-dom/components/common#dangerously-setting-the-inner-html).
The native [object URL API](https://developer.mozilla.org/en-US/docs/Web/API/URL/createObjectURL_static)
and [image src property](https://developer.mozilla.org/en-US/docs/Web/API/HTMLImageElement/src)
describe the relevant URL/image operations rather than HTML-string insertion.

## Source binding

| Source | Git blob at the analyzed head |
| --- | --- |
| barcode-camera-modal.tsx | e72035ae37e3174b95cb65cd46db1817fd1f46ec |
| catalog-create-form.tsx | 670afcd7292720f300724c436a71c412f1447a48 |
| catalog-edit-form.tsx | 352b4dfbc4f0d80d1fc24f643657dd139ef8aa66 |
| social-post-content.tsx | 3730cbaf23f33bf70357aa8a6d8faa60290c9e76 |
| merchant-form.tsx | 1c4cebb8292a988729f244f6d824c62849a3c4fe |

All five working files match those exact blobs. Current full Web verification685
PASS and the real Seller→upload→publish→guest flow retain literal markup-like caption
text; they are supporting bounded evidence, not an exhaustive browser XSS audit.

Only alerts6–12 qualify for targeted GitHub false-positive dismissal with the above
analysis/commit-specific explanation. Do not disable a query, suppress whole paths,
rewrite working preview components just to alter scanner output, dismiss unrelated
alerts or treat successful Analyze jobs as proof that no alerts were reported. The
API receipt and subsequent aggregate check state must establish actual disposition.
Reopen review if these flows acquire an HTML sink, change URL construction, or stop
using the reviewed React/image attribute paths.

Actual API receipts now confirm all seven reviewed alerts dismissed as false
positives, zero open alerts on the reviewed PR head, and the aggregate CodeQL check
successful. No detector configuration changed. Raw SARIF findings remain recorded;
this is not a claim that the scanner emitted zero results.
