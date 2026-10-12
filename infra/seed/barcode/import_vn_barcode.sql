-- Run from repository root: psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f infra/seed/barcode/import_vn_barcode.sql
-- Requires schema migration applied. Loads infra/seed/barcode/vn_barcode_reference.csv UTF8; idempotent.
BEGIN;
CREATE TEMP TABLE barcode_reference_csv_stage (
    gtin text, product_name text, brand text, quantity text, category_label text,
    market_country text, market_signal text, source text,
    source_record_url text, evidence_url text, license_id text,
    quality_status text, source_modified_at text
) ON COMMIT DROP;
\copy barcode_reference_csv_stage (gtin,product_name,brand,quantity,category_label,market_country,market_signal,source,source_record_url,evidence_url,license_id,quality_status,source_modified_at) FROM 'infra/seed/barcode/vn_barcode_reference.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8', NULL '');

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM barcode_reference_csv_stage
        WHERE NOT barcode_reference_valid_gtin(btrim(gtin))
           OR nullif(btrim(product_name), '') IS NULL
           OR char_length(btrim(product_name)) > 220
           OR char_length(coalesce(brand, '')) > 160
           OR char_length(coalesce(quantity, '')) > 80
           OR char_length(coalesce(category_label, '')) > 200
           OR market_country <> 'VN'
           OR market_signal NOT IN ('OFF_MARKET_TAG','VN_CANDIDATE_MIRROR')
           OR source <> 'OPEN_FOOD_FACTS'
           OR license_id <> 'ODbL-1.0'
           OR quality_status NOT IN ('COMMUNITY_UNVERIFIED','NEEDS_REVIEW')
           OR source_record_url IS NULL OR evidence_url IS NULL
    ) THEN
        RAISE EXCEPTION 'Invalid CSV input (barcode, missing name, source license or field length). Transaction rolled back.';
    END IF;
END;
$$;

WITH dedup AS (
    SELECT DISTINCT ON (lpad(btrim(gtin),14,'0'))
      btrim(gtin) AS gtin,
      btrim(product_name) AS product_name,
      nullif(btrim(brand),'') AS brand,
      nullif(btrim(quantity),'') AS quantity,
      nullif(btrim(category_label),'') AS category_label,
      btrim(market_country) AS market_country,
      btrim(market_signal) AS market_signal,
      btrim(source) AS source,
      btrim(source_record_url) AS source_record_url,
      btrim(evidence_url) AS evidence_url,
      btrim(license_id) AS license_id,
      btrim(quality_status) AS quality_status,
      nullif(btrim(source_modified_at),'')::timestamptz AS source_modified_at
    FROM barcode_reference_csv_stage
    ORDER BY lpad(btrim(gtin),14,'0'), nullif(btrim(source_modified_at),'')::timestamptz DESC NULLS LAST, gtin
)
INSERT INTO barcode_reference_products (
  gtin,product_name,brand,quantity,category_label,market_country,market_signal,
  source,source_record_url,evidence_url,license_id,quality_status,source_modified_at
)
SELECT gtin, product_name, brand, quantity, category_label, market_country, market_signal,
       source,source_record_url,evidence_url,license_id,quality_status,source_modified_at
FROM dedup
ON CONFLICT (source,gtin14) DO UPDATE SET
   gtin = EXCLUDED.gtin,
   product_name = EXCLUDED.product_name,
   brand = EXCLUDED.brand,
   quantity = EXCLUDED.quantity,
   category_label = EXCLUDED.category_label,
   market_country = EXCLUDED.market_country,
   market_signal = EXCLUDED.market_signal,
   source_record_url = EXCLUDED.source_record_url,
   evidence_url = EXCLUDED.evidence_url,
   license_id = EXCLUDED.license_id,
   quality_status = EXCLUDED.quality_status,
   source_modified_at = EXCLUDED.source_modified_at,
   last_seen_at = now(),
   is_active = true;
COMMIT;

-- Quick lookup example (SQL parameter should be bound by JDBC in actual API):
-- SELECT gtin, product_name,brand,quantity,source_record_url,quality_status
-- FROM barcode_reference_products WHERE is_active AND gtin14=lpad('8935217400393',14,'0');
