-- psql only. Default: preflight and ROLLBACK, no persistent changes.
-- Apply after separate approval: psql ... -v apply=true -f this-file.sql
-- Roll back after separate approval: psql ... -v restore=true -f this-file.sql
\set ON_ERROR_STOP on
\if :{?apply}
\else
  \set apply false
\endif
\if :{?restore}
\else
  \set restore false
\endif
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
DO $$ BEGIN
  IF current_database() <> 'covie_demo_recovered' THEN
    RAISE EXCEPTION 'Wrong database: expected covie_demo_recovered';
  END IF;
END $$;
\if :restore
  LOCK TABLE notifications IN SHARE ROW EXCLUSIVE MODE;
  DO $$ BEGIN
    IF (SELECT count(*) FROM demo_notification_backup_20261007) <> 24 THEN
      RAISE EXCEPTION 'Expected exactly 24 backup rows';
    END IF;
    IF EXISTS (SELECT 1 FROM demo_notification_backup_20261007 b
      LEFT JOIN notifications n ON n.id=b.id
      WHERE n.id IS NULL OR n.notification_type IS DISTINCT FROM b.new_type
        OR n.payload IS DISTINCT FROM b.new_payload) THEN
      RAISE EXCEPTION 'Notification changed after repair; refusing rollback';
    END IF;
  END $$;
  UPDATE notifications n SET notification_type=b.old_type, payload=b.old_payload
    FROM demo_notification_backup_20261007 b WHERE n.id=b.id;
  COMMIT;
