#!/usr/bin/env python3
"""CoVie OFF -> Vietnam barcode CSV. Python 3.10+, stdlib only.

Modes: --input local OFF bulk csv/tsv/gz; or --api pages (gentle pagination).
This is a derived Open Food Facts dataset: attribute and publish under ODbL 1.0.
"""
from __future__ import annotations
import argparse
import csv
import gzip
import json
import pathlib
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

HEADERS = [
    "gtin", "product_name", "brand", "quantity", "category_label", "market_country",
    "market_signal", "source", "source_record_url", "evidence_url", "license_id",
    "quality_status", "source_modified_at",
]
OFF = "https://world.openfoodfacts.org"
API = OFF + "/api/v2/search"
FIELDS = ("code", "product_name", "product_name_vi", "brands", "quantity", "categories",
          "categories_tags", "countries_tags", "countries", "last_modified_datetime")


def valid_gtin(code: str) -> bool:
    if len(code) not in (8, 12, 13, 14) or not code.isascii() or not code.isdigit():
        return False
    digits = [int(x) for x in code]
    weighted = sum(d * (3 if i % 2 else 1) for i, d in enumerate(reversed(digits[:-1]), 1))
    return (10 - weighted % 10) % 10 == digits[-1]


def clean(v: object, n: int) -> str:
    if v is None:
        return ""
    s = str(v).strip().replace("\x00", "")
    s = " ".join(s.split())
    return s[:n]


def is_vietnam(p: dict) -> bool:
    tags = p.get("countries_tags", "")
    if isinstance(tags, list):
        return "en:vietnam" in [str(x).casefold() for x in tags]
    tags = str(tags).casefold()
    tokens = [x.strip() for x in tags.split(",")]
    if "en:vietnam" in tokens:
        return True
    # Fallback for some older exports with country labels only.
    c = str(p.get("countries", "")).casefold()
    return "việt nam" in c or "vietnam" in c or "viet nam" in c


def normalize(p: dict) -> dict | None:
    if not is_vietnam(p):
        return None
    gtin = clean(p.get("code"), 100)
    if not valid_gtin(gtin):
        return None
    name = clean(p.get("product_name_vi") or p.get("product_name"), 220)
    if not name:
        return None
    modified = clean(p.get("last_modified_datetime", ""), 60)
    # Timestamps from OFF are ISO8601 or blank. Reject unusual forms to prevent SQL cast issues.
    if modified:
        try:
            datetime.fromisoformat(modified.replace("Z", "+00:00"))
        except ValueError:
            modified = ""
    return {
        "gtin": gtin, "product_name": name,
        "brand": clean(p.get("brands"), 160),
        "quantity": clean(p.get("quantity"), 80),
        "category_label": clean(p.get("categories"), 200),
        "market_country": "VN", "market_signal": "OFF_MARKET_TAG",
        "source": "OPEN_FOOD_FACTS", "source_record_url": f"{OFF}/product/{gtin}",
        "evidence_url": f"{OFF}/product/{gtin}",
        "license_id": "ODbL-1.0", "quality_status": "COMMUNITY_UNVERIFIED",
        "source_modified_at": modified,
    }


def iter_bulk_stream(path: pathlib.Path):
    opener = gzip.open if path.suffix.lower() == ".gz" else open
    with opener(path, "rt", encoding="utf-8-sig", errors="replace", newline="") as f:
        first = f.readline()
        if not first:
            return
        sep = "\t" if "\t" in first else ","
        class Prepend:
            def __init__(self, head, tail): self.head, self.tail = head, tail
            def __iter__(self):
                yield self.head
                yield from self.tail
        for row in csv.DictReader(Prepend(first, f), delimiter=sep):
            yield row


def fetch_page(page: int, size: int, agent: str) -> dict:
    params = {
        "countries_tags_en": "Vietnam", "fields": ",".join(FIELDS),
        "sort_by": "nothing", "page": page, "page_size": size,
    }
    url = API + "?" + urllib.parse.urlencode(params)
    for attempt in range(4):
        try:
            with urllib.request.urlopen(
                urllib.request.Request(url, headers={"User-Agent": agent, "Accept": "application/json"}),
                timeout=50,
            ) as response:
                return json.load(response)
        except urllib.error.HTTPError as e:
            if e.code not in (429, 503) or attempt == 3:
                raise
        except (TimeoutError, urllib.error.URLError):
            if attempt == 3:
                raise
        time.sleep(12 * (2 ** attempt))
    raise RuntimeError("unreachable")


