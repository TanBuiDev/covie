#!/usr/bin/env python3
"""Validate/upload/attach the immutable CoVie demo media pack."""
from __future__ import annotations

import argparse
import hashlib
import json
import mimetypes
import os
import re
import shutil
import subprocess
import sys
import uuid
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import quote, unquote, urlparse
from urllib.request import Request, urlopen

MANIFEST_SHA256 = "87e267134a880afc3b2de2e16bec3465d4de73cfff52cfc63663f4e0064214f7"
ASSET_COUNT, AI_COUNT, QR_COUNT = 172, 162, 10
LOCAL_HOSTS = {"localhost", "127.0.0.1", "::1"}
CONTACT_NS = uuid.UUID("f3567c08-a543-5e45-bb13-ec4b0827f8e5")
PURPOSES = {
    "USER_AVATAR",
    "MERCHANT_LOGO",
    "MERCHANT_COVER",
    "MERCHANT_CONTACT_QR",
    "CATALOG_IMAGE",
}


def die(msg: str) -> "NoReturn":
    print(f"import-demo-media: {msg}", file=sys.stderr)
    raise SystemExit(2)


def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def sqlq(value) -> str:
    return "'" + str(value).replace("'", "''") + "'"


def assets(manifest):
    if isinstance(manifest, list):
        return manifest
    for key in ("assets", "items", "media"):
        value = manifest.get(key)
        if isinstance(value, list):
            return value
    die("manifest has no assets array")


def ready_path(asset) -> str:
    value = asset.get("readyPath") or asset.get("ready_path")
    if not value and isinstance(asset.get("paths"), dict):
        value = asset["paths"].get("ready")
    if not value:
        die(f"missing readyPath for {asset.get('assetId', '?')}")
    return str(value)


def purpose(asset) -> str:
    value = str(asset.get("purpose") or "").upper().replace("-", "_").replace(" ", "_")
    aliases = {
        "AVATAR": "USER_AVATAR",
        "LOGO": "MERCHANT_LOGO",
        "COVER": "MERCHANT_COVER",
        "CONTACT_QR": "MERCHANT_CONTACT_QR",
        "QR": "MERCHANT_CONTACT_QR",
        "CATALOG": "CATALOG_IMAGE",
        "IMAGE": "CATALOG_IMAGE",
    }
    value = aliases.get(value, value)
    if value not in PURPOSES:
        die(f"invalid media purpose for {asset.get('assetId', '?')}: {value!r}")
    return value


def entity_key(asset, path: Path) -> str:
    for raw in (path.name, str(asset.get("assetId") or "")):
        parts = raw.split("__")
        if len(parts) >= 3 and parts[0] in {"usr", "mer", "cat"}:
            return parts[1]
    value = asset.get("entityKey") or asset.get("entity_key")
    if value:
        return str(value)
    die(f"cannot resolve entity key for {asset.get('assetId', path.name)}")


def api(url, method, token, body=None, headers=None):
    data = None if body is None else json.dumps(body).encode()
    request_headers = {"Authorization": f"Bearer {token}", "Accept": "application/json"}
    if data is not None:
        request_headers["Content-Type"] = "application/json"
    if headers:
        request_headers.update(headers)
    try:
        with urlopen(Request(url, data=data, method=method, headers=request_headers), timeout=60) as response:
            raw = response.read()
            return json.loads(raw) if raw else {}
    except HTTPError as exc:
        detail = exc.read().decode(errors="replace")[:500]
        die(f"{method} {url}: HTTP {exc.code}: {detail}")


def upload(base: str, token: str, path: Path, asset) -> str:
    mime = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
    created = api(
        base + "/api/v1/media/uploads",
        "POST",
        token,
        {"purpose": purpose(asset), "contentType": mime, "fileSize": path.stat().st_size},
    )
    media_id = str(created["mediaId"])
    headers = {str(k): str(v) for k, v in (created.get("requiredHeaders") or {}).items()}
    if not any(k.lower() == "content-type" for k in headers):
        headers["Content-Type"] = mime
    try:
        with path.open("rb") as f, urlopen(
            Request(
                str(created["uploadUrl"]),
                data=f.read(),
                method=str(created.get("method") or "PUT").upper(),
                headers=headers,
            ),
            timeout=120,
        ) as response:
            response.read()
    except HTTPError as exc:
        detail = exc.read().decode(errors="replace")[:500]
        die(f"storage upload {path.name}: HTTP {exc.code}: {detail}")
    completed = api(
        base + f"/api/v1/media/{quote(media_id)}/complete",
        "POST",
        token,
        headers={"Idempotency-Key": f"demo-media-{asset.get('assetId', media_id)}"},
    )
    if completed.get("mediaId") and str(completed["mediaId"]) != media_id:
        die(f"complete id mismatch for {path.name}")
    return media_id