\else
  \if :apply
    LOCK TABLE notifications, orders IN SHARE ROW EXCLUSIVE MODE;
  \endif
  CREATE TEMP TABLE expected_notifications (
    ordinal integer PRIMARY KEY, id uuid UNIQUE, order_id uuid UNIQUE,
    user_id uuid, expected_status text, new_type text
  ) ON COMMIT DROP;
  INSERT INTO expected_notifications VALUES
    (1, '497b84a6-e0f1-58f8-930c-f3737ba52f83'::uuid, '0c84c5fe-fd33-5bc1-ae8d-e69b50956c5b'::uuid, 'b5c14aa8-805c-5df9-bfe5-884d232973ce'::uuid, 'COMPLETED', 'ORDER_COMPLETED'),
    (2, 'a72da7c2-f6b7-51c9-b0a0-1810714d1c8a'::uuid, '256a32e6-5de0-598a-9caf-f9ad5362f75f'::uuid, 'fdc8270e-6104-539a-8f55-9aa218742f7c'::uuid, 'COMPLETED', 'ORDER_COMPLETED'),
    (3, '893429cf-2dda-548b-8068-35199a296c19'::uuid, '1388a2da-c119-5aed-a84b-68e2d1f62556'::uuid, '115aa24c-1f2d-533c-aa4f-285bd5a5a5f3'::uuid, 'COMPLETED', 'ORDER_COMPLETED'),
    (4, '47084eb9-3969-519d-a5c0-b5980407fd43'::uuid, '0cb76209-8e74-5104-987d-48172c56f3f2'::uuid, 'd94b1463-3284-5f97-8e15-da50c18a2b6f'::uuid, 'COMPLETED', 'ORDER_COMPLETED'),
    (5, '4f06d9ca-8d92-575d-9c0c-a89edb26bdd4'::uuid, 'b6642323-abe4-5e71-8d89-77c8d40bc573'::uuid, '2ce01d23-a1c8-514e-aa9d-fcdc91c6827a'::uuid, 'COMPLETED', 'ORDER_COMPLETED'),
    (6, 'f2b13300-5751-5656-953a-26431c2fcd31'::uuid, 'e2d1c0a0-aa24-5026-86a5-7e90192ecb16'::uuid, 'a7796a0c-2957-58f4-a977-c163febea37a'::uuid, 'COMPLETED', 'ORDER_COMPLETED'),
    (7, '8295e3b4-f689-5fdb-ae80-6aa95f030f95'::uuid, 'f6583020-baa8-5fcb-9acc-9722085ccd33'::uuid, '51a2a914-d81a-54f4-aa2a-58a66fe0fbd5'::uuid, 'COMPLETED', 'ORDER_COMPLETED'),
    (8, '0f4b1c66-c31a-5d60-9016-3523e694e485'::uuid, 'd6b3e974-cdd7-590a-87ce-4c4c462b6944'::uuid, '7251cd0e-67b6-5f05-9944-991d93033cba'::uuid, 'COMPLETED', 'ORDER_COMPLETED'),
    (9, 'fc927f7e-3295-5bb2-b54c-08100b4ce698'::uuid, '5e8b4c1c-bff5-509b-bedb-cb8c707f3e68'::uuid, 'b5c14aa8-805c-5df9-bfe5-884d232973ce'::uuid, 'COMPLETED', 'ORDER_COMPLETED'),
    (10, 'c8fa000e-d3ac-51b4-9be7-ef60d7733f9f'::uuid, 'd406b5f1-caa1-59cd-89a6-da4dcb8e7803'::uuid, 'fdc8270e-6104-539a-8f55-9aa218742f7c'::uuid, 'COMPLETED', 'ORDER_COMPLETED'),
    (11, '6a7d8e42-d07f-5bc4-b544-ede56ca737e6'::uuid, '200c3077-a311-524f-bfdc-d259722c7dda'::uuid, '115aa24c-1f2d-533c-aa4f-285bd5a5a5f3'::uuid, 'COMPLETED', 'ORDER_COMPLETED'),
    (12, '8e873166-c5b7-5dcb-9c37-d23d30525e11'::uuid, 'e8c9d9d3-b860-5ac7-b733-e4cabccec191'::uuid, 'd94b1463-3284-5f97-8e15-da50c18a2b6f'::uuid, 'COMPLETED', 'ORDER_COMPLETED'),
    (13, '013ab032-be30-51d5-ba6a-ac7a16ddacff'::uuid, '91cd3887-65e3-5cf6-9bae-948378b3121b'::uuid, '2ce01d23-a1c8-514e-aa9d-fcdc91c6827a'::uuid, 'COMPLETED', 'ORDER_COMPLETED'),
    (14, '6430367c-3049-538f-8a83-c122fd87214f'::uuid, 'a7c789fe-72d9-59d7-89ab-aaab9d782e91'::uuid, 'a7796a0c-2957-58f4-a977-c163febea37a'::uuid, 'COMPLETED', 'ORDER_COMPLETED'),
    (15, '1210b951-61fe-5959-8592-51efacc1570a'::uuid, 'fe446f1d-d306-512b-a065-88aab4ba30e4'::uuid, '51a2a914-d81a-54f4-aa2a-58a66fe0fbd5'::uuid, 'COMPLETED', 'ORDER_COMPLETED'),
    (16, '91d0f96c-6f59-5ea7-9ddb-05752456a074'::uuid, 'c63ae485-d60e-5615-930b-0e280162d1fd'::uuid, '7251cd0e-67b6-5f05-9944-991d93033cba'::uuid, 'COMPLETED', 'ORDER_COMPLETED'),
    (17, '3ea8b46b-702c-5222-9817-6fdc7cb4fe90'::uuid, 'f9746713-5d5f-5b40-9f60-a300c71e6ba0'::uuid, 'b5c14aa8-805c-5df9-bfe5-884d232973ce'::uuid, 'COMPLETED', 'ORDER_COMPLETED'),
    (18, '220783b7-c741-5639-9fd6-0b132000cb43'::uuid, '34f9b621-770d-55cf-a6a8-914181832c64'::uuid, 'fdc8270e-6104-539a-8f55-9aa218742f7c'::uuid, 'COMPLETED', 'ORDER_COMPLETED'),
    (19, '6913702a-d4fe-5a5a-9236-4c3d72a2450d'::uuid, '3acbfae1-85f2-5333-8273-fbc1484a94c3'::uuid, '115aa24c-1f2d-533c-aa4f-285bd5a5a5f3'::uuid, 'PENDING_CONFIRMATION', 'ORDER_NEW'),
    (20, 'bbda600a-9ea9-55d0-b26d-b253f28bfe02'::uuid, '7e3bb3d4-84b6-59cd-864a-57fe95490428'::uuid, 'd94b1463-3284-5f97-8e15-da50c18a2b6f'::uuid, 'CONFIRMED', 'ORDER_CONFIRMED'),
    (21, 'eda5d97a-4bb3-513e-b25e-18c22ae2b2ae'::uuid, '5032d799-4574-5409-8d90-6c30eb7d71c1'::uuid, '2ce01d23-a1c8-514e-aa9d-fcdc91c6827a'::uuid, 'OUT_FOR_DELIVERY', 'ORDER_FULFILLMENT_UPDATED'),
    (22, 'ab7b333d-9e3e-5faa-8568-4c8735c6608f'::uuid, 'e9fc4c3c-4cf5-5fa0-a0c6-a083da241511'::uuid, 'a7796a0c-2957-58f4-a977-c163febea37a'::uuid, 'READY_FOR_PICKUP', 'ORDER_FULFILLMENT_UPDATED'),
    (23, '77af49db-5eed-5af5-b11c-8866e9554e13'::uuid, '60e63039-faaf-52c9-9052-52b33d4c6312'::uuid, '51a2a914-d81a-54f4-aa2a-58a66fe0fbd5'::uuid, 'AWAITING_BUYER_CONFIRMATION', 'ORDER_FULFILLMENT_UPDATED'),
    (24, '075f14de-2eb3-51fc-a3f2-b56d0c3b70c4'::uuid, 'd1f8d242-152c-5525-991d-7de5729ab0aa'::uuid, '7251cd0e-67b6-5f05-9944-991d93033cba'::uuid, 'CANCELLED_BEFORE_CONFIRMATION', 'ORDER_CANCELLED');
  CREATE TEMP TABLE proposed_notifications ON COMMIT DROP AS
    SELECT e.*, n.notification_type AS old_type, n.payload AS old_payload,
      n.payload || jsonb_build_object('status', e.expected_status,
        'deepLink', '/orders/' || e.order_id::text) AS new_payload
    FROM expected_notifications e JOIN notifications n ON n.id=e.id;
  DO $$ BEGIN
    IF (SELECT count(*) FROM proposed_notifications) <> 24 THEN
      RAISE EXCEPTION 'Expected all 24 fixture IDs';
    END IF;
    IF EXISTS (SELECT 1 FROM expected_notifications e
      JOIN notifications n ON n.id=e.id LEFT JOIN orders o ON o.id=e.order_id
      WHERE n.notification_type <> 'ORDER_STATUS_CHANGED'
        OR n.user_id <> e.user_id OR n.related_entity_type IS DISTINCT FROM 'ORDER'
        OR n.related_entity_id IS DISTINCT FROM e.order_id
        OR n.payload->>'orderId' IS DISTINCT FROM e.order_id::text
        OR jsonb_typeof(n.payload) IS DISTINCT FROM 'object'
        OR o.id IS NULL OR o.buyer_user_id <> e.user_id
        OR o.status IS DISTINCT FROM e.expected_status
        OR o.order_code IS DISTINCT FROM 'CV-DEMO-' || lpad(e.ordinal::text,4,'0')) THEN
      RAISE EXCEPTION 'Fixture ownership, payload, type or order state differs; inspect manually';
    END IF;
    IF (SELECT count(*) FROM notifications WHERE notification_type='ORDER_STATUS_CHANGED') <> 24 THEN
      RAISE EXCEPTION 'Unexpected additional legacy notification rows';
    END IF;
  END $$;
  SELECT user_id, expected_status, old_type, new_type, count(*)
    FROM proposed_notifications GROUP BY 1,2,3,4 ORDER BY 1,2;
  SELECT id, order_id, new_type, new_payload FROM proposed_notifications ORDER BY ordinal;
  \if :apply
    -- Fresh backup is deliberately required; an existing table aborts this transaction.
    CREATE TABLE demo_notification_backup_20261007 AS
      SELECT id, old_type, old_payload, new_type, new_payload FROM proposed_notifications;
    ALTER TABLE demo_notification_backup_20261007 ADD PRIMARY KEY(id);
    DO $$ DECLARE affected integer; BEGIN
      UPDATE notifications n SET notification_type=p.new_type, payload=p.new_payload
        FROM proposed_notifications p WHERE n.id=p.id
          AND n.notification_type=p.old_type AND n.payload IS NOT DISTINCT FROM p.old_payload;
      GET DIAGNOSTICS affected = ROW_COUNT;
      IF affected <> 24 THEN RAISE EXCEPTION 'Concurrent change: updated % instead of 24', affected; END IF;
    END $$;
    COMMIT;
  \else
    ROLLBACK;
  \endif
\endif
