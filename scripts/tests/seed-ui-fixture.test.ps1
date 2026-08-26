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

    Write-Output "seed-ui-fixture regression checks passed"
} finally {
    Pop-Location
}
