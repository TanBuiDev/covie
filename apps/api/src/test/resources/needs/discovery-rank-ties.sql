-- Synthetic deterministic fixture. Run only inside a disposable test transaction.
insert into users(id,display_name,account_status,created_at,updated_at,version)
select md5('pilot-user-'||g)::uuid,'Pilot user '||g,'ACTIVE',now(),now(),0 from generate_series(1,1) g;

insert into location_areas(id,area_type,name,slug,geo_boundary,status,created_at,updated_at)
select md5('pilot-area-'||g)::uuid,'WARD','Pilot area '||g,'pilot-area-'||g,
ST_Multi(ST_MakeEnvelope(106.6+((g-1)%10)*0.02,10.7+((g-1)/10)*0.01,
106.62+((g-1)%10)*0.02,10.71+((g-1)/10)*0.01,4326))::geography,
case when g%10=0 then 'INACTIVE' else 'ACTIVE' end,now(),now() from generate_series(1,1) g;

insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at,version)
select md5('pilot-merchant-'||g)::uuid,'Pilot merchant '||g,'pilot-merchant-'||g,
case when g%10=0 then 'PAUSED' else 'ACTIVE' end,'APPROVED',now(),now(),0 from generate_series(1,1) g;

insert into merchant_locations(id,merchant_profile_id,name,address_text,location_area_id,geo_point,pin_source,timezone,
location_status,pickup_enabled,seller_delivery_enabled,service_enabled,currency_code,created_at,updated_at,version)
select md5('pilot-location-'||g)::uuid,md5('pilot-merchant-'||((g-1)%1000+1))::uuid,'Pilot location '||g,'Synthetic address',
md5('pilot-area-'||((g%100)/10+10*(g/500)+1))::uuid,ST_SetSRID(ST_MakePoint(106.6+(g%100)*0.002,10.7+(g/100)*0.002),4326)::geography,
'GPS','Asia/Ho_Chi_Minh',case when g%10=0 then 'PAUSED' else 'ACTIVE' end,true,true,false,'VND',now(),now(),0
from generate_series(1,1) g;

insert into categories(id,name,slug,catalog_type,status,sort_order,version)
select md5('pilot-category-'||g)::uuid,'Pilot category '||g,'pilot-category-'||g,'PRODUCT','ACTIVE',g,0 from generate_series(1,1) g;

insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,listing_status,
moderation_status,search_vector,created_at,updated_at,version)
select md5('pilot-item-'||g)::uuid,md5('pilot-merchant-'||((g-1)%1000+1))::uuid,'PRODUCT','STANDARD',
md5('pilot-category-'||((g-1)%10+1))::uuid,'Pilot rice '||g,'pilot-item-'||g,
case when g%10=0 then 'PAUSED' else 'ACTIVE' end,'APPROVED',to_tsvector('simple','Pilot rice '||g),now(),now(),0
from generate_series(1,1) g;

insert into variants(id,catalog_item_id,variant_name,is_default,variant_status,created_at,updated_at,version)
select md5('pilot-variant-'||g)::uuid,md5('pilot-item-'||(case when g<=20000 then g else g-20000 end))::uuid,
'Pilot variant '||g,g<=20000,case when g%10=0 then 'PAUSED' else 'ACTIVE' end,now(),now(),0 from generate_series(1,1) g;

insert into location_variants(id,merchant_location_id,variant_id,price,currency_code,is_available,inventory_mode,created_at,updated_at,version)
select md5('pilot-offer-'||g)::uuid,
md5('pilot-location-'||(((case when g<=20000 then g when g<=30000 then g-20000 else g-30000 end)-1)%1000+1+case when g>30000 then 1000 else 0 end))::uuid,
md5('pilot-variant-'||(case when g<=30000 then g else g-30000 end))::uuid,10000+(g%100)*100,'VND',g%10<>0,'UNTRACKED',now(),now(),0
from generate_series(1,1) g;

insert into need_requests(id,buyer_user_id,need_type,category_id,title,query_text,requested_quantity,location_area_id,
geo_point,location_snapshot,search_radius_meters,request_status,expires_at,created_at,updated_at,closed_at)
select md5('pilot-need-'||g)::uuid,md5('pilot-user-1')::uuid,'PRODUCT',md5('pilot-category-1')::uuid,
'Pilot rice need '||g,'rice',1,md5('pilot-area-1')::uuid,
ST_SetSRID(ST_MakePoint(106.602,10.7),4326)::geography,'{}'::jsonb,5000,
'ACTIVE',
now()+interval '1 day',now()-interval '3 days',now(),
null from generate_series(1,20) g;