def user_sel(key: str) -> str:
    key = key.lower().replace("_", "-")
    match = re.fullmatch(r"(buyer|seller|staff|admin)-?(\d+)", key)
    if match:
        key = f"{match.group(1)}-{int(match.group(2)):02d}"
    return (
        "(select user_id from user_identities where provider='EMAIL' and email_normalized="
        + sqlq(key + "@demo.covie.invalid")
        + " limit 1)"
    )


def merchant_sel(key: str) -> str:
    key = key.lower()
    values = [key] + ([] if key.startswith("demo-") else ["demo-" + key])
    return (
        "(select id from merchant_profiles where slug in ("
        + ",".join(sqlq(x) for x in values)
        + f") order by (slug={sqlq(key)}) desc limit 1)"
    )


def catalog_sel(key: str) -> str:
    key = key.lower()
    values = [key]
    if key.startswith("product_"):
        values.append(key[8:].replace("_", "-"))
    values = list(dict.fromkeys(values))
    return (
        "(select id from catalog_items where slug in ("
        + ",".join(sqlq(x) for x in values)
        + f") order by (slug={sqlq(key)}) desc limit 1)"
    )


def attach_sql(rows) -> str:
    out = [
        r"\set ON_ERROR_STOP on",
        "begin;",
        "do $$ begin if current_setting('covie.demo.allow_seed',true) is distinct from 'true' then raise exception 'demo media attach blocked'; end if; end $$;",
    ]
    for row in rows:
        p = row["purpose"]
        key = row["entityKey"]
        mid = f"{sqlq(row['mediaId'])}::uuid"
        if p == "USER_AVATAR":
            target = user_sel(key)
            out += [
                f"do $$ begin if {target} is null then raise exception 'missing demo user: {key}'; end if; end $$;",
                f"update users set avatar_media_id={mid},updated_at=now() where id={target};",
            ]
        elif p in {"MERCHANT_LOGO", "MERCHANT_COVER"}:
            target = merchant_sel(key)
            column = "logo_media_id" if p == "MERCHANT_LOGO" else "cover_media_id"
            out += [
                f"do $$ begin if {target} is null then raise exception 'missing demo merchant: {key}'; end if; end $$;",
                f"update merchant_profiles set {column}={mid},updated_at=now() where id={target};",
            ]
        elif p == "MERCHANT_CONTACT_QR":
            target = merchant_sel(key)
            contact_id = sqlq(str(uuid.uuid5(CONTACT_NS, "demo-contact-qr:" + key.lower()))) + "::uuid"
            out += [
                f"do $$ begin if {target} is null then raise exception 'missing demo merchant: {key}'; end if; end $$;",
                "insert into merchant_contact_methods(id,merchant_profile_id,merchant_location_id,contact_type,value,qr_media_id,is_primary,is_public,status,created_at,version) "
                + f"select {contact_id},mp.id,ml.id,'ZALO_QR',null,{mid},false,true,'ACTIVE',now(),0 from merchant_profiles mp "
                + "join lateral (select id from merchant_locations where merchant_profile_id=mp.id and location_status='ACTIVE' order by created_at,id limit 1) ml on true "
                + f"where mp.id={target} on conflict(id) do update set qr_media_id=excluded.qr_media_id,status='ACTIVE',is_public=true;",
            ]
        elif p == "CATALOG_IMAGE":
            target = catalog_sel(key)
            order = int(row.get("sortOrder") or 0)
            primary = "true" if row.get("primary") else "false"
            out += [
                f"do $$ begin if {target} is null then raise exception 'missing demo catalog item: {key}'; end if; end $$;",
                f"insert into catalog_item_media(catalog_item_id,media_asset_id,media_type,sort_order,is_primary) values({target},{mid},'IMAGE',{order},{primary}) "
                + "on conflict(catalog_item_id,media_asset_id) do update set sort_order=excluded.sort_order,is_primary=excluded.is_primary;",
            ]
    return "\n".join(out + ["commit;", ""])


