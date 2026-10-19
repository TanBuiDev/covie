package vn.covie.ads;
import java.time.*; import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.covie.ads.api.*;
import vn.covie.ads.api.AdCampaignRequest.*;
import vn.covie.ads.api.AdCampaignResponse.Status;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.merchants.security.*;
import vn.covie.media.application.*;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.audit.AuditLogWriter;
import org.springframework.beans.factory.annotation.Autowired;
import vn.covie.billing.application.BillingEntitlementService;
import vn.covie.settings.application.SystemSettingContext;
import vn.covie.settings.application.SystemSettingResolver;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.concurrency.VersionPrecondition;
import vn.covie.media.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import vn.covie.shared.web.*;
@Service
@Transactional
public class AdService {
 private final JdbcTemplate jdbc; private final AdminAccessPolicy admins; private final MerchantAccessPolicy merchants;
 private final MediaAttachmentPolicy mediaPolicy; private final MediaAssetRepository media; private final MediaUrlResolver urls;
 private final CatalogItemRepository items; private final AuditLogWriter audit; private final UuidV7Generator ids; private final AppClock clock;
 private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
 private BillingEntitlementService billing;
 private SystemSettingResolver settings;
 private java.net.URI publicOrigin=java.net.URI.create("http://localhost:3000");

 @Autowired public void setPublicOrigin(@org.springframework.beans.factory.annotation.Value("${covie.production.public-url:${COVIE_PUBLIC_URL:http://localhost:3000}}") java.net.URI origin) {
  publicOrigin=origin;
 }

