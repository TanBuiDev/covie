package vn.covie.performance;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.support.PostgresIntegrationTest;

@Tag("performance")
@EnabledIfSystemProperty(named="covie.test.pilot-fixture",matches="true")
@SpringBootTest(properties={"covie.outbox.max-events-per-poll=0","covie.retention.enabled=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=vn.covie.performance.PilotFixtureIntegrationTest$SqlCapture"})
@Transactional
class PilotFixtureIntegrationTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired vn.covie.notifications.persistence.NotificationRepository notifications;
    @Autowired vn.covie.shared.outbox.OutboxEventRepository outbox;
    @Autowired vn.covie.orders.persistence.OrderRepository orders;
    @Autowired vn.covie.cart.persistence.CartRepository carts;
    public static class SqlCapture implements org.hibernate.resource.jdbc.spi.StatementInspector {
        static final ThreadLocal<String> SQL=new ThreadLocal<>();
        public SqlCapture() {}
        @Override public String inspect(String sql) {
            if(sql.startsWith("select "))SQL.set(sql);
            return sql;
        }
    }
    @Test void bulkFixtureMeetsEveryPilotThresholdAndPreservesOfferOwnership() throws Exception {
        try(var stream=getClass().getResourceAsStream("/performance/pilot-fixture.sql")) {
            assertThat(stream).isNotNull();
            jdbc.execute(new String(stream.readAllBytes(),StandardCharsets.UTF_8));
        }
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection->{
            PerformanceFixtureBuilder.assertPilotScale(connection);
            return null;
        });
        assertThat(jdbc.queryForObject("select count(*) from location_variants lv join variants v on v.id=lv.variant_id join catalog_items i on i.id=v.catalog_item_id join merchant_locations l on l.id=lv.merchant_location_id where i.merchant_profile_id<>l.merchant_profile_id",Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(distinct location_status) from merchant_locations",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(distinct ST_AsText(geo_point::geometry)) from merchant_locations",Integer.class)).isEqualTo(2000);
        assertThat(jdbc.queryForObject("select count(*) from location_areas where geo_boundary is not null and ST_IsValid(geo_boundary::geometry)",Long.class)).isEqualTo(100);
        assertThat(jdbc.queryForObject("select count(*) from merchant_locations l join location_areas a on a.id=l.location_area_id where not ST_DWithin(a.geo_boundary,l.geo_point,1)",Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from need_requests n join location_areas a on a.id=n.location_area_id where not ST_DWithin(a.geo_boundary,n.geo_point,1)",Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from order_events e join orders o on o.id=e.order_id where o.status='PENDING_CONFIRMATION' and e.event_type<>'ORDER_CREATED'",Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where status in ('PENDING','FAILED')",Long.class)).isEqualTo(100);
        verifyHistoricalHotPlans();
        verifyOrderAndCartPlans();
        verifyMultiLocationOrderPlans();
        verifyAutoCompletionPlans();
        verifyNamedDiscoveryPlans();
    }
    private void verifyMultiLocationOrderPlans() throws Exception {
        var merchant=jdbc.queryForObject("select md5('pilot-merchant-1')::uuid",java.util.UUID.class);
        var primary=jdbc.queryForObject("select md5('pilot-location-1')::uuid",java.util.UUID.class);
        var secondary=jdbc.queryForObject("select md5('pilot-location-1001')::uuid",java.util.UUID.class);
        entityManager.flush();
        jdbc.update("update orders set merchant_location_id=? where merchant_profile_id=? and order_code in "
                +"(select 'PILOT-'||g from generate_series(1001,50001,2000) g)",secondary,merchant);
        jdbc.update("update product_fulfillments f set pickup_location_id=o.merchant_location_id from orders o "
                +"where f.order_id=o.id and o.merchant_profile_id=?",merchant);
        entityManager.clear();
        var expected=jdbc.queryForList("select id from orders where merchant_profile_id=? order by created_at desc,id desc",
                java.util.UUID.class,merchant);
        assertThat(expected).hasSize(51);
        var observed=new java.util.ArrayList<java.util.UUID>();
        var locations=new java.util.HashSet<java.util.UUID>();
        var page=org.springframework.data.domain.PageRequest.of(0,21);
        java.time.Instant afterTime=null;
        java.util.UUID afterId=null;
        SqlCapture.SQL.remove();
        try {
            for(int number=0;number<4;number++) {
                var rows=orders.findMerchantPage(merchant,null,afterTime,afterId,page);
                if(number==0)verifyPlan("seller-orders-all-locations-first",SqlCapture.SQL.get(),
                        "ix_orders_merchant_created_v010",merchant,21);
                if(number==1)verifyPlan("seller-orders-all-locations-cursor",SqlCapture.SQL.get(),
                        "ix_orders_merchant_created_v010",merchant,java.sql.Timestamp.from(afterTime),
                        java.sql.Timestamp.from(afterTime),afterId,21);
                if(number==3){assertThat(rows).isEmpty();break;}
                assertThat(rows).hasSize(number==2?9:21);
                rows.forEach(row->{observed.add(row.getId());locations.add(row.getMerchantLocationId());});
                var last=rows.getLast();afterTime=last.getCreatedAt();afterId=last.getId();
            }
            assertThat(observed).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
            assertThat(locations).containsExactlyInAnyOrder(primary,secondary);
            for(var location:java.util.List.of(primary,secondary)) {
                var localExpected=jdbc.queryForList("select id from orders where merchant_profile_id=? and merchant_location_id=? order by created_at desc,id desc",
                        java.util.UUID.class,merchant,location);
                var localObserved=new java.util.ArrayList<java.util.UUID>();
                afterTime=null;afterId=null;
                for(int number=0;number<3;number++) {
                    var rows=orders.findMerchantPage(merchant,location,afterTime,afterId,page);
                    assertThat(rows).allSatisfy(row->assertThat(row.getMerchantLocationId()).isEqualTo(location));
                    if(rows.isEmpty())break;
                    rows.forEach(row->localObserved.add(row.getId()));
                    var last=rows.getLast();afterTime=last.getCreatedAt();afterId=last.getId();
                }
                assertThat(localObserved).containsExactlyElementsOf(localExpected).doesNotHaveDuplicates();
            }
        } finally {SqlCapture.SQL.remove();}
    }
    private void verifyAutoCompletionPlans() throws Exception {
        var now=java.time.Instant.now();
        var page=org.springframework.data.domain.PageRequest.of(0,100);
        SqlCapture.SQL.remove();
        try {
            var products=orders.findDueProductAutoCompletionIds(vn.covie.orders.model.OrderStatus.AWAITING_BUYER_CONFIRMATION,now,page);
            assertThat(products).hasSize(100).containsExactlyInAnyOrderElementsOf(jdbc.queryForList(
                    "select md5('pilot-order-'||g)::uuid from generate_series(101,200) g",java.util.UUID.class));
            verifyPlanIndexes("orders-product-auto-completion",SqlCapture.SQL.get(),
                    new String[]{"ix_product_fulfillment_auto_complete","ix_orders_admin_state_created"},
                    "AWAITING_BUYER_CONFIRMATION",java.sql.Timestamp.from(now),100);
            var services=orders.findDueServiceAutoCompletionIds(vn.covie.orders.model.OrderStatus.AWAITING_BUYER_CONFIRMATION,now,page);
            assertThat(services).hasSize(100).containsExactlyInAnyOrderElementsOf(jdbc.queryForList(
                    "select md5('pilot-order-'||g)::uuid from generate_series(25101,25200) g",java.util.UUID.class));
            verifyPlanIndexes("orders-service-auto-completion",SqlCapture.SQL.get(),
                    new String[]{"ix_service_fulfillment_auto_complete","ix_orders_admin_state_created"},
                    "AWAITING_BUYER_CONFIRMATION",java.sql.Timestamp.from(now),100);
        } finally {SqlCapture.SQL.remove();}
    }
    private void verifyNamedDiscoveryPlans() throws Exception {
        var now=java.time.Instant.now();
        var merchant=jdbc.queryForObject("select md5('pilot-merchant-1')::uuid",java.util.UUID.class);
        var location=jdbc.queryForObject("select md5('pilot-location-1')::uuid",java.util.UUID.class);
        var category=jdbc.queryForObject("select md5('pilot-category-1')::uuid",java.util.UUID.class);
        var capture=new NamedSqlCapture(jdbc.getDataSource());
        var needs=new vn.covie.needs.persistence.JdbcNeedDiscoveryRepository(capture);
        var needFirst=needs.findCandidates(merchant,java.util.Set.of(location),location,now,null,31);
        assertThat(needFirst).hasSize(31);
        verifyNamedPlan("seller-needs-first",capture,"ix_location_variant_location_available","ux_location_variant_active");
        var needLast=needFirst.getLast();
        var needAfter=new vn.covie.needs.application.NeedDiscoveryCursor(needLast.textRank(),needLast.distanceMeters(),needLast.needId(),needLast.merchantLocationId());
        var needNext=needs.findCandidates(merchant,java.util.Set.of(location),location,now,needAfter,31);
        assertThat(needNext).hasSize(31);
        assertThat(needNext).extracting(vn.covie.needs.persistence.NeedMatchRow::needId)
                .doesNotContainAnyElementsOf(needFirst.stream().map(vn.covie.needs.persistence.NeedMatchRow::needId).toList());
        verifyNamedPlan("seller-needs-cursor",capture,"ix_location_variant_location_available","ux_location_variant_active");
        var catalog=new vn.covie.catalog.persistence.JdbcCatalogDiscoveryRepository(capture);
        var query=new vn.covie.catalog.api.dto.discovery.CatalogDiscoveryQuery("rice",vn.covie.catalog.model.CatalogItemType.PRODUCT,
                null,category,10.7,106.602,null,1000,true,false,null,null,
                vn.covie.catalog.api.dto.discovery.CatalogSort.RELEVANCE,20,null);
        var context=new vn.covie.location.application.LocationContext(vn.covie.location.application.LocationResolutionSource.COORDINATES,
                new vn.covie.location.application.GeoPointFactory().point(10.7,106.602),null,null,java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        var catalogFirst=catalog.find(query,context,null,now);
        assertThat(catalogFirst).hasSize(21);
        verifyNamedPlan("catalog-query-category-location",capture,"ix_merchant_locations_geo");
        var last=catalogFirst.getLast();
        var after=new vn.covie.catalog.api.dto.discovery.CatalogCursor(query.sort(),last.rank(),last.distanceMeters(),last.minPrice(),last.createdAt(),last.itemId());
        var catalogNext=catalog.find(query,context,after,now);
        assertThat(catalogNext).hasSize(21);
        assertThat(catalogNext).extracting(vn.covie.catalog.persistence.CatalogDiscoveryRepository.Row::itemId)
                .doesNotContainAnyElementsOf(catalogFirst.stream().map(vn.covie.catalog.persistence.CatalogDiscoveryRepository.Row::itemId).toList());
        verifyNamedPlan("catalog-relevance-cursor",capture,"ix_merchant_locations_geo");
        verifyCatalogSorts(catalog,capture,category,context,now);
        verifyProductCoverage(catalog,capture,category,context,now);
        verifyServiceCoverage(catalog,capture,category,context,now);
    }
    private void verifyCatalogSorts(vn.covie.catalog.persistence.JdbcCatalogDiscoveryRepository catalog,NamedSqlCapture capture,
            java.util.UUID category,vn.covie.location.application.LocationContext context,java.time.Instant now) throws Exception {
        for(var sort:vn.covie.catalog.api.dto.discovery.CatalogSort.values()) {
            var baselineQuery=new vn.covie.catalog.api.dto.discovery.CatalogDiscoveryQuery("rice",vn.covie.catalog.model.CatalogItemType.PRODUCT,
                    null,null,10.7,106.602,null,1000,true,false,10200L,10400L,sort,10000,null);
            var expected=catalog.find(baselineQuery,context,null,now);
            assertThat(expected).isNotEmpty().allSatisfy(row->assertThat(row.minPrice()).isBetween(10200L,10400L));
            java.util.Comparator<vn.covie.catalog.persistence.CatalogDiscoveryRepository.Row> idOrder=
                    java.util.Comparator.comparing(row->row.itemId().toString());
            java.util.Comparator<vn.covie.catalog.persistence.CatalogDiscoveryRepository.Row> comparator=switch(sort) {
                case DISTANCE -> java.util.Comparator.comparing(vn.covie.catalog.persistence.CatalogDiscoveryRepository.Row::distanceMeters).thenComparing(idOrder);
                case PRICE_ASC -> java.util.Comparator.comparing(vn.covie.catalog.persistence.CatalogDiscoveryRepository.Row::minPrice).thenComparing(idOrder);
                case PRICE_DESC -> java.util.Comparator.comparing(vn.covie.catalog.persistence.CatalogDiscoveryRepository.Row::minPrice).thenComparing(idOrder).reversed();
                case NEWEST -> java.util.Comparator.comparing(vn.covie.catalog.persistence.CatalogDiscoveryRepository.Row::createdAt).thenComparing(idOrder).reversed();
                case RELEVANCE -> java.util.Comparator.comparing(vn.covie.catalog.persistence.CatalogDiscoveryRepository.Row::rank).reversed()
                        .thenComparing(vn.covie.catalog.persistence.CatalogDiscoveryRepository.Row::distanceMeters).thenComparing(idOrder);
            };
            assertThat(expected).isSortedAccordingTo(comparator);
            var pagedQuery=new vn.covie.catalog.api.dto.discovery.CatalogDiscoveryQuery("rice",vn.covie.catalog.model.CatalogItemType.PRODUCT,
                    null,null,10.7,106.602,null,1000,true,false,10200L,10400L,sort,20,null);
            var observed=new java.util.ArrayList<java.util.UUID>();
            vn.covie.catalog.api.dto.discovery.CatalogCursor cursor=null;
            for(int page=0;page<100;page++) {
                var rows=catalog.find(pagedQuery,context,cursor,now);
                if(page<2)verifyNamedPlan("catalog-"+sort.name().toLowerCase(java.util.Locale.ROOT)+"-filtered-"+page,capture,"ix_merchant_locations_geo");
                if(rows.isEmpty())break;
                var visible=rows.stream().limit(20).toList();
                visible.forEach(row->observed.add(row.itemId()));
                var last=visible.getLast();
                cursor=switch(sort) {
                    case RELEVANCE -> new vn.covie.catalog.api.dto.discovery.CatalogCursor(sort,last.rank(),last.distanceMeters(),null,null,last.itemId());
                    case DISTANCE -> new vn.covie.catalog.api.dto.discovery.CatalogCursor(sort,null,last.distanceMeters(),null,null,last.itemId());
                    case PRICE_ASC,PRICE_DESC -> new vn.covie.catalog.api.dto.discovery.CatalogCursor(sort,null,null,last.minPrice(),null,last.itemId());
                    case NEWEST -> new vn.covie.catalog.api.dto.discovery.CatalogCursor(sort,null,null,null,last.createdAt(),last.itemId());
                };
                var codec=new vn.covie.catalog.application.CatalogCursorCodec();
                cursor=codec.decode(codec.encode(cursor),sort);
            }
            assertThat(observed).containsExactlyElementsOf(expected.stream().map(vn.covie.catalog.persistence.CatalogDiscoveryRepository.Row::itemId).toList()).doesNotHaveDuplicates();
        }
    }
    private void verifyServiceCoverage(vn.covie.catalog.persistence.JdbcCatalogDiscoveryRepository catalog,NamedSqlCapture capture,
            java.util.UUID category,vn.covie.location.application.LocationContext context,java.time.Instant now) throws Exception {
        jdbc.update("update categories set catalog_type='SERVICE'");
        jdbc.update("update catalog_items set item_type='SERVICE'");
        jdbc.update("update merchant_locations set service_enabled=true");
        jdbc.update("update delivery_coverages set coverage_type='SERVICE_AREA'");
        var query=new vn.covie.catalog.api.dto.discovery.CatalogDiscoveryQuery("rice",vn.covie.catalog.model.CatalogItemType.SERVICE,
                null,category,10.7,106.602,null,5000,false,false,null,null,
                vn.covie.catalog.api.dto.discovery.CatalogSort.RELEVANCE,100,null,true,null);
        var radiusRows=catalog.find(query,context,null,now);
        assertThat(radiusRows).isNotEmpty().allSatisfy(row->assertThat(row.distanceMeters()).isLessThanOrEqualTo(300));
        verifyNamedPlan("service-coverage-radius",capture,"ix_merchant_locations_geo");
        var area=jdbc.queryForObject("select md5('pilot-area-1')::uuid",java.util.UUID.class);
        var areaContext=new vn.covie.location.application.LocationContext(context.source(),context.point(),area,null,context.timezone());
        var areaRows=catalog.find(query,areaContext,null,now);
        assertThat(areaRows).anySatisfy(row->assertThat(row.distanceMeters()).isGreaterThan(300));
        verifyNamedPlan("service-coverage-area",capture,"ix_merchant_locations_geo");
        jdbc.update("update delivery_coverages set radius_enabled=true,radius_meters=300,area_enabled=true");
        jdbc.update("insert into delivery_coverage_areas(delivery_coverage_id,location_area_id) "
                +"select id,? from delivery_coverages on conflict do nothing",area);
        var hybridWithoutArea=catalog.find(query,context,null,now);
        assertThat(hybridWithoutArea).isNotEmpty().allSatisfy(row->assertThat(row.distanceMeters()).isLessThanOrEqualTo(300));
        var hybridWithArea=catalog.find(query,areaContext,null,now);
        assertThat(hybridWithArea).anySatisfy(row->assertThat(row.distanceMeters()).isGreaterThan(300));
        verifyNamedPlan("service-coverage-hybrid",capture,"ix_merchant_locations_geo");
        jdbc.update("update delivery_coverages set coverage_status='INACTIVE'");
        assertThat(catalog.find(query,areaContext,null,now)).isEmpty();
        jdbc.update("update delivery_coverages set coverage_status='ACTIVE'");
        jdbc.update("update merchant_locations set service_enabled=false");
        assertThat(catalog.find(query,areaContext,null,now)).isEmpty();
    }
    private void verifyProductCoverage(vn.covie.catalog.persistence.JdbcCatalogDiscoveryRepository catalog,NamedSqlCapture capture,
            java.util.UUID category,vn.covie.location.application.LocationContext context,java.time.Instant now) throws Exception {
        jdbc.update("update merchant_locations set pickup_enabled=false");
        var query=new vn.covie.catalog.api.dto.discovery.CatalogDiscoveryQuery("rice",vn.covie.catalog.model.CatalogItemType.PRODUCT,
                null,category,10.7,106.602,null,5000,false,false,null,null,
                vn.covie.catalog.api.dto.discovery.CatalogSort.RELEVANCE,100,null,true,null);
        var radiusRows=catalog.find(query,context,null,now);
        assertThat(radiusRows).isNotEmpty().allSatisfy(row->assertThat(row.distanceMeters()).isLessThanOrEqualTo(300));
        verifyNamedPlan("product-delivery-radius",capture,"ix_merchant_locations_geo");
        var area=jdbc.queryForObject("select md5('pilot-area-1')::uuid",java.util.UUID.class);
        var areaContext=new vn.covie.location.application.LocationContext(context.source(),context.point(),area,null,context.timezone());
        var areaRows=catalog.find(query,areaContext,null,now);
        assertThat(areaRows).anySatisfy(row->assertThat(row.distanceMeters()).isGreaterThan(300));
        verifyNamedPlan("product-delivery-area",capture,"ix_merchant_locations_geo");
        assertThat(jdbc.queryForObject("select count(*) from delivery_coverage_areas",Long.class)).isEqualTo(1000);
    }
    private void verifyNamedPlan(String name,NamedSqlCapture capture,String... indexes) throws Exception {
        String sql=capture.sql;
        var parameters=capture.parameters;
        assertThat(sql.toLowerCase(java.util.Locale.ROOT)).doesNotContain(" offset ");
        String plan=new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc.getDataSource())
                .queryForObject("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) "+sql,parameters,String.class);
        HotQueryPlanIntegrationTest.assertHealthyPlan(plan);
        assertThat(plan).containsAnyOf(indexes);
        var directory=java.nio.file.Path.of("../../.local/perf-pilot-20261011");
        java.nio.file.Files.createDirectories(directory);
        java.nio.file.Files.writeString(directory.resolve(name+".sql"),sql);
        java.nio.file.Files.writeString(directory.resolve(name+"-bindings.txt"),parameters.toString());
        java.nio.file.Files.writeString(directory.resolve(name+"-plan.json"),plan);
    }
    private static class NamedSqlCapture extends org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate {
        String sql;
        org.springframework.jdbc.core.namedparam.SqlParameterSource parameters;
        NamedSqlCapture(javax.sql.DataSource datasource){super(datasource);}
        @Override public <T> java.util.List<T> query(String sql,org.springframework.jdbc.core.namedparam.SqlParameterSource parameters,
                org.springframework.jdbc.core.RowMapper<T> mapper) {
            this.sql=sql;this.parameters=parameters;
            return super.query(sql,parameters,mapper);
        }
    }
    private void verifyOrderAndCartPlans() throws Exception {
        var now=java.time.Instant.now();
        var merchant=jdbc.queryForObject("select md5('pilot-merchant-1')::uuid",java.util.UUID.class);
        var location=jdbc.queryForObject("select md5('pilot-location-1')::uuid",java.util.UUID.class);
        var user=jdbc.queryForObject("select md5('pilot-user-1')::uuid",java.util.UUID.class);
        var page=org.springframework.data.domain.PageRequest.of(0,21);
        SqlCapture.SQL.remove();
        try {
            var first=orders.findMerchantFirstPageAtLocation(merchant,location,page);
            assertThat(first).hasSize(21);
            verifyPlan("seller-orders-location-first",SqlCapture.SQL.get(),"ix_orders_merchant_created_v010",merchant,location,21);
            var last=first.getLast();
            assertThat(orders.findMerchantPageAfterAtLocation(merchant,location,last.getCreatedAt(),last.getId(),page)).hasSize(21);
            verifyPlan("seller-orders-location-cursor",SqlCapture.SQL.get(),"ix_orders_merchant_created_v010",merchant,location,
                    java.sql.Timestamp.from(last.getCreatedAt()),java.sql.Timestamp.from(last.getCreatedAt()),last.getId(),21);
            assertThat(orders.findTop100ByStatusAndConfirmationExpiresAtLessThanEqualOrderByConfirmationExpiresAtAsc(
                    vn.covie.orders.model.OrderStatus.PENDING_CONFIRMATION,now)).hasSize(100);
            verifyPlan("orders-confirmation-expiry",SqlCapture.SQL.get(),"ix_orders_pending_expiry","PENDING_CONFIRMATION",java.sql.Timestamp.from(now),100);
            assertThat(carts.findByUserIdAndCartStatusOrderByUpdatedAtDesc(user,vn.covie.cart.model.CartStatus.ACTIVE)).hasSize(1);
            verifyPlan("active-carts-user",SqlCapture.SQL.get(),"ux_cart_active_user_location_type",user,"ACTIVE");
        } finally {SqlCapture.SQL.remove();}
    }
    private void verifyHistoricalHotPlans() throws Exception {
        var now=java.time.Instant.now();
        var user=jdbc.queryForObject("select md5('pilot-user-1')::uuid",java.util.UUID.class);
        var cursor=now.minusSeconds(50000);
        var cursorId=new java.util.UUID(0,0);
        var page=org.springframework.data.domain.PageRequest.of(0,31);
        SqlCapture.SQL.remove();
        try {
            assertThat(notifications.findByUserIdOrderByCreatedAtDescIdDesc(user,page)).hasSize(31);
            String firstSql=SqlCapture.SQL.get();
            assertThat(firstSql).isNotBlank();
            assertThat(firstSql.chars().filter(c->c=='?').count()).isEqualTo(2);
            verifyPlan("notifications-first",firstSql,"ix_notifications_user_cursor",user,31);
            SqlCapture.SQL.remove();
            assertThat(notifications.pageAfter(user,cursor,cursorId,page)).hasSize(31);
            String cursorSql=SqlCapture.SQL.get();
            assertThat(cursorSql).isNotBlank();
            assertThat(cursorSql.chars().filter(c->c=='?').count()).isEqualTo(5);
            verifyPlan("notifications-cursor",cursorSql,"ix_notifications_user_cursor",user,
                    java.sql.Timestamp.from(cursor),java.sql.Timestamp.from(cursor),cursorId,31);
        } finally { SqlCapture.SQL.remove(); }
        assertThat(outbox.lockNextDue(now)).isPresent();
        var method=vn.covie.shared.outbox.OutboxEventRepository.class.getMethod("lockNextDue",java.time.Instant.class);
        String outboxSql=method.getAnnotation(org.springframework.data.jpa.repository.Query.class).value().replace(":now","?");
        assertThat(outboxSql).contains("for update skip locked");
        verifyPlan("outbox-due",outboxSql,"ix_outbox_dispatch",java.sql.Timestamp.from(now));
    }
    private void verifyPlan(String name,String sql,String index,Object... parameters) throws Exception {
        verifyPlanIndexes(name,sql,new String[]{index},parameters);
    }
    private void verifyPlanIndexes(String name,String sql,String[] indexes,Object... parameters) throws Exception {
        assertThat(sql.toLowerCase(java.util.Locale.ROOT)).doesNotContain(" offset ");
        String plan=jdbc.queryForObject("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) "+sql,String.class,parameters);
        HotQueryPlanIntegrationTest.assertHealthyPlan(plan);
        assertThat(plan).containsAnyOf(indexes);
        var directory=java.nio.file.Path.of("../../.local/perf-pilot-20261011");
        java.nio.file.Files.createDirectories(directory);
        java.nio.file.Files.writeString(directory.resolve(name+".sql"),sql);
        java.nio.file.Files.writeString(directory.resolve(name+"-bindings.txt"),java.util.Arrays.toString(parameters));
        java.nio.file.Files.writeString(directory.resolve(name+"-plan.json"),plan);
    }
}
