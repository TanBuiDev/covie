#!/usr/bin/env python3
"""Validate/upload/attach the immutable CoVie demo media pack."""
from __future__ import annotations
import argparse, hashlib, json, mimetypes, os, re, shutil, subprocess, sys, uuid
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import quote, unquote, urlparse
from urllib.request import Request, urlopen

MANIFEST_SHA256 = "0cca53918b5b8b1ec129b4a0220a54a5805d95925105062c27c0b5d726cb8f01"
ASSET_COUNT, AI_COUNT, QR_COUNT = 172, 162, 10
LOCAL_HOSTS = {"localhost", "127.0.0.1", "::1"}
CONTACT_NS = uuid.UUID("f3567c08-a543-5e45-bb13-ec4b0827f8e5")
PURPOSES = {"USER_AVATAR", "MERCHANT_LOGO", "MERCHANT_COVER", "MERCHANT_CONTACT_QR", "CATALOG_IMAGE"}


def die(msg):
    print(f"import-demo-media: {msg}", file=sys.stderr); raise SystemExit(2)


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""): h.update(chunk)
    return h.hexdigest()


def load(path): return json.loads(path.read_text(encoding="utf-8"))
def sqlq(value): return "'" + str(value).replace("'", "''") + "'"


def assets(manifest):
    if isinstance(manifest, list): return manifest
    for key in ("assets", "items", "media"):
        if isinstance(manifest.get(key), list): return manifest[key]
    die("manifest has no assets array")


def ready_path(asset):
    value = asset.get("readyPath") or asset.get("ready_path")
    if not value and isinstance(asset.get("paths"), dict): value = asset["paths"].get("ready")
    if not value: die(f"missing readyPath for {asset.get('assetId', '?')}")
    return str(value)


def purpose(asset):
    value = str(asset.get("purpose") or "").upper().replace("-", "_").replace(" ", "_")
    aliases = {"AVATAR":"USER_AVATAR", "LOGO":"MERCHANT_LOGO", "COVER":"MERCHANT_COVER", "CONTACT_QR":"MERCHANT_CONTACT_QR", "QR":"MERCHANT_CONTACT_QR", "CATALOG":"CATALOG_IMAGE", "IMAGE":"CATALOG_IMAGE"}
    value = aliases.get(value, value)
    if value not in PURPOSES: die(f"invalid media purpose for {asset.get('assetId', '?')}: {value!r}")
    return value


def entity_key(asset, path):
    # Ready filenames encode DB keys: usr__buyer-01, mer__bep-nha-moc,
    # cat__prd-001-com-tam-suon-nuong. Prefer them over semantic entityKey.
    for raw in (path.name, str(asset.get("assetId") or "")):
        parts = raw.split("__")
        if len(parts) >= 3 and parts[0] in {"usr", "mer", "cat"}: return parts[1]
    value = asset.get("entityKey") or asset.get("entity_key")
    if value: return str(value)
    die(f"cannot resolve entity key for {asset.get('assetId', path.name)}")


def api(url, method, token, body=None, headers=None):
    data = None if body is None else json.dumps(body).encode()
    h = {"Authorization": f"Bearer {token}", "Accept": "application/json"}
    if data is not None: h["Content-Type"] = "application/json"
    if headers: h.update(headers)
    try:
        with urlopen(Request(url, data=data, method=method, headers=h), timeout=60) as r:
            raw = r.read(); return json.loads(raw) if raw else {}
    except HTTPError as e: die(f"{method} {url}: HTTP {e.code}: {e.read().decode(errors='replace')[:500]}")


def upload(base, token, path, asset):
    mime = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
    created = api(base + "/api/v1/media/uploads", "POST", token, {"purpose": purpose(asset), "contentType": mime, "fileSize": path.stat().st_size})
    mid = str(created["mediaId"]); headers = {str(k): str(v) for k,v in (created.get("requiredHeaders") or {}).items()}
    if not any(k.lower() == "content-type" for k in headers): headers["Content-Type"] = mime
    try:
        with path.open("rb") as f, urlopen(Request(str(created["uploadUrl"]), data=f.read(), method=str(created.get("method") or "PUT").upper(), headers=headers), timeout=120) as r: r.read()
    except HTTPError as e: die(f"storage upload {path.name}: HTTP {e.code}: {e.read().decode(errors='replace')[:500]}")
    completed = api(base + f"/api/v1/media/{quote(mid)}/complete", "POST", token, headers={"Idempotency-Key": f"demo-media-{asset.get('assetId', mid)}"})
    if completed.get("mediaId") and str(completed["mediaId"]) != mid: die(f"complete id mismatch for {path.name}")
    return mid


