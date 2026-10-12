-- PROPOSED new Flyway migration for TanBuiDev/covie (main checked 2026-10-10).
-- Do not apply blindly if a new V017 already exists. Re-number after checking latest main.
-- Dedicated OFF-derived reference database; not merchant catalog, inventory, or pricing.
CREATE OR REPLACE FUNCTION barcode_reference_valid_gtin(p_code text)
RETURNS boolean
LANGUAGE plpgsql IMMUTABLE STRICT AS $$
DECLARE
  i integer;
  weighted integer := 0;
  value integer;
  digit integer;
BEGIN
  IF p_code !~ '^[0-9]+$' OR length(p_code) NOT IN (8,12,13,14) THEN
    RETURN false;
  END IF;
  FOR i IN 1 .. length(p_code)-1 LOOP
    value := substr(p_code, length(p_code)-i, 1)::integer;
    weighted := weighted + value * (CASE WHEN i % 2 = 1 THEN 3 ELSE 1 END);
  END LOOP;
  digit := (10 - (weighted % 10)) % 10;
  RETURN digit = right(p_code, 1)::integer;
END;
$$;

CREATE TABLE barcode_reference_products (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    gtin varchar(14) NOT NULL,
    gtin14 char(14) GENERATED ALWAYS AS (lpad(gtin, 14, '0')) STORED,
    product_name varchar(220) NOT NULL,
    brand varchar(160),
    quantity varchar(80),
    category_label varchar(200),
    market_country char(2) NOT NULL DEFAULT 'VN',
    market_signal varchar(40) NOT NULL,
    source varchar(50) NOT NULL,
    source_record_url text NOT NULL,
    evidence_url text NOT NULL,
    license_id varchar(40) NOT NULL,
    quality_status varchar(40) NOT NULL DEFAULT 'COMMUNITY_UNVERIFIED',
    source_modified_at timestamptz,
    first_seen_at timestamptz NOT NULL DEFAULT now(),
    last_seen_at timestamptz NOT NULL DEFAULT now(),
    is_active boolean NOT NULL DEFAULT true,
    CONSTRAINT ck_barcode_reference_valid_gtin CHECK (barcode_reference_valid_gtin(gtin)),
    CONSTRAINT ck_barcode_reference_product_name CHECK (length(btrim(product_name)) > 0),
    CONSTRAINT ck_barcode_reference_vn_market CHECK (market_country = 'VN'),
    CONSTRAINT ck_barcode_reference_signal CHECK (market_signal IN ('OFF_MARKET_TAG','VN_CANDIDATE_MIRROR')),
    CONSTRAINT ck_barcode_reference_off CHECK (source = 'OPEN_FOOD_FACTS' AND license_id = 'ODbL-1.0'),
    CONSTRAINT ck_barcode_reference_quality CHECK (quality_status IN ('COMMUNITY_UNVERIFIED','NEEDS_REVIEW')),
    CONSTRAINT uq_barcode_reference_source_gtin UNIQUE (source, gtin14)
);
CREATE INDEX ix_barcode_reference_gtin14 ON barcode_reference_products (gtin14) WHERE is_active;
COMMENT ON TABLE barcode_reference_products IS 'Open Food Facts ODbL reference data only; do not merge with private merchant/commerce tables as a derived database. Review ODbL obligations before public use.';
