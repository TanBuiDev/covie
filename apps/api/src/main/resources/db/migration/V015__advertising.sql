CREATE TABLE ad_campaigns (
 id uuid PRIMARY KEY,
 merchant_id uuid REFERENCES merchant_profiles(id),
 owner_user_id uuid NOT NULL REFERENCES users(id),
 payload jsonb NOT NULL,
 placement varchar(30) NOT NULL CHECK (placement IN ('SIDEBAR_LEFT','HOME_HERO','IN_FEED','ITEM_DETAIL')),
 device varchar(10) NOT NULL CHECK (device IN ('DESKTOP','MOBILE','ALL')),
 status varchar(20) NOT NULL CHECK (status IN ('DRAFT','PENDING','APPROVED','REJECTED')),
 enabled boolean NOT NULL,
 priority integer NOT NULL CHECK (priority BETWEEN 0 AND 1000),
 starts_at timestamptz NOT NULL,
 ends_at timestamptz NOT NULL CHECK (ends_at > starts_at),
 review_reason varchar(500),
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL,
 updated_at timestamptz NOT NULL,
 deleted_at timestamptz
);
CREATE INDEX ad_campaign_delivery_idx ON ad_campaigns(placement,device,priority DESC) WHERE deleted_at IS NULL AND enabled AND status='APPROVED';
CREATE INDEX ad_campaign_merchant_idx ON ad_campaigns(merchant_id,created_at DESC) WHERE deleted_at IS NULL;
CREATE TABLE ad_deliveries (
 id uuid PRIMARY KEY,
 campaign_id uuid NOT NULL REFERENCES ad_campaigns(id),
 campaign_version bigint NOT NULL,
 issued_at timestamptz NOT NULL,
 expires_at timestamptz NOT NULL
);
CREATE INDEX ad_delivery_expiry_idx ON ad_deliveries(expires_at);
CREATE TABLE ad_events (
 event_id uuid PRIMARY KEY,
 campaign_id uuid NOT NULL REFERENCES ad_campaigns(id),
 delivery_key varchar(256) NOT NULL,
 event_type varchar(20) NOT NULL CHECK (event_type IN ('IMPRESSION','CLICK')),
 occurred_at timestamptz NOT NULL,
 UNIQUE(campaign_id,delivery_key,event_type)
);
CREATE INDEX ad_events_stats_idx ON ad_events(campaign_id,occurred_at,event_type);
