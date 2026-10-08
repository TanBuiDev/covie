alter table service_fulfillments
    add column distance_meters integer null,
    add constraint ck_service_fulfillment_distance
        check (distance_meters is null or distance_meters >= 0);
