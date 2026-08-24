package vn.covie.notifications.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.catalog.model.InventoryMode;
import vn.covie.catalog.model.LocationVariantEntity;
import vn.covie.inventory.model.InventoryBalanceEntity;
import vn.covie.inventory.persistence.InventoryBalanceRepository;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.needs.model.NeedRequestEntity;
import vn.covie.needs.model.NeedType;
import vn.covie.needs.persistence.NeedRequestRepository;
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class JdbcNeedAudienceRepositoryIntegrationTest extends PostgresIntegrationTest {
    @Autowired NeedAudienceRepository audience; @Autowired NeedRequestRepository needs; @Autowired UserRepository users; @Autowired MerchantProfileRepository merchants; @Autowired MerchantLocationRepository locations;
    @Autowired LocationVariantRepository offers; @Autowired InventoryBalanceRepository inventory; @Autowired JdbcTemplate jdbc;

    @Test @Transactional
    void excludesTrackedOfferWithNoAvailableStock() {
        Instant now=Instant.now(); UUID buyer=UUID.randomUUID(), merchant=UUID.randomUUID(), category=UUID.randomUUID(), item=UUID.randomUUID(), variant=UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(buyer,"buyer-"+buyer,now)); merchants.saveAndFlush(MerchantProfileEntity.create(merchant,"Merchant","merchant-"+merchant,now));
        UUID location=locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(),merchant,"Main","1 Street",10.8,106.7,now)).getId();
        jdbc.update("insert into categories(id,name,slug,catalog_type,status,sort_order,version) values (?, 'Food', ?, 'PRODUCT','ACTIVE',0,0)",category,"food-"+category);
        jdbc.update("insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,listing_status,moderation_status,created_at,updated_at,version) values (?,?, 'PRODUCT','STANDARD',?,'Rice',?,'ACTIVE','APPROVED',?,?,0)",item,merchant,category,"rice-"+item,java.sql.Timestamp.from(now),java.sql.Timestamp.from(now));
        jdbc.update("insert into variants(id,catalog_item_id,variant_name,is_default,variant_status,created_at,updated_at,version) values (?,?, 'Default',true,'ACTIVE',?,?,0)",variant,item,java.sql.Timestamp.from(now),java.sql.Timestamp.from(now));
        UUID offer=offers.saveAndFlush(LocationVariantEntity.create(UUID.randomUUID(),location,variant,100,"VND",true,InventoryMode.TRACKED,null,now)).getId(); inventory.saveAndFlush(InventoryBalanceEntity.zero(offer,now));
        var factory=new org.locationtech.jts.geom.GeometryFactory(new org.locationtech.jts.geom.PrecisionModel(),4326); var point=factory.createPoint(new org.locationtech.jts.geom.Coordinate(106.7,10.8)); point.setSRID(4326);
        UUID needId=needs.saveAndFlush(NeedRequestEntity.create(UUID.randomUUID(),buyer,NeedType.PRODUCT,category,"Rice","Rice",null,1L,null,point,JsonNodeFactory.instance.objectNode(),5_000,now.plusSeconds(3600),now)).getId();

        assertThat(audience.findCandidateLocations(needId,100)).isEmpty();
    }
}