 @Autowired public void setBillingDependencies(BillingEntitlementService billing, SystemSettingResolver settings) {
  this.billing=Objects.requireNonNull(billing);
  this.settings=Objects.requireNonNull(settings);
 }
 public AdService(JdbcTemplate j, AdminAccessPolicy a, MerchantAccessPolicy m, MediaAttachmentPolicy p, MediaAssetRepository r, MediaUrlResolver u, CatalogItemRepository i, AuditLogWriter l, UuidV7Generator g, AppClock c) {
  jdbc=j;admins=a;merchants=m;mediaPolicy=p;media=r;urls=u;items=i;audit=l;ids=g;clock=c;
 }
 private UUID actor(UUID merchant) { return merchant==null ? admins.requireAdmin().userId() : merchants.require(merchant,MerchantCapability.MANAGE_CATALOG_STRUCTURE).userId(); }
 private void validate(UUID merchant, UUID owner, AdCampaignRequest r) {
  AdRules.validateTarget(r.targetUrl());
  if(r.startsAt()==null||r.endsAt()==null||!r.endsAt().isAfter(r.startsAt())||r.endsAt().isAfter(r.startsAt().plus(Duration.ofDays(366)))) throw AdRules.invalid("Campaign dates must span at most 366 days.");
  if(r.creativeType()==null||r.placement()==null||r.device()==null||r.priority()<0||r.priority()>1000) throw AdRules.invalid("Invalid advertising configuration.");
  if(r.creativeType()==CreativeType.CSS) {if(r.template()==null||r.mediaId()!=null) throw AdRules.invalid("CSS creative requires a template and no media.");}
  else {
   if(r.mediaId()==null||r.template()!=null) throw AdRules.invalid("Image and video creatives require media.");
   var asset=mediaPolicy.requireReadyOwned(r.mediaId(),owner,Set.of(MediaPurpose.AD_BANNER));
   var allowed=r.creativeType()==CreativeType.IMAGE?Set.of("image/webp","image/jpeg","image/png"):Set.of("video/mp4","video/webm");
   if(!allowed.contains(asset.getMimeType())) throw AdRules.invalid("Media type does not match the creative.");
  }
  if(merchant!=null) {
   if(r.targetUrl().equals("/merchants/"+merchant)) return;
   if(r.targetUrl().startsWith("/items/")) {
    try { UUID item=UUID.fromString(r.targetUrl().substring(7)); if(items.findByIdAndMerchantProfileIdAndDeletedAtIsNull(item,merchant).isPresent())return; } catch(IllegalArgumentException ignored){}
   }
   throw AdRules.invalid("Seller advertising must target its own merchant or catalog item.");
  }
 }
 private String payload(AdCampaignRequest r) {try{return json.writeValueAsString(r);}catch(Exception e){throw new IllegalStateException(e);}}
 private record Row(UUID id,UUID merchant,UUID owner,AdCampaignRequest request,Status status,String reason,long version){}
 private Row map(ResultSet rs,int n)throws SQLException {
  try{return new Row(rs.getObject("id",UUID.class),rs.getObject("merchant_id",UUID.class),rs.getObject("owner_user_id",UUID.class),json.readValue(rs.getString("payload"),AdCampaignRequest.class),Status.valueOf(rs.getString("status")),rs.getString("review_reason"),rs.getLong("version"));}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new SQLException("Invalid stored ad payload",e);}
 }
 private Row find(UUID merchant,UUID id,boolean lock) {
  var rows=jdbc.query("select * from ad_campaigns where id=? and deleted_at is null"+(merchant==null?"":" and merchant_id=?")+(lock?" for update":""),this::map,merchant==null?new Object[]{id}:new Object[]{id,merchant});
  if(rows.isEmpty())throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"Resource not found.");return rows.getFirst();
 }
 private String mediaUrl(AdCampaignRequest r) {
  if(r.mediaId()==null)return null;
  return media.findById(r.mediaId()).filter(a->a.getMediaStatus()==MediaStatus.READY).map(a->urls.publicUrl(a).toString()).orElse(null);
 }
 private AdCampaignResponse response(Row row,boolean admin) {
  var r=row.request();var actions=new ArrayList<>(List.of("UPDATE","DELETE","VIEW_STATS"));
  if(admin&&row.status()==Status.PENDING)actions.addAll(List.of("APPROVE","REJECT"));
  if(!admin&&(row.status()==Status.DRAFT||row.status()==Status.REJECTED))actions.add("SUBMIT");
  return new AdCampaignResponse(row.id(),row.merchant(),r.title(),r.body(),r.ctaLabel(),r.targetUrl(),r.creativeType(),r.template(),r.mediaId(),mediaUrl(r),r.placement(),r.device(),r.startsAt(),r.endsAt(),r.enabled(),r.priority(),row.status(),row.reason(),row.version(),actions);
 }
 public List<AdCampaignResponse> list(UUID merchant) {
  return list(merchant,0);
 }
 public List<AdCampaignResponse> list(UUID merchant,int offset) {
  actor(merchant);if(offset<0||offset>1000000)throw AdRules.invalid("Campaign offset must be between 0 and 1000000.");
  return jdbc.query("select * from ad_campaigns where deleted_at is null"+(merchant==null?"":" and merchant_id=?")+" order by created_at desc,id desc limit 100 offset ?",this::map,merchant==null?new Object[]{offset}:new Object[]{merchant,offset}).stream().map(r->response(r,merchant==null)).toList();
 }
 public AdCampaignResponse create(UUID merchant,AdCampaignRequest r){
  UUID owner=actor(merchant);validate(merchant,owner,r);UUID id=ids.generate();var now=clock.now();var status=merchant==null?Status.APPROVED:Status.DRAFT;
  jdbc.update("insert into ad_campaigns(id,merchant_id,owner_user_id,payload,placement,device,status,enabled,priority,starts_at,ends_at,created_at,updated_at) values(?,?,?,?::jsonb,?,?,?,?,?,?,?,?,?)",id,merchant,owner,payload(r),r.placement().name(),r.device().name(),status.name(),r.enabled(),r.priority(),java.sql.Timestamp.from(r.startsAt()),java.sql.Timestamp.from(r.endsAt()),java.sql.Timestamp.from(now),java.sql.Timestamp.from(now));
  log(owner,"AD_CREATE",id);return response(new Row(id,merchant,owner,r,status,null,0),merchant==null);
 }
 public AdCampaignResponse update(UUID merchant,UUID id,String match,AdCampaignRequest r) {
  UUID owner=actor(merchant);var row=find(merchant,id,true);VersionPrecondition.require(VersionPrecondition.parseRequired(match),row.version());
  // Retaining existing media preserves its original uploader; replacement media must belong to this editor.
  validate(row.merchant(),Objects.equals(row.request().mediaId(),r.mediaId())?mediaOwner(row.request(),row.owner()):owner,r);
  var status=merchant==null?row.status():Status.DRAFT;
  jdbc.update("update ad_campaigns set payload=?::jsonb,placement=?,device=?,enabled=?,priority=?,starts_at=?,ends_at=?,status=?,review_reason=null,version=version+1,updated_at=? where id=?",payload(r),r.placement().name(),r.device().name(),r.enabled(),r.priority(),java.sql.Timestamp.from(r.startsAt()),java.sql.Timestamp.from(r.endsAt()),status.name(),java.sql.Timestamp.from(clock.now()),id);
  log(owner,"AD_UPDATE",id);return response(new Row(id,row.merchant(),row.owner(),r,status,null,row.version()+1),merchant==null);
 }
 public void delete(UUID merchant,UUID id,String match){UUID owner=actor(merchant);var row=find(merchant,id,true);VersionPrecondition.require(VersionPrecondition.parseRequired(match),row.version());jdbc.update("update ad_campaigns set deleted_at=?,version=version+1 where id=?",java.sql.Timestamp.from(clock.now()),id);log(owner,"AD_DELETE",id);}
 public AdCampaignResponse submit(UUID merchant,UUID id,String match) {
  UUID owner=actor(merchant);var row=find(merchant,id,true);VersionPrecondition.require(VersionPrecondition.parseRequired(match),row.version());
  if(row.status()!=Status.DRAFT&&row.status()!=Status.REJECTED)throw AdRules.invalid("Only draft or rejected campaigns can be submitted.");
  if(billing != null) billing.requireCanSell(merchant);
  validate(merchant,mediaOwner(row.request(),row.owner()),row.request());return changeStatus(row,Status.PENDING,null,owner,"AD_SUBMIT",false);
 }
 public AdCampaignResponse review(UUID id,String match,boolean approved,String reason) {
  UUID owner=actor(null);var row=find(null,id,true);VersionPrecondition.require(VersionPrecondition.parseRequired(match),row.version());
  if(row.status()!=Status.PENDING)throw AdRules.invalid("Only pending campaigns can be reviewed.");
  if(!approved&&(reason==null||reason.isBlank()))throw AdRules.invalid("Rejection reason is required.");
  if(reason!=null&&reason.length()>500)throw AdRules.invalid("Reason is too long.");
  validate(row.merchant(),mediaOwner(row.request(),row.owner()),row.request());return changeStatus(row,approved?Status.APPROVED:Status.REJECTED,reason,owner,approved?"AD_APPROVE":"AD_REJECT",true);
 }
 private AdCampaignResponse changeStatus(Row row,Status status,String reason,UUID owner,String action,boolean admin){jdbc.update("update ad_campaigns set status=?,review_reason=?,version=version+1,updated_at=? where id=?",status.name(),reason,java.sql.Timestamp.from(clock.now()),row.id());log(owner,action,row.id());return response(new Row(row.id(),row.merchant(),row.owner(),row.request(),status,reason,row.version()+1),admin);}
 private void log(UUID owner,String action,UUID id){audit.securityEvent(owner,action,"AD_CAMPAIGN",id,null,Map.of());}
 public AdStatsResponse stats(UUID merchant,UUID id,LocalDate from,LocalDate to){
  actor(merchant);if(from==null||to==null||to.isBefore(from)||java.time.temporal.ChronoUnit.DAYS.between(from,to)>92)throw AdRules.invalid("Statistics range must contain at most 93 UTC days.");find(merchant,id,false);
  var days=jdbc.query("select (occurred_at at time zone 'UTC')::date as day,count(*) filter(where event_type='IMPRESSION') as impressions,count(*) filter(where event_type='CLICK') as clicks from ad_events where campaign_id=? and occurred_at>=? and occurred_at<? group by day order by day",(rs,n)->new AdStatsResponse.Day(rs.getDate("day").toLocalDate(),rs.getLong("impressions"),rs.getLong("clicks"),ctr(rs.getLong("impressions"),rs.getLong("clicks"))),id,java.sql.Timestamp.from(from.atStartOfDay().toInstant(ZoneOffset.UTC)),java.sql.Timestamp.from(to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
  long impressions=days.stream().mapToLong(AdStatsResponse.Day::impressions).sum(),clicks=days.stream().mapToLong(AdStatsResponse.Day::clicks).sum();return new AdStatsResponse(impressions,clicks,ctr(impressions,clicks),days);
 }
 private double ctr(long impressions,long clicks){return impressions==0?0:Math.round(clicks*10000.0/impressions)/100.0;}
 private UUID mediaOwner(AdCampaignRequest r,UUID fallback){return r.mediaId()==null?fallback:media.findById(r.mediaId()).map(MediaAssetEntity::getOwnerUserId).orElse(fallback);}
 private record Target(boolean visible,UUID merchant) {}
 private Target target(Row row) {
  var destination=java.net.URI.create(row.request().targetUrl());
  if(destination.isAbsolute()&&!(Objects.equals(destination.getScheme().toLowerCase(Locale.ROOT),publicOrigin.getScheme().toLowerCase(Locale.ROOT))
    &&Objects.equals(destination.getHost().toLowerCase(Locale.ROOT),publicOrigin.getHost().toLowerCase(Locale.ROOT))
    &&originPort(destination)==originPort(publicOrigin)))return new Target(true,null);
  String path=java.net.URI.create(destination.getRawPath().replaceAll("(?i)%2e",".")).normalize().getPath();
  while(path.startsWith("/../"))path=path.substring(3);
  if(path.equals("/.."))path="/";
  UUID merchant=row.merchant();
  if(path.startsWith("/items/")) {
   try {var item=items.findById(UUID.fromString(path.substring(7))).orElse(null);if(item==null||item.getDeletedAt()!=null||item.getListingStatus()!=vn.covie.catalog.model.ListingStatus.ACTIVE||!(item.getModerationStatus()==vn.covie.catalog.model.CatalogModerationStatus.APPROVED||item.getModerationStatus()==vn.covie.catalog.model.CatalogModerationStatus.NOT_REQUIRED))return new Target(false,null);merchant=item.getMerchantProfileId();}catch(IllegalArgumentException e){return new Target(false,null);}
  } else if(path.startsWith("/merchants/")) {try{merchant=UUID.fromString(path.substring(11));}catch(IllegalArgumentException e){return new Target(false,null);}}
  return new Target(merchant==null||Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from merchant_profiles where id=? and deleted_at is null and merchant_status='ACTIVE' and moderation_status in ('NOT_REQUIRED','APPROVED'))",Boolean.class,merchant)),merchant);
 }
 private boolean billingEnforced() {
  return settings != null && settings.bool("billing.enforcement_enabled",SystemSettingContext.globalOnly());
 }
 private int originPort(java.net.URI origin) {
  return origin.getPort()>=0?origin.getPort():"https".equalsIgnoreCase(origin.getScheme())?443:80;
 }
 private Set<UUID> linkedMerchants(Row row,Target target) {
  var linked=new HashSet<UUID>();
  if(row.merchant()!=null)linked.add(row.merchant());
  if(target.merchant()!=null)linked.add(target.merchant());
  return linked;
 }
 private Map<UUID,Instant> entitlementExpiries(Set<UUID> merchants,Instant now) {
  if(merchants.isEmpty())return Map.of();
  var expiries=new HashMap<UUID,Instant>();
  new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc).query("""
    select merchant_id,max(valid_until) as valid_until from billing_entitlement_grants
    where merchant_id in (:merchants) and revoked_at is null
      and valid_from<=:now and valid_until>:now group by merchant_id
    """,Map.of("merchants",merchants,"now",java.sql.Timestamp.from(now)),
    (org.springframework.jdbc.core.RowCallbackHandler) result->expiries.put(result.getObject("merchant_id",UUID.class),result.getTimestamp("valid_until").toInstant()));
  return expiries;
 }
 public List<PublicAdResponse> active(Placement placement,Device device) {
  if(device==Device.ALL)throw AdRules.invalid("Select a concrete device.");var now=clock.now();
  jdbc.update("delete from ad_deliveries where id in (select id from ad_deliveries where expires_at<? order by expires_at limit 500)",java.sql.Timestamp.from(now));
  boolean enforced=billingEnforced();
  var candidates=jdbc.query("""
    select * from ad_campaigns c where c.deleted_at is null and c.enabled
      and c.status='APPROVED' and c.placement=? and (c.device=? or c.device='ALL')
      and c.starts_at<=? and c.ends_at>?
      and (c.merchant_id is null or exists(
          select 1 from merchant_profiles m where m.id=c.merchant_id and m.deleted_at is null
           and m.merchant_status='ACTIVE' and m.moderation_status in ('NOT_REQUIRED','APPROVED')
      ))
      and (? = false or c.merchant_id is null or exists(
          select 1 from billing_entitlement_grants g where g.merchant_id=c.merchant_id
            and g.revoked_at is null and g.valid_from<=? and g.valid_until>?
      ))
      order by c.priority desc,c.created_at desc,c.id desc limit 100
    """,this::map,placement.name(),device.name(),java.sql.Timestamp.from(now),
      java.sql.Timestamp.from(now),enforced,java.sql.Timestamp.from(now),java.sql.Timestamp.from(now));
  if(!enforced)return candidates.stream().filter(row->target(row).visible())
    .filter(row->row.request().mediaId()==null||mediaUrl(row.request())!=null).limit(10)
    .map(row->deliver(row,now,null)).toList();
  var deliveries=new ArrayList<PublicAdResponse>();
  for(int start=0;start<candidates.size()&&deliveries.size()<10;start+=10){
   var batch=candidates.subList(start,Math.min(start+10,candidates.size()));
   var targets=new HashMap<UUID,Target>();var merchants=new HashSet<UUID>();
   for(var row:batch){var resolved=target(row);targets.put(row.id(),resolved);merchants.addAll(linkedMerchants(row,resolved));}
   var expiries=entitlementExpiries(merchants,now);
   for(var row:batch){
    var resolved=targets.get(row.id());var linked=linkedMerchants(row,resolved);
    if(!resolved.visible()||!expiries.keySet().containsAll(linked)||row.request().mediaId()!=null&&mediaUrl(row.request())==null)continue;
    Instant expiry=linked.stream().map(expiries::get).min(Comparator.naturalOrder()).orElse(null);
    deliveries.add(deliver(row,now,expiry));if(deliveries.size()==10)break;
   }
  }
  return List.copyOf(deliveries);
 }
 private PublicAdResponse deliver(Row row,Instant now,Instant entitlementExpiry) {
  var request=row.request();UUID token=ids.generate();Instant expires=now.plusSeconds(1800);
  if(request.endsAt().isBefore(expires))expires=request.endsAt();
  if(entitlementExpiry!=null&&entitlementExpiry.isBefore(expires))expires=entitlementExpiry;
  jdbc.update("insert into ad_deliveries(id,campaign_id,campaign_version,issued_at,expires_at) values(?,?,?,?,?)",token,row.id(),row.version(),java.sql.Timestamp.from(now),java.sql.Timestamp.from(expires));
  return new PublicAdResponse(row.id(),request.title(),request.body(),request.ctaLabel(),request.targetUrl(),request.creativeType(),request.template(),mediaUrl(request),request.placement(),request.endsAt(),token.toString(),expires);
 }
 public void event(UUID id,AdEventRequest event) {
  if(!AdRules.acceptsEvent(event.type().name(),event.visibleMillis()))throw AdRules.invalid("Impressions require at least 1000ms of continuous visibility.");
  UUID token;try{token=UUID.fromString(event.deliveryToken());}catch(IllegalArgumentException e){throw AdRules.invalid("Invalid advertising delivery token.");}
  var deliveries=jdbc.query("select campaign_version,issued_at from ad_deliveries where id=? and campaign_id=? and expires_at>?",(rs,n)->new long[]{rs.getLong(1),rs.getTimestamp(2).toInstant().toEpochMilli()},token,id,java.sql.Timestamp.from(clock.now()));
  if(deliveries.isEmpty())throw AdRules.invalid("Invalid or expired advertising delivery token.");long version=deliveries.getFirst()[0],issued=deliveries.getFirst()[1];
  var now=clock.now();long age=now.toEpochMilli()-issued;if(age<0||age>1800000||event.type()==AdEventRequest.Type.IMPRESSION&&age<event.visibleMillis())throw AdRules.invalid("Advertising delivery token is expired or too recent.");
  var row=find(null,id,true);var r=row.request();var resolved=target(row);var linked=linkedMerchants(row,resolved);
  if(row.version()!=version||row.status()!=Status.APPROVED||!r.enabled()||now.isBefore(r.startsAt())||!now.isBefore(r.endsAt())||!resolved.visible()||billingEnforced()&&!entitlementExpiries(linked,now).keySet().containsAll(linked))throw AdRules.invalid("Campaign is no longer active.");
  jdbc.update("insert into ad_events(event_id,campaign_id,delivery_key,event_type,occurred_at) values(?,?,?,?,?) on conflict do nothing",event.eventId(),id,token.toString(),event.type().name(),java.sql.Timestamp.from(now));
 }
}
