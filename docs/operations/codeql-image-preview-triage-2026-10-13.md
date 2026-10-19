# CodeQL image-preview triage — 2026-10-13

Reviewed committed main `2f9888c2f2645df2f5872c82ec49aa08f710427e`, JavaScript analysis1937994773. Downloaded the actual GitHub SARIF to `.local/main-codeql-1937994773.sarif.json`; inspected `js/xss-through-dom` codeFlows and the matching committed source with `git show HEAD:<path>`, rather than relying on current uncommitted catalog UI.

| Alert | Source and actual transformation | Flagged sink | Disposition |
|---|---|---|---|
| 6 | Catalog create: file-input526/527 → File[] state203 → `files.map(file => URL.createObjectURL(file))`209 | catalog-create-form.tsx543, image preview `src={previewUrls[0]}` | Source-reviewed false positive |
| 7 | Catalog edit: file-input415/416 → File[] state149 → objectURL mapping155 | catalog-edit-form.tsx431, image preview `src={previewUrls[idx]}` | Source-reviewed false positive |
| 8 | Merchant logo: file-input371 → handleLogoSelect153 → objectURL164 → preview state132 | merchant-form.tsx309 | Source-reviewed false positive |
| 9 | Same logo dataflow as8 | merchant-form.tsx356 | Source-reviewed false positive |
| 10 | Merchant cover: file-input452 → handleCoverSelect168 → objectURL179 → preview state133 | merchant-form.tsx437 | Source-reviewed false positive |

The browser-generated object URL is used as an image source; source DOM text or file names are not copied into HTML. React attribute assignment does not reinterpret the input as HTML. No innerHTML/outerHTML/insertAdjacentHTML/dangerouslySetInnerHTML is present in the three reviewed files. Merchant handlers additionally restrict selected MIME types to JPEG/PNG/WebP and size<=10MB. Catalog accept hints alone are not treated as security validation; the disposition rests on the actual File→objectURL→image flow, not on accept attributes or file names.

Merchant stored logoUrl/coverUrl is a separate branch supplied by READY asset resolution and is not the source path cited by these five SARIF flows. This triage does not prove all remote image URLs, upload processing, SVG handling, storage delivery or unrelated UI sinks safe.

No production repair, refactor, suppression, rule disabling or GitHub dismissal was performed. All five alerts remain open on GitHub. Individual CodeQL Analyze jobs succeeded, meaning analysis completed; the aggregate PR25 CodeQL result failed because it reported alerts. Required PR checks and main CI success must not be described as zero security findings.

Recommended disposition when managing the alerts: dismiss only6–10 as `false positive`, with this exact committed-source/analysis-specific evidence. Do not dismiss unrelated results from the same SARIF: the file also contains historical/dismissed findings in Ads, whose status and dataflows were outside this focused triage. Reopen review if relevant source changes replace objectURL/image assignment with an HTML sink or change the cited dataflow.
