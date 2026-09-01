#!/usr/bin/env python3
"""Guarded seed/reset/verification lifecycle for CoVie demo-data-v1."""
from __future__ import annotations

import argparse
import gzip
import os
import shutil
import subprocess
import sys
from pathlib import Path
from urllib.parse import unquote, urlparse

ROOT = Path(__file__).resolve().parents[2]
SEED = ROOT / "infra/demo/seed-demo-data-v1.sql.gz"
RESET = ROOT / "infra/demo/reset-demo-data-v1.sql"
DEFAULT_JDBC = "jdbc:postgresql://localhost:5433/covie"
LOCAL_HOSTS = {"localhost", "127.0.0.1", "::1"}

EXPECTED = {
    "users": 22,
    "merchants": 10,
    "catalog_items": 45,
    "variants": 60,
    "location_variants": 55,
    "inventory_balances": 18,
    "carts": 11,
    "cart_items": 25,
    "checkout_sessions": 11,
    "orders": 26,
    "active_need_requests": 5,
    "need_responses": 10,
    "need_response_items": 20,
    "reviews": 18,
    "notifications": 24,
    "moderation_cases": 7,
}


def die(message: str) -> "NoReturn":
    print(f"demo-data: {message}", file=sys.stderr)
    raise SystemExit(2)


def resolve_connection(raw: str | None) -> tuple[str, dict[str, str]]:
    raw = raw or os.getenv("COVIE_DEMO_DB_URL") or os.getenv("DB_URL") or DEFAULT_JDBC
    if raw.startswith("jdbc:"):
        raw = raw[5:]
    parsed = urlparse(raw)
    if parsed.scheme not in {"postgresql", "postgres"}:
        die("DB URL must be postgresql://... or jdbc:postgresql://...")
    host = parsed.hostname or "localhost"
    if host not in LOCAL_HOSTS and os.getenv("COVIE_ALLOW_REMOTE_DEMO_DATA") != "1":
        die(
            f"refusing non-local database host {host!r}; set "
            "COVIE_ALLOW_REMOTE_DEMO_DATA=1 only for an intentional demo/staging database"
        )
    username = unquote(parsed.username) if parsed.username else os.getenv("DB_USERNAME", "covie")
    password = unquote(parsed.password) if parsed.password else os.getenv("DB_PASSWORD", "covie_local")
    port = parsed.port or 5432
    database = parsed.path.lstrip("/") or "covie"
    dsn = f"postgresql://{username}@{host}:{port}/{database}"
    env = os.environ.copy()
    env["PGPASSWORD"] = password
    return dsn, env


def require_mutation_opt_in() -> None:
    if os.getenv("COVIE_ALLOW_DEMO_DATA") != "1":
        die("mutation blocked; export COVIE_ALLOW_DEMO_DATA=1 first")


def psql(sql: bytes, db_url: str | None, *, guarded: bool = False, capture: bool = False) -> str:
    executable = shutil.which("psql")
    if not executable:
        die("psql is required and was not found on PATH")
    dsn, env = resolve_connection(db_url)
    if guarded:
        current = env.get("PGOPTIONS", "").strip()
        env["PGOPTIONS"] = f"{current} -c covie.demo.allow_seed=true".strip()
    proc = subprocess.run(
        [executable, "--no-psqlrc", "--set", "ON_ERROR_STOP=1", "--dbname", dsn],
        input=sql,
        env=env,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
    )
    if proc.returncode:
        if capture and proc.stderr:
            print(proc.stderr.decode(errors="replace"), file=sys.stderr, end="")
        raise SystemExit(proc.returncode)
    return proc.stdout.decode() if capture and proc.stdout else ""


def seed(db_url: str | None) -> None:
    require_mutation_opt_in()
    if not SEED.is_file():
        die(f"missing seed snapshot: {SEED}")
    with gzip.open(SEED, "rb") as fh:
        psql(fh.read(), db_url, guarded=True)
    print("CoVie demo-data-v1 seeded.")


def reset(db_url: str | None) -> None:
    require_mutation_opt_in()
    if not RESET.is_file():
        die(f"missing reset SQL: {RESET}")
    psql(RESET.read_bytes(), db_url, guarded=True)
    print("CoVie demo-data-v1 reset.")


