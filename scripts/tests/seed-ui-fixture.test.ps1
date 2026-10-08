[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$fixturePath = Join-Path $repositoryRoot ".local/dev/ui-fixture.json"
$seedScriptPath = Join-Path $repositoryRoot "scripts/seed-ui-fixture.ps1"
$seedScriptSource = Get-Content -LiteralPath $seedScriptPath -Raw

if ($seedScriptSource -notmatch 'dependency:copy-dependencies' -or
    $seedScriptSource -notmatch 'spring-security-crypto,bcprov-jdk18on,commons-logging') {
    throw "UI fixture seed must resolve only the three Argon2 runtime dependencies."
}
if ($seedScriptSource -notmatch '-join \[IO\.Path\]::PathSeparator') {
    throw "UI fixture seed must build an explicit platform-separated JShell classpath."
}
if ($seedScriptSource -match '\$fixtureClasspath\s*=\s*"target/ui-fixture-dependencies/\*"') {
    throw "UI fixture seed must not pass an unexpanded dependency wildcard to JShell."
}
if ($seedScriptSource -notmatch '\[IO\.File\]::WriteAllText\(\$snippetPath' -or
    $seedScriptSource -notmatch 'Remove-Item -LiteralPath \$snippetPath') {
    throw "UI fixture seed must execute and remove a temporary JShell snippet."
}

Push-Location $repositoryRoot
try {
    & pnpm ui:seed-local | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "UI fixture seed failed." }

    $bytes = [System.IO.File]::ReadAllBytes($fixturePath)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        throw "UI fixture JSON must be UTF-8 without BOM."
    }

    & node -e "JSON.parse(require('fs').readFileSync(process.argv[1], 'utf8'))" $fixturePath
    if ($LASTEXITCODE -ne 0) { throw "Node could not parse UI fixture JSON." }

    $fixture = Get-Content -LiteralPath $fixturePath -Raw | ConvertFrom-Json
    foreach ($key in @(
        'buyerId','buyerEmail','buyerPassword',
        'zeroReputationBuyerEmail','zeroReputationBuyerPassword',
        'warningReputationBuyerEmail','warningReputationBuyerPassword',
        'sellerId','sellerEmail','sellerPassword',
        'adminId','adminEmail','adminPassword',
        'merchantId','publicServiceMerchantId','emptyNoticeMerchantId','publicMixedMerchantId','locationId',
        'pendingProductOrderId','deliveryProductOrderId','pickupProductOrderId','serviceOrderId',
        'reviewEligibleOrderId','alreadyReviewedOrderId','reviewDisabledOrderId',
        'candidateNeedId','respondedNeedId','emptyCandidateNeedId'
    )) {
        if (-not $fixture.$key) { throw "Multi-role UI fixture is missing '$key'." }
    }
    if (@($fixture.buyerEmail, $fixture.sellerEmail, $fixture.adminEmail | Sort-Object -Unique).Count -ne 3) {
        throw "Buyer, Seller, and Admin fixture emails must be distinct."
    }
    if ($fixture.merchantId -ne '33333333-3333-4333-8333-333333333333') {
        throw "PUB-009 fixture must expose the deterministic merchant ID."
    }
    if ($fixture.publicServiceMerchantId -ne '33333333-3333-4333-8333-333333333334' -or
        $fixture.emptyNoticeMerchantId -ne '33333333-3333-4333-8333-333333333335' -or
        $fixture.publicMixedMerchantId -ne '33333333-3333-4333-8333-333333333336') {
        throw "PUB-009 fixture merchant IDs must match the visual case catalog."
    }
    $roleIdentities = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select (select count(*) from user_identities where email_normalized in ('$($fixture.buyerEmail)','$($fixture.sellerEmail)','$($fixture.adminEmail)') and verified_at is not null) || '|' || (select count(*) from system_role_assignments where user_id in ('$($fixture.buyerId)','$($fixture.sellerId)') and role='USER') || '|' || (select count(*) from system_role_assignments where user_id='$($fixture.adminId)' and role='ADMIN')"
    if ($roleIdentities -ne "3|2|1") {
        throw "Fixture must provide verified Buyer/Seller/Admin identities and the Admin role; found '$roleIdentities'."
    }
    $buyerDisplayNames = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select string_agg(encode(convert_to(display_name,'UTF8'),'base64'), '|' order by id) from users where id in ('11111111-1111-4111-8111-111111111112','11111111-1111-4111-8111-111111111113')"
    if ($buyerDisplayNames -ne "QnV5ZXIgQ2jGsGEgQ8OzIEzhu4tjaCBT4but|QnV5ZXIgQ+G6p24gTMawdSDDnQ==") {
        throw "Fixture buyer display names must retain their exact UTF-8 text; found '$buyerDisplayNames'."
    }
    $adminDirectoryTimestamps = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select (select count(*) from users where id in ('$($fixture.buyerId)','11111111-1111-4111-8111-111111111112','11111111-1111-4111-8111-111111111113','$($fixture.sellerId)','$($fixture.adminId)') and updated_at='2026-09-16T00:00:00Z') || '|' || (select count(*) from merchant_profiles where id in ('$($fixture.merchantId)','$($fixture.publicServiceMerchantId)','$($fixture.emptyNoticeMerchantId)','$($fixture.publicMixedMerchantId)') and updated_at='2026-09-16T00:00:00Z')"
    if ($adminDirectoryTimestamps -ne "5|4") {
        throw "ADM-002/003 fixture timestamps must be deterministic; found '$adminDirectoryTimestamps'."
    }
    $sellerMembership = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select count(*) from merchant_memberships where merchant_profile_id='$($fixture.merchantId)' and user_id='$($fixture.sellerId)' and membership_status='ACTIVE' and role='OWNER'"
    if ([int]$sellerMembership -ne 1) { throw "Seller fixture must own the deterministic merchant." }
    $emptyNoticeMembership = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select count(*) from merchant_memberships where merchant_profile_id='$($fixture.emptyNoticeMerchantId)' and user_id='$($fixture.sellerId)' and membership_status='ACTIVE' and role='OWNER'"
    if ([int]$emptyNoticeMembership -ne 1) { throw "Seller fixture must own the deterministic empty-notice merchant." }
    $sellerNotices = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select count(*) || '|' || count(*) filter (where payload->>'targetId'='$($fixture.merchantId)') || '|' || count(*) filter (where payload->>'targetId'='$($fixture.emptyNoticeMerchantId)') from notifications where user_id='$($fixture.sellerId)' and notification_type='MODERATION_UPDATED' and id='18181818-1818-4181-8181-181818181221'"
    if ($sellerNotices -ne "1|1|0") {
        throw "Seller fixture must provide one scoped moderation notice and none for the empty merchant; found '$sellerNotices'."
    }
    $moderationQueueState = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select count(*) || '|' || count(*) filter (where id='20510000-0000-4000-8000-000000000001' and target_type='CATALOG_ITEM' and reason_code='SPAM_OR_MISLEADING' and opened_at='2026-09-16T06:00:00Z') || '|' || count(*) filter (where id='20500000-0000-4000-8000-000000000002' and target_type='MERCHANT' and reason_code='POLICY_VIOLATION' and opened_at='2026-09-16T05:00:00Z') from moderation_cases where id in ('20510000-0000-4000-8000-000000000001','20500000-0000-4000-8000-000000000002') and case_type='USER_REPORT' and case_status='OPEN'"
    if ($moderationQueueState -ne "2|1|1") {
        throw "ADM-009 fixture must provide two deterministic open reports; found '$moderationQueueState'."
    }
    $adminAuditState = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select count(*) || '|' || count(distinct action) || '|' || count(*) filter (where created_at between '2026-09-16T07:00:00Z' and '2026-09-16T07:04:00Z') || '|' || count(*) filter (where entity_type='UI_VISUAL_ACCEPTANCE') from audit_logs where id in ('17171717-1717-4171-8171-171717171701','17171717-1717-4171-8171-171717171702','17171717-1717-4171-8171-171717171703','17171717-1717-4171-8171-171717171704','17171717-1717-4171-8171-171717171705')"
    if ($adminAuditState -ne "5|5|5|5") {
        throw "ADM-017 fixture must provide five deterministic audit operations; found '$adminAuditState'."
    }
    $activeCartCount = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select count(*) from carts where user_id='$($fixture.buyerId)' and cart_status='ACTIVE'"
    if ([int]$activeCartCount -ne 0) {
        throw "UI fixture seed must leave zero active carts; found $activeCartCount."
    }

    $categorySlugs = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select string_agg(slug, '|' order by sort_order) from categories where slug in ('ui-do-an','ui-do-uong','ui-tap-hoa','ui-dich-vu')"
    if ($categorySlugs -ne "ui-do-an|ui-do-uong|ui-tap-hoa|ui-dich-vu") {
        throw "UI fixture seed must provide the four deterministic Home categories; found '$categorySlugs'."
    }
    $locationAreaTree = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select count(*) || '|' || count(*) filter (where id='23232323-2323-4232-8232-232323232301' and parent_id is null and area_type='COMMUNITY') || '|' || count(*) filter (where id='23232323-2323-4232-8232-232323232302' and parent_id='23232323-2323-4232-8232-232323232301' and area_type='ZONE') || '|' || count(*) filter (where id='23232323-2323-4232-8232-232323232303' and parent_id='23232323-2323-4232-8232-232323232302' and area_type='WARD') from location_areas where id in ('23232323-2323-4232-8232-232323232301','23232323-2323-4232-8232-232323232302','23232323-2323-4232-8232-232323232303') and status='ACTIVE'"
    if ($locationAreaTree -ne "3|1|1|1") {
        throw "ADM-015 fixture must provide a deterministic three-level active location-area tree; found '$locationAreaTree'."
    }

    $serviceOptions = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select count(*) || '|' || count(*) filter (where v.is_default) || '|' || coalesce(max(lv.price) filter (where v.is_default),0) from variants v join location_variants lv on lv.variant_id=v.id where v.catalog_item_id='$($fixture.serviceItemId)' and v.variant_status='ACTIVE' and lv.is_available"
    if ($serviceOptions -ne "3|1|280000") {
        throw "PUB-007 fixture must provide three available service options with 280000 as the single default; found '$serviceOptions'."
    }

    $trackedInventory = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select stock_on_hand || '|' || reserved_quantity || '|' || (stock_on_hand-reserved_quantity) from inventory_balances where location_variant_id='$($fixture.productOfferId)'"
    if ($trackedInventory -ne "50|2|48") {
        throw "SEL inventory fixture must expose reserved stock for the stale-conflict state; found '$trackedInventory'."
    }

    $merchantProfile = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select count(*) || '|' || max(completion_rate_basis_points) || '|' || (select count(*) from merchant_contact_methods where merchant_profile_id='$($fixture.merchantId)' and is_public and status='ACTIVE') from merchant_reputation_stats where merchant_profile_id='$($fixture.merchantId)'"
    if ($merchantProfile -ne "1|9600|3") {
        throw "PUB-009 fixture must provide public trust and three contact methods; found '$merchantProfile'."
    }

    $publicCatalogShapes = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select (select count(*) from catalog_items ci join variants v on v.catalog_item_id=ci.id and v.variant_status='ACTIVE' and v.deleted_at is null join location_variants lv on lv.variant_id=v.id and lv.is_available and lv.deleted_at is null join merchant_locations ml on ml.id=lv.merchant_location_id and ml.merchant_profile_id=ci.merchant_profile_id and ml.location_status='ACTIVE' and ml.deleted_at is null where ci.merchant_profile_id='$($fixture.merchantId)' and ci.item_type='PRODUCT' and ci.listing_status='ACTIVE' and ci.moderation_status in ('NOT_REQUIRED','APPROVED') and ci.deleted_at is null) || '|' || (select count(*) from catalog_items ci join variants v on v.catalog_item_id=ci.id and v.variant_status='ACTIVE' and v.deleted_at is null join location_variants lv on lv.variant_id=v.id and lv.is_available and lv.deleted_at is null join merchant_locations ml on ml.id=lv.merchant_location_id and ml.merchant_profile_id=ci.merchant_profile_id and ml.location_status='ACTIVE' and ml.deleted_at is null where ci.merchant_profile_id='$($fixture.merchantId)' and ci.item_type='SERVICE' and ci.listing_status='ACTIVE' and ci.moderation_status in ('NOT_REQUIRED','APPROVED') and ci.deleted_at is null) || '|' || (select count(*) from catalog_items ci join variants v on v.catalog_item_id=ci.id and v.variant_status='ACTIVE' and v.deleted_at is null join location_variants lv on lv.variant_id=v.id and lv.is_available and lv.deleted_at is null join merchant_locations ml on ml.id=lv.merchant_location_id and ml.merchant_profile_id=ci.merchant_profile_id and ml.location_status='ACTIVE' and ml.deleted_at is null where ci.merchant_profile_id='$($fixture.publicServiceMerchantId)' and ci.item_type='SERVICE' and ci.listing_status='ACTIVE' and ci.moderation_status in ('NOT_REQUIRED','APPROVED') and ci.deleted_at is null) || '|' || (select count(*) from catalog_items ci join variants v on v.catalog_item_id=ci.id and v.variant_status='ACTIVE' and v.deleted_at is null join location_variants lv on lv.variant_id=v.id and lv.is_available and lv.deleted_at is null join merchant_locations ml on ml.id=lv.merchant_location_id and ml.merchant_profile_id=ci.merchant_profile_id and ml.location_status='ACTIVE' and ml.deleted_at is null where ci.merchant_profile_id='$($fixture.publicMixedMerchantId)' and ci.item_type='PRODUCT' and ci.listing_status='ACTIVE' and ci.moderation_status in ('NOT_REQUIRED','APPROVED') and ci.deleted_at is null) || '|' || (select count(*) from catalog_items ci join variants v on v.catalog_item_id=ci.id and v.variant_status='ACTIVE' and v.deleted_at is null join location_variants lv on lv.variant_id=v.id and lv.is_available and lv.deleted_at is null join merchant_locations ml on ml.id=lv.merchant_location_id and ml.merchant_profile_id=ci.merchant_profile_id and ml.location_status='ACTIVE' and ml.deleted_at is null where ci.merchant_profile_id='$($fixture.publicMixedMerchantId)' and ci.item_type='SERVICE' and ci.listing_status='ACTIVE' and ci.moderation_status in ('NOT_REQUIRED','APPROVED') and ci.deleted_at is null) || '|' || (select count(*) from catalog_items ci join variants v on v.catalog_item_id=ci.id and v.variant_status='ACTIVE' and v.deleted_at is null join location_variants lv on lv.variant_id=v.id and lv.is_available and lv.deleted_at is null join merchant_locations ml on ml.id=lv.merchant_location_id and ml.merchant_profile_id=ci.merchant_profile_id and ml.location_status='ACTIVE' and ml.deleted_at is null where ci.merchant_profile_id='$($fixture.emptyNoticeMerchantId)' and ci.listing_status='ACTIVE' and ci.moderation_status in ('NOT_REQUIRED','APPROVED') and ci.deleted_at is null)"
    if ($publicCatalogShapes -ne "4|0|3|1|1|0") {
        throw "PUB-009 fixture must expose four Product-only offer rows (three-card page plus cursor), Service-only 3 variant rows, mixed 1+1, and empty 0 public rows; found '$publicCatalogShapes'."
    }

    $buyerAddresses = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select count(*) || '|' || count(*) filter (where is_default) || '|' || count(*) filter (where id='99999999-9999-4999-8999-999999999998' and note is not null) || '|' || count(*) filter (where id='$($fixture.addressId)' and note is null) from user_addresses where user_id='$($fixture.buyerId)' and status='ACTIVE'"
    if ($buyerAddresses -ne "2|1|1|1") {
        throw "BUY-003/004 fixture must provide two addresses, exactly one default, a company note, and no stale primary-address note; found '$buyerAddresses'."
    }

    $expectedOrderIds = @(
        '12121212-1212-4121-8121-121212121221',
        '12121212-1212-4121-8121-121212121222',
        '12121212-1212-4121-8121-121212121223',
        '12121212-1212-4121-8121-121212121224'
    )
    $actualOrderIds = @(
        $fixture.pendingProductOrderId,
        $fixture.deliveryProductOrderId,
        $fixture.pickupProductOrderId,
        $fixture.serviceOrderId
    )
    if (($actualOrderIds -join '|') -ne ($expectedOrderIds -join '|')) {
        throw "Seller/Admin order fixture IDs must match the visual case catalog; found '$($actualOrderIds -join '|')'."
    }

    $orderState = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select count(*) || '|' || count(*) filter (where order_type='PRODUCT') || '|' || count(*) filter (where order_type='SERVICE') || '|' || count(*) filter (where status='PENDING_CONFIRMATION') || '|' || count(*) filter (where status='CONFIRMED') from orders where id in ('$($expectedOrderIds[0])','$($expectedOrderIds[1])','$($expectedOrderIds[2])','$($expectedOrderIds[3])') and buyer_user_id='$($fixture.buyerId)' and merchant_profile_id='$($fixture.merchantId)' and merchant_location_id='$($fixture.locationId)'"
    if ($orderState -ne "4|3|1|1|3") {
        throw "Seller/Admin fixture must provide one pending and three confirmed Product/Service orders; found '$orderState'."
    }
    $fixtureBuyerOrderCount = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select count(*) from orders where buyer_user_id='$($fixture.buyerId)'"
    if ([int]$fixtureBuyerOrderCount -ne 7) {
        throw "UI fixture seed must remove transient checkout orders for the disposable buyer; found '$fixtureBuyerOrderCount'."
    }

    $fulfillmentState = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select (select count(*) from order_items where order_id in ('$($expectedOrderIds[0])','$($expectedOrderIds[1])','$($expectedOrderIds[2])','$($expectedOrderIds[3])') and item_snapshot <> '{}'::jsonb) || '|' || (select count(*) from product_fulfillments where order_id in ('$($expectedOrderIds[0])','$($expectedOrderIds[1])','$($expectedOrderIds[2])') and fulfillment_method='SELLER_DELIVERY') || '|' || (select count(*) from product_fulfillments where order_id='$($expectedOrderIds[2])' and fulfillment_method='PICKUP') || '|' || (select count(*) from service_fulfillments where order_id='$($expectedOrderIds[3])' and distance_meters is not null) || '|' || (select count(*) from order_events where order_id in ('$($expectedOrderIds[0])','$($expectedOrderIds[1])','$($expectedOrderIds[2])','$($expectedOrderIds[3])'))"
    if ($fulfillmentState -ne "4|2|1|1|7") {
        throw "Seller/Admin fixture must provide complete item, fulfillment, distance, and timeline snapshots; found '$fulfillmentState'."
    }

    & pnpm ui:seed-local | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "Second UI fixture seed failed." }
    $secondFixture = Get-Content -LiteralPath $fixturePath -Raw | ConvertFrom-Json
    $secondOrderIds = @(
        $secondFixture.pendingProductOrderId,
        $secondFixture.deliveryProductOrderId,
        $secondFixture.pickupProductOrderId,
        $secondFixture.serviceOrderId
    )
    if (($secondOrderIds -join '|') -ne ($expectedOrderIds -join '|')) {
        throw "Repeated fixture seed must preserve deterministic order IDs; found '$($secondOrderIds -join '|')'."
    }
    $reseededOrderState = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select count(*) || '|' || (select count(*) from order_items where order_id in ('$($expectedOrderIds[0])','$($expectedOrderIds[1])','$($expectedOrderIds[2])','$($expectedOrderIds[3])')) || '|' || (select count(*) from order_events where order_id in ('$($expectedOrderIds[0])','$($expectedOrderIds[1])','$($expectedOrderIds[2])','$($expectedOrderIds[3])')) from orders where id in ('$($expectedOrderIds[0])','$($expectedOrderIds[1])','$($expectedOrderIds[2])','$($expectedOrderIds[3])')"
    if ($reseededOrderState -ne "4|4|7") {
        throw "Repeated fixture seed must remain idempotent for orders, items, and events; found '$reseededOrderState'."
    }
    $reseededModerationQueueState = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select count(*) || '|' || count(*) filter (where opened_at in ('2026-09-16T05:00:00Z','2026-09-16T06:00:00Z')) from moderation_cases where id in ('20510000-0000-4000-8000-000000000001','20500000-0000-4000-8000-000000000002') and case_status='OPEN'"
    if ($reseededModerationQueueState -ne "2|2") {
        throw "Repeated fixture seed must preserve the deterministic Admin report queue; found '$reseededModerationQueueState'."
    }
    $reseededAdminAuditState = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select count(*) from audit_logs where id in ('17171717-1717-4171-8171-171717171701','17171717-1717-4171-8171-171717171702','17171717-1717-4171-8171-171717171703','17171717-1717-4171-8171-171717171704','17171717-1717-4171-8171-171717171705') and created_at between '2026-09-16T07:00:00Z' and '2026-09-16T07:04:00Z'"
    if ([int]$reseededAdminAuditState -ne 5) {
        throw "Repeated fixture seed must preserve five deterministic Admin audit operations; found '$reseededAdminAuditState'."
    }
    $reseededLocationAreaTree = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select count(*) from location_areas where id in ('23232323-2323-4232-8232-232323232301','23232323-2323-4232-8232-232323232302','23232323-2323-4232-8232-232323232303') and status='ACTIVE'"
    if ([int]$reseededLocationAreaTree -ne 3) {
        throw "Repeated fixture seed must preserve three deterministic location areas; found '$reseededLocationAreaTree'."
    }

    $expectedNeedIds = @(
        '15151515-1515-4151-8151-151515151221',
        '15151515-1515-4151-8151-151515151222',
        '15151515-1515-4151-8151-151515151223'
    )
    $actualNeedIds = @($secondFixture.candidateNeedId, $secondFixture.respondedNeedId, $secondFixture.emptyCandidateNeedId)
    if (($actualNeedIds -join '|') -ne ($expectedNeedIds -join '|')) {
        throw "Seller Need fixture IDs must remain deterministic; found '$($actualNeedIds -join '|')'."
    }
    $needState = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select count(*) || '|' || count(*) filter (where need_type='PRODUCT') || '|' || count(*) filter (where need_type='SERVICE') || '|' || count(*) filter (where request_status='ACTIVE' and moderation_status='APPROVED') || '|' || (select count(*) from need_responses where need_request_id in ('$($expectedNeedIds[0])','$($expectedNeedIds[1])','$($expectedNeedIds[2])') and response_status='ACTIVE') || '|' || (select count(*) from need_response_items nri join need_responses nr on nr.id=nri.need_response_id where nr.need_request_id in ('$($expectedNeedIds[0])','$($expectedNeedIds[1])','$($expectedNeedIds[2])') and nri.item_status='ACTIVE') from need_requests where id in ('$($expectedNeedIds[0])','$($expectedNeedIds[1])','$($expectedNeedIds[2])')"
    if ($needState -ne "3|3|0|3|1|1") {
        throw "Seller Need fixture must provide candidate, responded, and empty-candidate scenarios; found '$needState'."
    }

    Write-Output "seed-ui-fixture regression checks passed"
} finally {
    Pop-Location
}