def run_attach(sql: str, db_url: str | None) -> None:
    if os.getenv("COVIE_ALLOW_DEMO_DATA") != "1":
        die("--attach requires COVIE_ALLOW_DEMO_DATA=1")
    raw = db_url or os.getenv("COVIE_DEMO_DB_URL") or os.getenv("DB_URL") or "jdbc:postgresql://localhost:5433/covie"
    if raw.startswith("jdbc:"):
        raw = raw[5:]
    parsed = urlparse(raw)
    host = parsed.hostname or "localhost"
    if host not in LOCAL_HOSTS and os.getenv("COVIE_ALLOW_REMOTE_DEMO_DATA") != "1":
        die(f"refusing non-local DB {host!r}")
    executable = shutil.which("psql")
    if not executable:
        die("psql is required for --attach")
    user = unquote(parsed.username) if parsed.username else os.getenv("DB_USERNAME", "covie")
    password = unquote(parsed.password) if parsed.password else os.getenv("DB_PASSWORD", "covie_local")
    dsn = f"postgresql://{user}@{host}:{parsed.port or 5432}/{parsed.path.lstrip('/') or 'covie'}"
    env = os.environ.copy()
    env["PGPASSWORD"] = password
    env["PGOPTIONS"] = (env.get("PGOPTIONS", "") + " -c covie.demo.allow_seed=true").strip()
    rc = subprocess.run(
        [executable, "--no-psqlrc", "--set", "ON_ERROR_STOP=1", "--dbname", dsn],
        input=sql.encode(),
        env=env,
    ).returncode
    if rc:
        raise SystemExit(rc)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pack", required=True, type=Path)
    parser.add_argument("--api-base", default=os.getenv("COVIE_API_BASE_URL", "http://localhost:8080"))
    parser.add_argument("--token", default=os.getenv("COVIE_ACCESS_TOKEN"))
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--attach", action="store_true")
    parser.add_argument("--db-url")
    args = parser.parse_args()

    pack = args.pack.resolve()
    manifest_path = pack / "manifest.json"
    if not manifest_path.is_file() or digest(manifest_path) != MANIFEST_SHA256:
        die("manifest missing or SHA-256 mismatch")

    source = assets(load(manifest_path))
    if len(source) != ASSET_COUNT:
        die(f"expected {ASSET_COUNT} assets, found {len(source)}")

    prepared = []
    for asset in source:
        path = (pack / ready_path(asset)).resolve()
        if (pack != path and pack not in path.parents) or not path.is_file():
            die(f"missing/unsafe ready asset: {path}")
        prepared.append((asset, path, purpose(asset), entity_key(asset, path)))

    qr_count = sum(p == "MERCHANT_CONTACT_QR" for _, _, p, _ in prepared)
    if (len(prepared) - qr_count, qr_count) != (AI_COUNT, QR_COUNT):
        die(f"expected {AI_COUNT} AI + {QR_COUNT} QR assets")
    print(f"validated {len(prepared)} assets ({AI_COUNT} AI + {QR_COUNT} QR)")
    if args.dry_run:
        return
    if not args.token:
        die("COVIE_ACCESS_TOKEN or --token is required")

    map_path = pack / "media-map.json"
    existing = {}
    if map_path.exists():
        existing = {
            str(row["assetId"]): row
            for row in load(map_path)
            if row.get("assetId") and row.get("mediaId")
        }

    rows = []
    for index, (asset, path, p, key) in enumerate(prepared, 1):
        asset_id = str(asset.get("assetId") or path.name)
        row = existing.get(asset_id) if not args.force else None
        if not row:
            row = {
                "assetId": asset_id,
                "mediaId": upload(args.api_base.rstrip("/"), args.token, path, asset),
                "entityKey": key,
                "purpose": p,
                "sortOrder": asset.get("sortOrder", 0),
                "primary": bool(asset.get("primary", False)),
                "readyPath": str(path.relative_to(pack)),
                "sha256": digest(path),
            }
            existing[asset_id] = row
        rows.append(row)
        map_path.write_text(
            json.dumps(list(existing.values()), ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        print(f"[{index:03d}/{len(prepared)}] {asset_id} -> {row['mediaId']}")

    sql = attach_sql(rows)
    attach_path = pack / "attach-media.sql"
    attach_path.write_text(sql, encoding="utf-8")
    print(f"wrote {map_path} and {attach_path}")
    if args.attach:
        run_attach(sql, args.db_url)
        print("media attached")


if __name__ == "__main__":
    main()