def build(args):
    dest = pathlib.Path(args.output)
    dest.parent.mkdir(parents=True, exist_ok=True)
    counts = {"seen": 0, "written": 0, "not_vn_or_invalid": 0, "duplicate": 0}
    scanned = set()
    with dest.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=HEADERS, extrasaction="ignore")
        writer.writeheader()
        if args.input:
            iterable = iter_bulk_stream(pathlib.Path(args.input))
            for r in iterable:
                counts["seen"] += 1
                record = normalize(r)
                if not record:
                    counts["not_vn_or_invalid"] += 1
                    continue
                key = record["gtin"].zfill(14)
                if key in scanned:
                    counts["duplicate"] += 1
                    continue
                writer.writerow(record)
                scanned.add(key)
                counts["written"] += 1
                if args.max_rows and counts["written"] >= args.max_rows:
                    break
        else:
            for page in range(1, args.max_pages + 1):
                if page > 1:
                    time.sleep(args.delay)
                result = fetch_page(page, args.page_size, f"CovieBarcodeDataset/0.1 (mailto:{args.contact})")
                products = result.get("products") or []
                for p in products:
                    counts["seen"] += 1
                    record = normalize(p)
                    if not record:
                        counts["not_vn_or_invalid"] += 1
                        continue
                    key = record["gtin"].zfill(14)
                    if key in scanned:
                        counts["duplicate"] += 1
                        continue
                    writer.writerow(record)
                    scanned.add(key)
                    counts["written"] += 1
                    if args.max_rows and counts["written"] >= args.max_rows:
                        break
                print(f"Page {page}: items={len(products)}, total matching={result.get('count','?')}",file=sys.stderr)
                if not products or (args.max_rows and counts["written"] >= args.max_rows):
                    break
    manifest = {
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "source": "Open Food Facts", "source_license": "ODbL-1.0",
        "filter": "market countries_tags contains en:vietnam; NOT country of manufacture",
        "mode": "bulk" if args.input else "search_api_v2",
        "input_file": str(args.input) if args.input else None,
        "output_file": str(dest), "counts": counts,
        "note": "Product names/community-contributed content are NOT GS1-verified. No photos included.",
    }
    manifest_path = dest.with_suffix(".manifest.json")
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",encoding="utf-8")
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


def cli(argv=None):
    p = argparse.ArgumentParser(description="Extract Vietnam-market OFF barcode dataset")
    group = p.add_mutually_exclusive_group(required=True)
    group.add_argument("--input", type=str, help="OFF bulk CSV/TSV/CSV.gz (streaming)")
    group.add_argument("--api", action="store_true", help="OFF search API v2 (small pilot only)")
    p.add_argument("--output", default="data/vn_barcode_reference.csv")
    p.add_argument("--contact", help="Real contact email for required custom User-Agent; required with --api")
    p.add_argument("--max-pages", type=int, default=3, help="API pilot page limit; default 3")
    p.add_argument("--page-size", type=int, default=50, help="API page size; default 50")
    p.add_argument("--delay", type=float, default=7.0, help="Delay between search calls >=6.5 s")
    p.add_argument("--max-rows", type=int, default=0, help="Optional row cap, 0=no cap")
    args = p.parse_args(argv)
    if args.api and (not args.contact or "@" not in args.contact):
        p.error("--contact with a real email is required for --api")
    if args.delay < 6.5:
        p.error("--delay must be >=6.5 seconds to respect OFF 10 search/min/IP")
    if args.max_pages < 1 or not 1 <= args.page_size <= 100:
        p.error("--max-pages >=1 and --page-size between 1 and 100")
    csv.field_size_limit(min(sys.maxsize, 2**31-1))
    build(args)


if __name__ == "__main__":
    cli()