def user_sel(key):
    key = key.lower().replace("_", "-")
    m = re.fullmatch(r"(buyer|seller|staff|admin)-?(\d+)", key)
    if m: key = f"{m.group(1)}-{int(m.group(2)):02d}"
    return f"(select user_id from user_identities where provider='EMAIL' and email_normalized={sqlq(key+'@demo.covie.invalid')} limit 1)"


def merchant_sel(key):
    key = key.lower(); vals = [key] + ([] if key.startswith("demo-") else ["demo-" + key])
    return f"(select id from merchant_profiles where slug in ({','.join(sqlq(x) for x in vals)}) order by (slug={sqlq(key)}) desc limit 1)"


def catalog_sel(key):
    key = key.lower(); vals = [key]
    if key.startswith("product_"): vals += [key[8:].replace("_", "-")]
    return f"(select id from catalog_items where slug in ({','.join(sqlq(x) for x in dict.fromkeys(vals))}) order by (slug={sqlq(key)}) desc limit 1)"


def attach_sql(rows):
    out = [r"\set ON_ERROR_STOP on", "begin;", "do $$ begin if current_setting('covie.demo.allow_seed',true) is distinct from 'true' then raise exception 'demo media attach blocked'; end if; end $$;"]
    for row in rows:
        p, key, mid = row["purpose"], row["entityKey"], f"{sqlq(row['mediaId'])}::uuid"
        if p == "USER_AVATAR":
            target = user_sel(key); out += [f"do $$ begin if {target} is null then raise exception 'missing demo user: {key}'; end if; end $$;", f"update users set avatar_media_id={mid},updated_at=now() where id={target};"]
        elif p in {"MERCHANT_LOGO", "MERCHANT_COVER"}:
            target = merchant_sel(key); col = "logo_media_id" if p == "MERCHANT_LOGO" else "cover_media_id"
            out += [f"do $$ begin if {target} is null then raise exception 'missing demo merchant: {key}'; end if; end $$;", f"update merchant_profiles set {col}={mid},updated_at=now() where id={target};"]
        elif p == "MERCHANT_CONTACT_QR":
            target = merchant_sel(key); cid = sqlq(str(uuid.uuid5(CONTACT_NS, "demo-contact-qr:" + key.lower()))) + "::uuid"
            out += [f"do $$ begin if {target} is null then raise exception 'missing demo merchant: {key}'; end if; end $$;", "insert into merchant_contact_methods(id,merchant_profile_id,merchant_location_id,contact_type,value,qr_media_id,is_primary,is_public,status,created_at,version) " + f"select {cid},mp.id,ml.id,'ZALO_QR',null,{mid},false,true,'ACTIVE',now(),0 from merchant_profiles mp join lateral (select id from merchant_locations where merchant_profile_id=mp.id and location_status='ACTIVE' order by created_at,id limit 1) ml on true where mp.id={target} on conflict(id) do update set qr_media_id=excluded.qr_media_id,status='ACTIVE',is_public=true;"]
        elif p == "CATALOG_IMAGE":
            target = catalog_sel(key); order = int(row.get("sortOrder") or 0); primary = "true" if row.get("primary") else "false"
            out += [f"do $$ begin if {target} is null then raise exception 'missing demo catalog item: {key}'; end if; end $$;", f"insert into catalog_item_media(catalog_item_id,media_asset_id,media_type,sort_order,is_primary) values({target},{mid},'IMAGE',{order},{primary}) on conflict(catalog_item_id,media_asset_id) do update set sort_order=excluded.sort_order,is_primary=excluded.is_primary;"]
    return "\n".join(out + ["commit;", ""])


