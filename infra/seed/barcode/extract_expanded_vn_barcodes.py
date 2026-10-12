#!/usr/bin/env python3
"""CoVie Open Food Facts -> Vietnam expanded barcode CSV builder.
Extracts:
1. Records explicitly tagged with Vietnam (en:vietnam / Việt Nam) -> OFF_MARKET_TAG, COMMUNITY_UNVERIFIED
2. Records with GS1 Vietnam prefix 893 (with valid GTIN and product name) -> VN_CANDIDATE_MIRROR, NEEDS_REVIEW
"""
from __future__ import annotations
import csv
import gzip
import json
import pathlib
import sys
from datetime import datetime, timezone

HEADERS = [
    "gtin", "product_name", "brand", "quantity", "category_label", "market_country",
    "market_signal", "source", "source_record_url", "evidence_url", "license_id",
    "quality_status", "source_modified_at",
]
OFF = "https://world.openfoodfacts.org"

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

def is_vietnam(tags_val: str, countries_val: str) -> bool:
    tags = tags_val.casefold()
    tokens = [x.strip() for x in tags.split(",")]
    if "en:vietnam" in tokens:
        return True
    c = countries_val.casefold()
    return "việt nam" in c or "vietnam" in c or "viet nam" in c

def main():
    uncompressed = pathlib.Path(r"E:\WebApp\CoVie\thu_muc_tam\en.openfoodfacts.org.products.csv\en.openfoodfacts.org.products.csv")
    out_file = pathlib.Path(r"E:\WebApp\CoVie\thu_muc_tam\vn_barcode_reference_expanded.csv")

    counts = {"seen": 0, "written_tag_vn": 0, "written_893_mirror": 0, "skipped": 0, "duplicate": 0}
    scanned = set()

    with open(uncompressed, "r", encoding="utf-8", errors="replace") as infile, \
         open(out_file, "w", newline="", encoding="utf-8") as outfile:
        
        header = infile.readline().split("\t")
        col_code = header.index("code")
        col_name = header.index("product_name")
        col_name_vi = header.index("product_name_vi") if "product_name_vi" in header else -1
        col_brand = header.index("brands")
        col_quant = header.index("quantity")
        col_cat = header.index("categories")
        col_tags = header.index("countries_tags")
        col_countries = header.index("countries")
        col_mod = header.index("last_modified_datetime")
        
        writer = csv.DictWriter(outfile, fieldnames=HEADERS)
        writer.writeheader()
        
        for line in infile:
            counts["seen"] += 1
            parts = line.split("\t")
            if len(parts) <= max(col_code, col_name, col_tags, col_countries):
                counts["skipped"] += 1
                continue
                
            code = clean(parts[col_code], 14)
            if not valid_gtin(code):
                counts["skipped"] += 1
                continue
                
            name_val = parts[col_name_vi] if col_name_vi != -1 and len(parts) > col_name_vi and parts[col_name_vi].strip() else parts[col_name]
            name = clean(name_val, 220)
            if not name:
                counts["skipped"] += 1
                continue
                
            tags = parts[col_tags]
            countries = parts[col_countries]
            tag_vn = is_vietnam(tags, countries)
            is_893 = (code.startswith("893") and len(code) == 13) or (code.startswith("0893") and len(code) == 14)
            
            if not tag_vn and not is_893:
                counts["skipped"] += 1
                continue
                
            key = code.zfill(14)
            if key in scanned:
                counts["duplicate"] += 1
                continue
                
            modified = clean(parts[col_mod], 60) if len(parts) > col_mod else ""
            if modified:
                try:
                    datetime.fromisoformat(modified.replace("Z", "+00:00"))
                except ValueError:
                    modified = ""
                    
            signal = "OFF_MARKET_TAG" if tag_vn else "VN_CANDIDATE_MIRROR"
            quality = "COMMUNITY_UNVERIFIED" if tag_vn else "NEEDS_REVIEW"
            
            rec = {
                "gtin": code,
                "product_name": name,
                "brand": clean(parts[col_brand] if len(parts) > col_brand else "", 160),
                "quantity": clean(parts[col_quant] if len(parts) > col_quant else "", 80),
                "category_label": clean(parts[col_cat] if len(parts) > col_cat else "", 200),
                "market_country": "VN",
                "market_signal": signal,
                "source": "OPEN_FOOD_FACTS",
                "source_record_url": f"{OFF}/product/{code}",
                "evidence_url": f"{OFF}/product/{code}",
                "license_id": "ODbL-1.0",
                "quality_status": quality,
                "source_modified_at": modified,
            }
            writer.writerow(rec)
            scanned.add(key)
            if tag_vn:
                counts["written_tag_vn"] += 1
            else:
                counts["written_893_mirror"] += 1

    total_written = counts["written_tag_vn"] + counts["written_893_mirror"]
    print(f"Extraction complete! Total written: {total_written}")
    print(json.dumps(counts, indent=2))

if __name__ == "__main__":
    main()