def count_sql() -> bytes:
    return b"""\\pset tuples_only on
\\pset format unaligned
with demo_users as (
  select distinct user_id from user_identities
  where provider='EMAIL' and email_normalized like '%@demo.covie.invalid'
), demo_merchants as (
  select distinct mp.id from merchant_profiles mp
  where mp.slug like 'demo-%'
     or exists (select 1 from merchant_memberships mm join demo_users du on du.user_id=mm.user_id where mm.merchant_profile_id=mp.id)
), demo_locations as (
  select ml.id from merchant_locations ml join demo_merchants dm on dm.id=ml.merchant_profile_id
), demo_items as (
  select ci.id from catalog_items ci join demo_merchants dm on dm.id=ci.merchant_profile_id
), demo_variants as (
  select v.id from variants v join demo_items di on di.id=v.catalog_item_id
), demo_offers as (
  select lv.id from location_variants lv
  where lv.variant_id in (select id from demo_variants) or lv.merchant_location_id in (select id from demo_locations)
), demo_orders as (
  select o.id from orders o
  where o.buyer_user_id in (select user_id from demo_users)
     or o.merchant_profile_id in (select id from demo_merchants)
     or o.merchant_location_id in (select id from demo_locations)
), demo_needs as (
  select nr.id from need_requests nr where nr.buyer_user_id in (select user_id from demo_users)
), demo_responses as (
  select nr.id from need_responses nr
  where nr.need_request_id in (select id from demo_needs)
     or nr.merchant_profile_id in (select id from demo_merchants)
), demo_reviews as (
  select r.id from reviews r
  where r.buyer_user_id in (select user_id from demo_users)
     or r.catalog_item_id in (select id from demo_items)
     or r.merchant_profile_id in (select id from demo_merchants)
), pairs(metric, value) as (
  select 'users', count(*)::bigint from demo_users union all
  select 'merchants', count(*) from demo_merchants union all
  select 'catalog_items', count(*) from demo_items union all
  select 'variants', count(*) from demo_variants union all
  select 'location_variants', count(*) from demo_offers union all
  select 'inventory_balances', count(*) from inventory_balances where location_variant_id in (select id from demo_offers) union all
  select 'carts', count(*) from carts where user_id in (select user_id from demo_users) or merchant_location_id in (select id from demo_locations) union all
  select 'cart_items', count(*) from cart_items where cart_id in (select id from carts where user_id in (select user_id from demo_users) or merchant_location_id in (select id from demo_locations)) union all
  select 'checkout_sessions', count(*) from checkout_sessions where user_id in (select user_id from demo_users) union all
  select 'orders', count(*) from demo_orders union all
  select 'active_need_requests', count(*) from need_requests where id in (select id from demo_needs) and request_status='ACTIVE' union all
  select 'need_responses', count(*) from demo_responses union all
  select 'need_response_items', count(*) from need_response_items where need_response_id in (select id from demo_responses) union all
  select 'reviews', count(*) from demo_reviews union all
  select 'notifications', count(*) from notifications where user_id in (select user_id from demo_users) union all
  select 'moderation_cases', count(*) from moderation_cases
   where reporter_user_id in (select user_id from demo_users)
      or assigned_admin_user_id in (select user_id from demo_users)
      or target_user_id in (select user_id from demo_users)
      or target_merchant_profile_id in (select id from demo_merchants)
      or target_catalog_item_id in (select id from demo_items)
      or target_need_request_id in (select id from demo_needs)
      or target_review_id in (select id from demo_reviews)
)
select metric || '=' || value from pairs order by metric;
"""


def status(db_url: str | None, *, verify: bool) -> None:
    raw = psql(count_sql(), db_url, capture=True)
    actual: dict[str, int] = {}
    for line in raw.splitlines():
        line = line.strip()
        if not line or "=" not in line:
            continue
        key, value = line.split("=", 1)
        actual[key] = int(value)
    for key in sorted(actual):
        marker = ""
        if key in EXPECTED:
            marker = " OK" if actual[key] == EXPECTED[key] else f" expected={EXPECTED[key]}"
        print(f"{key}={actual[key]}{marker}")
    if verify:
        missing = [key for key in EXPECTED if key not in actual]
        mismatched = {key: (actual.get(key), expected) for key, expected in EXPECTED.items() if actual.get(key) != expected}
        if missing or mismatched:
            die(f"verification failed: {mismatched}")
        print("CoVie demo-data-v1 verification passed.")


def main() -> None:
    parser = argparse.ArgumentParser(description="Seed/reset/verify the guarded CoVie demo-data-v1 fixture.")
    parser.add_argument("command", choices=["seed", "reset", "reseed", "status", "verify"])
    parser.add_argument(
        "--db-url",
        help="PostgreSQL/JDBC URL; defaults to COVIE_DEMO_DB_URL, DB_URL, then local CoVie DB",
    )
    args = parser.parse_args()
    if args.command == "seed":
        seed(args.db_url)
    elif args.command == "reset":
        reset(args.db_url)
    elif args.command == "reseed":
        reset(args.db_url)
        seed(args.db_url)
    elif args.command == "verify":
        status(args.db_url, verify=True)
    else:
        status(args.db_url, verify=False)


if __name__ == "__main__":
    main()