def run_attach(sql, db_url):
    if os.getenv("COVIE_ALLOW_DEMO_DATA") != "1": die("--attach requires COVIE_ALLOW_DEMO_DATA=1")
    raw = db_url or os.getenv("COVIE_DEMO_DB_URL") or os.getenv("DB_URL") or "jdbc:postgresql://localhost:5433/covie"
    if raw.startswith("jdbc:"): raw = raw[5:]
    p = urlparse(raw); host = p.hostname or "localhost"
    if host not in LOCAL_HOSTS and os.getenv("COVIE_ALLOW_REMOTE_DEMO_DATA") != "1": die(f"refusing non-local DB {host!r}")
    exe = shutil.which("psql")
    if not exe: die("psql is required for --attach")
    user = unquote(p.username) if p.username else os.getenv("DB_USERNAME", "covie"); password = unquote(p.password) if p.password else os.getenv("DB_PASSWORD", "covie_local")
    dsn = f"postgresql://{user}@{host}:{p.port or 5432}/{p.path.lstrip('/') or 'covie'}"; env = os.environ.copy(); env["PGPASSWORD"] = password; env["PGOPTIONS"] = (env.get("PGOPTIONS", "") + " -c covie.demo.allow_seed=true").strip()
    rc = subprocess.run([exe,"--no-psqlrc","--set","ON_ERROR_STOP=1","--dbname",dsn], input=sql.encode(), env=env).returncode
    if rc: raise SystemExit(rc)


def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--pack", required=True, type=Path); ap.add_argument("--api-base", default=os.getenv("COVIE_API_BASE_URL","http://localhost:8080")); ap.add_argument("--token", default=os.getenv("COVIE_ACCESS_TOKEN")); ap.add_argument("--dry-run", action="store_true"); ap.add_argument("--force", action="store_true"); ap.add_argument("--attach", action="store_true"); ap.add_argument("--db-url"); args = ap.parse_args()
    pack = args.pack.resolve(); manifest_path = pack / "manifest.json"
    if not manifest_path.is_file() or digest(manifest_path) != MANIFEST_SHA256: die("manifest missing or SHA-256 mismatch")
    source = assets(load(manifest_path))
    if len(source) != ASSET_COUNT: die(f"expected {ASSET_COUNT} assets, found {len(source)}")
    prepared = []
    for asset in source:
        path = (pack / ready_path(asset)).resolve()
        if (pack != path and pack not in path.parents) or not path.is_file(): die(f"missing/unsafe ready asset: {path}")
        prepared.append((asset, path, purpose(asset), entity_key(asset, path)))
    qr = sum(p == "MERCHANT_CONTACT_QR" for _,_,p,_ in prepared)
    if (len(prepared)-qr, qr) != (AI_COUNT, QR_COUNT): die(f"expected {AI_COUNT} AI + {QR_COUNT} QR assets")
    print(f"validated {len(prepared)} assets ({AI_COUNT} AI + {QR_COUNT} QR)")
    if args.dry_run: return
    if not args.token: die("COVIE_ACCESS_TOKEN or --token is required")
    map_path = pack / "media-map.json"; existing = {}
    if map_path.exists(): existing = {str(x["assetId"]): x for x in load(map_path) if x.get("assetId") and x.get("mediaId")}
    rows = []
    for i,(asset,path,p,key) in enumerate(prepared,1):
        aid = str(asset.get("assetId") or path.name); row = existing.get(aid) if not args.force else None
        if not row:
            row = {"assetId":aid,"mediaId":upload(args.api_base.rstrip('/'),args.token,path,asset),"entityKey":key,"purpose":p,"sortOrder":asset.get("sortOrder",0),"primary":bool(asset.get("primary",False)),"readyPath":str(path.relative_to(pack)),"sha256":digest(path)}; existing[aid]=row
        rows.append(row); map_path.write_text(json.dumps(list(existing.values()),ensure_ascii=False,indent=2)+"\n",encoding="utf-8"); print(f"[{i:03d}/{len(prepared)}] {aid} -> {row['mediaId']}")
    sql = attach_sql(rows); attach = pack / "attach-media.sql"; attach.write_text(sql,encoding="utf-8"); print(f"wrote {map_path} and {attach}")
    if args.attach: run_attach(sql,args.db_url); print("media attached")

if __name__ == "__main__": main()
