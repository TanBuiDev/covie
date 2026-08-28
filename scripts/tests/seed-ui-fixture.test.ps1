[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$fixturePath = Join-Path $repositoryRoot ".local/dev/ui-fixture.json"

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
    if ($fixture.merchantId -ne '33333333-3333-4333-8333-333333333333') {
        throw "PUB-009 fixture must expose the deterministic merchant ID."
    }
    foreach ($actor in @('buyer', 'seller', 'admin')) {
        $actorFixture = $fixture.actors.$actor
        if ($null -eq $actorFixture -or [string]::IsNullOrWhiteSpace($actorFixture.email) -or [string]::IsNullOrWhiteSpace($actorFixture.password)) {
            throw "Task 13 fixture must expose ignored local credentials for the $actor actor."
        }
    }
    if ($fixture.actors.seller.userId -ne '22222222-2222-4222-8222-222222222222' -or
        $fixture.actors.admin.userId -ne 'aaaaaaaa-1111-4111-8111-111111111111') {
        throw "Task 13 fixture must expose deterministic Seller and Admin IDs."
    }
    if ([long]$fixture.scenarios.staleCatalogVersion -ge [long]$fixture.scenarios.currentCatalogVersion) {
        throw "Task 13 fixture must expose an intentionally stale catalog version."
    }
    $missingCatalogItemCount = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select count(*) from catalog_items where id='$($fixture.scenarios.missingCatalogItemId)'"
    if ([int]$missingCatalogItemCount -ne 0) {
        throw "Task 13 error-safe catalog scenario must resolve to a deterministic missing resource."
    }
    $activeCartCount = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select count(*) from carts where user_id='$($fixture.buyerId)' and cart_status='ACTIVE'"
    if ([int]$activeCartCount -ne 0) {
        throw "UI fixture seed must leave zero active carts; found $activeCartCount."
    }

    $categorySlugs = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select string_agg(slug, '|' order by sort_order) from categories where slug in ('ui-do-an','ui-do-uong','ui-tap-hoa','ui-dich-vu')"
    if ($categorySlugs -ne "ui-do-an|ui-do-uong|ui-tap-hoa|ui-dich-vu") {
        throw "UI fixture seed must provide the four deterministic Home categories; found '$categorySlugs'."
    }

    $serviceOptions = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select count(*) || '|' || count(*) filter (where v.is_default) || '|' || coalesce(max(lv.price) filter (where v.is_default),0) from variants v join location_variants lv on lv.variant_id=v.id where v.catalog_item_id='$($fixture.serviceItemId)' and v.variant_status='ACTIVE' and lv.is_available"
    if ($serviceOptions -ne "3|1|280000") {
        throw "PUB-007 fixture must provide three available service options with 280000 as the single default; found '$serviceOptions'."
    }

    $merchantProfile = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select count(*) || '|' || max(completion_rate_basis_points) || '|' || (select count(*) from merchant_contact_methods where merchant_profile_id='$($fixture.merchantId)' and is_public and status='ACTIVE') from merchant_reputation_stats where merchant_profile_id='$($fixture.merchantId)'"
    if ($merchantProfile -ne "1|9600|3") {
        throw "PUB-009 fixture must provide public trust and three contact methods; found '$merchantProfile'."
    }

    $buyerAddresses = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select count(*) || '|' || count(*) filter (where is_default) || '|' || count(*) filter (where id='99999999-9999-4999-8999-999999999998' and note is not null) from user_addresses where user_id='$($fixture.buyerId)' and status='ACTIVE'"
    if ($buyerAddresses -ne "2|1|1") {
        throw "BUY-003 fixture must provide two addresses with exactly one default; found '$buyerAddresses'."
    }

    $actorRoles = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select count(*) filter (where user_id='$($fixture.actors.buyer.userId)' and role='USER') || '|' || count(*) filter (where user_id='$($fixture.actors.seller.userId)' and role='USER') || '|' || count(*) filter (where user_id='$($fixture.actors.admin.userId)' and role='ADMIN') from system_role_assignments"
    if ($actorRoles -ne "1|1|1") {
        throw "Task 13 fixture must seed Buyer, Seller, and Admin role assignments; found '$actorRoles'."
    }

    $representativeStates = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select (select count(*) from carts where id='$($fixture.scenarios.abandonedCartId)' and cart_status='ABANDONED') || '|' || (select count(*) from orders where buyer_user_id='$($fixture.actors.buyer.userId)' and shipping_fee_type='NEGOTIATED') || '|' || (select count(*) from need_requests where buyer_user_id='$($fixture.actors.buyer.userId)') || '|' || (select count(*) from reviews where buyer_user_id='$($fixture.actors.buyer.userId)') || '|' || (select count(*) from moderation_cases where reporter_user_id='$($fixture.actors.buyer.userId)') || '|' || (select count(*) from system_settings where updated_by_user_id='$($fixture.actors.admin.userId)')"
    if ($representativeStates -ne "1|1|2|1|2|2") {
        throw "Task 13 representative cart/order/need/review/report/settings states are incomplete; found '$representativeStates'."
    }

    $sellerOperations = & docker exec compose-postgres-1 psql -U covie -d covie -tAc "select (select count(*) from variants where catalog_item_id in ('$($fixture.productItemId)','$($fixture.serviceItemId)','$($fixture.resaleItemId)')) || '|' || (select count(*) from merchant_locations where merchant_profile_id='$($fixture.merchantId)') || '|' || (select count(*) from shipping_policies sp join merchant_locations ml on ml.id=sp.merchant_location_id where ml.merchant_profile_id='$($fixture.merchantId)') || '|' || (select count(*) from inventory_balances ib join location_variants lv on lv.id=ib.location_variant_id where lv.merchant_location_id='$($fixture.locationId)')"
    if ($sellerOperations -ne "5|2|2|2") {
        throw "Task 13 seller catalog/location/shipping/inventory states are incomplete; found '$sellerOperations'."
    }

    Write-Output "seed-ui-fixture regression checks passed"
} finally {
    Pop-Location
}
