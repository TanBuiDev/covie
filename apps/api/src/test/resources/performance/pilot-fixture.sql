-- Synthetic deterministic fixture. Run only inside a disposable test transaction.
insert into users(id,display_name,account_status,created_at,updated_at,version)
select md5('pilot-user-'||g)::uuid,'Pilot user '||g,'ACTIVE',now(),now(),0 from generate_series(1,1000) g;

insert into location_areas(id,area_type,name,slug,geo_boundary,status,created_at,updated_at)
select md5('pilot-area-'||g)::uuid,'WARD','Pilot area '||g,'pilot-area-'||g,
ST_Multi(ST_MakeEnvelope(106.6+((g-1)%10)*0.02,10.7+((g-1)/10)*0.01,
106.62+((g-1)%10)*0.02,10.71+((g-1)/10)*0.01,4326))::geography,
case when g%10=0 then 'INACTIVE' else 'ACTIVE' end,now(),now() from generate_series(1,100) g;

insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at,version)
select md5('pilot-merchant-'||g)::uuid,'Pilot merchant '||g,'pilot-merchant-'||g,
case when g%10=0 then 'PAUSED' else 'ACTIVE' end,'APPROVED',now(),now(),0 from generate_series(1,1000) g;

insert into merchant_locations(id,merchant_profile_id,name,address_text,location_area_id,geo_point,pin_source,timezone,
location_status,pickup_enabled,seller_delivery_enabled,service_enabled,currency_code,created_at,updated_at,version)
select md5('pilot-location-'||g)::uuid,md5('pilot-merchant-'||((g-1)%1000+1))::uuid,'Pilot location '||g,'Synthetic address',
md5('pilot-area-'||((g%100)/10+10*(g/500)+1))::uuid,ST_SetSRID(ST_MakePoint(106.6+(g%100)*0.002,10.7+(g/100)*0.002),4326)::geography,
'GPS','Asia/Ho_Chi_Minh',case when g%10=0 then 'PAUSED' else 'ACTIVE' end,true,true,false,'VND',now(),now(),0
from generate_series(1,2000) g;

insert into categories(id,name,slug,catalog_type,status,sort_order,version)
select md5('pilot-category-'||g)::uuid,'Pilot category '||g,'pilot-category-'||g,'PRODUCT','ACTIVE',g,0 from generate_series(1,10) g;

insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,listing_status,
moderation_status,search_vector,created_at,updated_at,version)
select md5('pilot-item-'||g)::uuid,md5('pilot-merchant-'||((g-1)%1000+1))::uuid,'PRODUCT','STANDARD',
md5('pilot-category-'||((g-1)%10+1))::uuid,'Pilot rice '||g,'pilot-item-'||g,
case when g%10=0 then 'PAUSED' else 'ACTIVE' end,'APPROVED',to_tsvector('simple','Pilot rice '||g),now(),now(),0
from generate_series(1,20000) g;

insert into variants(id,catalog_item_id,variant_name,is_default,variant_status,created_at,updated_at,version)
select md5('pilot-variant-'||g)::uuid,md5('pilot-item-'||(case when g<=20000 then g else g-20000 end))::uuid,
'Pilot variant '||g,g<=20000,case when g%10=0 then 'PAUSED' else 'ACTIVE' end,now(),now(),0 from generate_series(1,30000) g;

insert into location_variants(id,merchant_location_id,variant_id,price,currency_code,is_available,inventory_mode,created_at,updated_at,version)
select md5('pilot-offer-'||g)::uuid,
md5('pilot-location-'||(((case when g<=20000 then g when g<=30000 then g-20000 else g-30000 end)-1)%1000+1+case when g>30000 then 1000 else 0 end))::uuid,
md5('pilot-variant-'||(case when g<=30000 then g else g-30000 end))::uuid,10000+(g%100)*100,'VND',g%10<>0,'UNTRACKED',now(),now(),0
from generate_series(1,40000) g;

insert into need_requests(id,buyer_user_id,need_type,category_id,title,query_text,requested_quantity,location_area_id,
geo_point,location_snapshot,search_radius_meters,request_status,expires_at,created_at,updated_at,closed_at)
select md5('pilot-need-'||g)::uuid,md5('pilot-user-'||((g-1)%1000+1))::uuid,'PRODUCT',md5('pilot-category-'||((g-1)%10+1))::uuid,
'Pilot rice need '||g,'rice',1,md5('pilot-area-'||((g%100)/10+10*(((g/100)%20)/5)+1))::uuid,
ST_SetSRID(ST_MakePoint(106.6+(g%100)*0.002,10.7+((g/100)%20)*0.002),4326)::geography,'{}'::jsonb,5000,
case when (g/100)%10<3 then 'EXPIRED' else 'ACTIVE' end,
case when (g/100)%10<3 then now()-interval '2 days' else now()+interval '1 day' end,now()-interval '3 days',now(),
case when (g/100)%10<3 then now()-interval '2 days' else null end from generate_series(1,10000) g;

insert into orders(id,order_code,order_type,buyer_user_id,merchant_profile_id,merchant_location_id,status,source_type,
payment_method,subtotal,shipping_fee_type,shipping_fee,known_total,total_amount,currency_code,confirmation_expires_at,
completed_at,created_at,updated_at,version)
select md5('pilot-order-'||g)::uuid,'PILOT-'||g,'PRODUCT',md5('pilot-user-'||((g-1)%1000+1))::uuid,
md5('pilot-merchant-'||((g-1)%1000+1))::uuid,md5('pilot-location-'||((g-1)%1000+1))::uuid,
case when g<=100 then 'PENDING_CONFIRMATION' else 'COMPLETED' end,'MARKETPLACE','COD',10000,'FREE',0,10000,10000,'VND',
case when g<=100 then now()-interval '1 minute' else null end,
case when g>100 then now()-g*interval '1 second' else null end,now()-g*interval '1 second'-interval '1 day',now(),0
from generate_series(1,50100) g;

insert into order_events(id,order_id,event_type,actor_context,created_at)
select md5('pilot-event-'||g)::uuid,md5('pilot-order-'||((g-1)%50000+101))::uuid,
case (g-1)/50000 when 0 then 'ORDER_CREATED' when 1 then 'SELLER_CONFIRMED'
when 2 then 'SELLER_MARKED_READY_FOR_PICKUP' else 'BUYER_CONFIRMED_RECEIVED' end,'SYSTEM',
now()-((g-1)%50000+101)*interval '1 second'-interval '1 day'+((g-1)/50000)*interval '1 second'
from generate_series(1,200000) g
union all
select md5('pilot-pending-event-'||g)::uuid,md5('pilot-order-'||g)::uuid,'ORDER_CREATED','SYSTEM',
now()-g*interval '1 second'-interval '1 day' from generate_series(1,100) g;

insert into notifications(id,user_id,notification_type,title,body,read_at,created_at)
select md5('pilot-notification-'||g)::uuid,md5('pilot-user-'||((g-1)%1000+1))::uuid,'ORDER_NEW','Pilot','Synthetic fixture',
case when g%3=0 then now() else null end,now()-g*interval '1 second' from generate_series(1,100000) g;

insert into outbox_events(id,event_type,aggregate_type,aggregate_id,payload,occurred_at,status,attempts,next_attempt_at,published_at)
select md5('pilot-outbox-'||g)::uuid,'PILOT_FIXTURE','ORDER',md5('pilot-order-'||g)::uuid,'{}'::jsonb,now()-g*interval '1 second',
case when g<=50 then 'PENDING' when g<=100 then 'FAILED' else 'PUBLISHED' end,
case when g>50 and g<=100 then 1 else 0 end,case when g>50 and g<=100 then now()-interval '1 minute' else null end,
case when g>100 then now() else null end from generate_series(1,50000) g;

insert into carts(id,user_id,merchant_location_id,cart_type,cart_status,created_at,updated_at,version)
select md5('pilot-cart-'||g)::uuid,md5('pilot-user-'||((g-1)%1000+1))::uuid,
md5('pilot-location-'||((g-1)%1000+1))::uuid,'PRODUCT',case when g<=1000 then 'ACTIVE' else 'ABANDONED' end,
now()-g*interval '1 second',now()-g*interval '1 second',0 from generate_series(1,10000) g;

insert into delivery_coverages(id,merchant_location_id,coverage_type,radius_enabled,radius_meters,area_enabled,coverage_status,created_at,updated_at,version)
select md5('pilot-coverage-'||g)::uuid,md5('pilot-location-'||g)::uuid,'PRODUCT_DELIVERY',g<=1000,
case when g<=1000 then 300 else null end,g>1000,'ACTIVE',now(),now(),0 from generate_series(1,2000) g;
insert into delivery_coverage_areas(delivery_coverage_id,location_area_id)
select md5('pilot-coverage-'||g)::uuid,md5('pilot-area-1')::uuid from generate_series(1001,2000) g;

analyze;

-- Historical fulfillments, with due and future awaiting-confirmation cohorts.
update orders set order_type='SERVICE',shipping_fee_type='NOT_APPLICABLE',shipping_fee=null
where id in (select md5('pilot-order-'||g)::uuid from generate_series(25101,50100) g);
update orders set status='AWAITING_BUYER_CONFIRMATION',completed_at=null
where id in (select md5('pilot-order-'||g)::uuid from generate_series(101,300) g
union all select md5('pilot-order-'||g)::uuid from generate_series(25101,25300) g);
insert into product_fulfillments(order_id,fulfillment_method,pickup_location_id,pickup_location_snapshot,buyer_contact_snapshot,merchant_contact_snapshot,auto_complete_at)
select md5('pilot-order-'||g)::uuid,'PICKUP',md5('pilot-location-'||((g-1)%1000+1))::uuid,
'{}'::jsonb,'{}'::jsonb,'{}'::jsonb,
case when g between 201 and 300 then now()+interval '1 day' else now()-g*interval '1 second' end
from generate_series(101,25100) g;
insert into service_fulfillments(order_id,service_address_snapshot,provider_location_snapshot,buyer_contact_snapshot,merchant_contact_snapshot,created_at,auto_complete_at)
select md5('pilot-order-'||g)::uuid,'{}'::jsonb,'{}'::jsonb,'{}'::jsonb,'{}'::jsonb,now()-interval '1 day',
case when g between 25201 and 25300 then now()+interval '1 day' else now()-g*interval '1 second' end
from generate_series(25101,50100) g;
analyze;
