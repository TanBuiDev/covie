[CmdletBinding()]
param(
    [string]$PostgresContainer = "compose-postgres-1"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$outputPath = Join-Path $repoRoot ".local/dev/ui-fixture.json"

$running = & docker inspect -f '{{.State.Running}}' $PostgresContainer 2>$null
if ($LASTEXITCODE -ne 0 -or $running -ne "true") {
    throw "Local PostgreSQL container '$PostgresContainer' is not running."
}

$buyerId = "11111111-1111-4111-8111-111111111111"
$zeroReputationBuyerId = "11111111-1111-4111-8111-111111111112"
$warningReputationBuyerId = "11111111-1111-4111-8111-111111111113"
$sellerId = "22222222-2222-4222-8222-222222222222"
$adminId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa01"
$buyerEmail = "ui.buyer@covie.local"
$buyerPassword = "Cv!$([Guid]::NewGuid().ToString('N'))"
$zeroReputationBuyerEmail = "ui.buyer.zero@covie.local"
$zeroReputationBuyerPassword = "Cv!$([Guid]::NewGuid().ToString('N'))"
$warningReputationBuyerEmail = "ui.buyer.warning@covie.local"
$warningReputationBuyerPassword = "Cv!$([Guid]::NewGuid().ToString('N'))"
$sellerEmail = "ui.seller@covie.local"
$sellerPassword = "Cv!$([Guid]::NewGuid().ToString('N'))"
$adminEmail = "ui.admin@covie.local"
$adminPassword = "Cv!$([Guid]::NewGuid().ToString('N'))"
$merchantId = "33333333-3333-4333-8333-333333333333"
$publicServiceMerchantId = "33333333-3333-4333-8333-333333333334"
$emptyNoticeMerchantId = "33333333-3333-4333-8333-333333333335"
$publicMixedMerchantId = "33333333-3333-4333-8333-333333333336"
$locationId = "44444444-4444-4444-8444-444444444444"
$publicServiceLocationId = "44444444-4444-4444-8444-444444444445"
$publicMixedLocationId = "44444444-4444-4444-8444-444444444446"
$foodCategoryId = "55555555-5555-4555-8555-555555555551"
$drinkCategoryId = "55555555-5555-4555-8555-555555555552"
$categoryId = "55555555-5555-4555-8555-555555555553"
$serviceCategoryId = "55555555-5555-4555-8555-555555555554"
$productItemId = "66666666-6666-4666-8666-666666666661"
$serviceItemId = "66666666-6666-4666-8666-666666666662"
$resaleItemId = "66666666-6666-4666-8666-666666666663"
$thirdProductItemId = "66666666-6666-4666-8666-666666666664"
$mixedProductItemId = "66666666-6666-4666-8666-666666666665"
$mixedServiceItemId = "66666666-6666-4666-8666-666666666666"
$fourthProductItemId = "66666666-6666-4666-8666-666666666667"
$productVariantId = "77777777-7777-4777-8777-777777777771"
$serviceVariantTwoId = "77777777-7777-4777-8777-777777777772"
$resaleVariantId = "77777777-7777-4777-8777-777777777773"
$serviceVariantOneId = "77777777-7777-4777-8777-777777777774"
$serviceVariantThreeId = "77777777-7777-4777-8777-777777777775"
$thirdProductVariantId = "77777777-7777-4777-8777-777777777776"
$mixedProductVariantId = "77777777-7777-4777-8777-777777777777"
$mixedServiceVariantId = "77777777-7777-4777-8777-777777777778"
$fourthProductVariantId = "77777777-7777-4777-8777-777777777779"
$productOfferId = "88888888-8888-4888-8888-888888888881"
$serviceOfferTwoId = "88888888-8888-4888-8888-888888888882"
$resaleOfferId = "88888888-8888-4888-8888-888888888883"
$serviceOfferOneId = "88888888-8888-4888-8888-888888888884"
$serviceOfferThreeId = "88888888-8888-4888-8888-888888888885"
$thirdProductOfferId = "88888888-8888-4888-8888-888888888886"
$mixedProductOfferId = "88888888-8888-4888-8888-888888888887"
$mixedServiceOfferId = "88888888-8888-4888-8888-888888888888"
$fourthProductOfferId = "88888888-8888-4888-8888-888888888889"
$addressId = "99999999-9999-4999-8999-999999999999"
$pendingProductOrderId = "12121212-1212-4121-8121-121212121221"
$deliveryProductOrderId = "12121212-1212-4121-8121-121212121222"
$pickupProductOrderId = "12121212-1212-4121-8121-121212121223"
$serviceOrderId = "12121212-1212-4121-8121-121212121224"
$reviewEligibleOrderId = "12121212-1212-4121-8121-121212121211"
$alreadyReviewedOrderId = "12121212-1212-4121-8121-121212121212"
$reviewDisabledOrderId = "12121212-1212-4121-8121-121212121213"
$reviewEligibleOrderItemId = "13131313-1313-4131-8131-131313131211"
$alreadyReviewedOrderItemId = "13131313-1313-4131-8131-131313131212"
$reviewDisabledOrderItemId = "13131313-1313-4131-8131-131313131213"
$candidateNeedId = "15151515-1515-4151-8151-151515151221"
$respondedNeedId = "15151515-1515-4151-8151-151515151222"
$emptyCandidateNeedId = "15151515-1515-4151-8151-151515151223"
$respondedNeedResponseId = "16161616-1616-4161-8161-161616161222"
$fixtureDirectoryTimestamp = "2026-09-16T00:00:00Z"

# Keep the script source ASCII-safe for Windows PowerShell 5, which reads UTF-8
# files without a BOM through the active legacy code page.
function ConvertFrom-UiFixtureBase64([string]$value) {
    return [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($value))
}
$sellerDisplayName = ConvertFrom-UiFixtureBase64 "Q2jhu6MgWGFuaCBPd25lcg=="
$zeroReputationBuyerDisplayName = ConvertFrom-UiFixtureBase64 "QnV5ZXIgQ2jGsGEgQ8OzIEzhu4tjaCBT4but"
$warningReputationBuyerDisplayName = ConvertFrom-UiFixtureBase64 "QnV5ZXIgQ+G6p24gTMawdSDDnQ=="
$addressLabel = ConvertFrom-UiFixtureBase64 "TmjDoA=="
$recipientName = ConvertFrom-UiFixtureBase64 "Tmd1eeG7hW4gVsSDbiBNaW5o"
$companyAddressLabel = ConvertFrom-UiFixtureBase64 "Q8O0bmcgdHk="
$homeAddressText = ConvertFrom-UiFixtureBase64 "UzUuMDMsIFZpbmhvbWVzIEdyYW5kIFBhcmssIExvbmcgQsOsbmgsIFRQIFRo4bunIMSQ4bupYw=="
$companyAddressText = ConvertFrom-UiFixtureBase64 "xJDGsOG7nW5nIFbDtSBDaMOtIEPDtG5nLCBUUCBUaOG7pyDEkOG7qWM="
$companyAddressNote = ConvertFrom-UiFixtureBase64 "R2lhbyB04bqhaSBs4buFIHTDom4="
$merchantName = ConvertFrom-UiFixtureBase64 "Q+G7rWEgaMOgbmcgQ2jhu6MgWGFuaA=="
$merchantDescription = ConvertFrom-UiFixtureBase64 "UmF1IGPhu6cgdsOgIGThu4tjaCB24bulIMSR4buLYSBwaMawxqFuZyBjaG8gdmlzdWFsIHJldmlldy4="
$locationName = ConvertFrom-UiFixtureBase64 "Q2jhu6MgWGFuaCBHcmFuZCBQYXJr"
$publicServiceMerchantName = ConvertFrom-UiFixtureBase64 "TmjDoCBjdW5nIGPhuqVwIEThu4tjaCB24bulIENvVmll"
$publicServiceMerchantDescription = ConvertFrom-UiFixtureBase64 "ROG7i2NoIHbhu6UgxJHhu4thIHBoxrDGoW5nIMSRw6MgeMOhYyBtaW5oLg=="
$publicEmptyMerchantName = ConvertFrom-UiFixtureBase64 "xJDGoW4gduG7iyBUcuG7kW5nIENvVmll"
$publicEmptyMerchantDescription = ConvertFrom-UiFixtureBase64 "Q2jGsGEgY8OzIG7hu5lpIGR1bmcgY8O0bmcga2hhaS4="
$publicMixedMerchantName = ConvertFrom-UiFixtureBase64 "xJDGoW4gduG7iyBI4buXbiBo4bujcCBDb1ZpZQ=="
$publicMixedMerchantDescription = ConvertFrom-UiFixtureBase64 "U+G6o24gcGjhuqltIHbDoCBk4buLY2ggduG7pSDEkeG7i2EgcGjGsMahbmcu"
$publicServiceLocationName = ConvertFrom-UiFixtureBase64 "xJBp4buDbSBk4buLY2ggduG7pSBDb1ZpZQ=="
$publicMixedLocationName = ConvertFrom-UiFixtureBase64 "xJBp4buDbSBo4buXbiBo4bujcCBDb1ZpZQ=="
$thirdProductName = ConvertFrom-UiFixtureBase64 "VHLDoWkgY8OieSDEkeG7i2EgcGjGsMahbmc="
$thirdProductDescription = ConvertFrom-UiFixtureBase64 "TsO0bmcgc+G6o24gdMawxqFpIHRoZW8gbcO5YS4="
$fourthProductName = ConvertFrom-UiFixtureBase64 "TeG6rXQgb25nIMSR4buLYSBwaMawxqFuZw=="
$fourthProductDescription = ConvertFrom-UiFixtureBase64 "TeG6rXQgb25nIMSRw7NuZyBjaGFpIHThu6sgaOG7mSDEkeG7i2EgcGjGsMahbmcu"
$mixedProductName = ConvertFrom-UiFixtureBase64 "R+G6oW8gaOG7r3UgY8ah"
$mixedProductDescription = ConvertFrom-UiFixtureBase64 "R+G6oW8gxJHhu4thIHBoxrDGoW5nIMSRw7NuZyBnw7NpLg=="
$mixedServiceName = ConvertFrom-UiFixtureBase64 "VuG7hyBzaW5oIG5ow6AgY+G7rWE="
$mixedServiceDescription = ConvertFrom-UiFixtureBase64 "ROG7i2NoIHbhu6UgduG7hyBzaW5oIHRoZW8gZ2nhu50u"
$defaultVariantName = ConvertFrom-UiFixtureBase64 "TeG6t2MgxJHhu4tuaA=="
$standardServicePackageName = ConvertFrom-UiFixtureBase64 "R8OzaSB0acOqdSBjaHXhuqlu"
$foodCategoryName = ConvertFrom-UiFixtureBase64 "xJDhu5MgxINu"
$drinkCategoryName = ConvertFrom-UiFixtureBase64 "xJDhu5MgdeG7kW5n"
$categoryName = ConvertFrom-UiFixtureBase64 "VOG6oXAgaMOzYQ=="
$serviceCategoryName = ConvertFrom-UiFixtureBase64 "ROG7i2NoIHbhu6U="
$productName = ConvertFrom-UiFixtureBase64 "UmF1IGPhu6cgdGhlbyBtw7lh"
$productDescription = ConvertFrom-UiFixtureBase64 "R8OzaSByYXUgY+G7pyB0xrDGoWkgdGhlbyBtw7lhLCB0dXnhu4NuIGNo4buNbiB04bqhaSDEkeG7i2EgcGjGsMahbmcu"
$serviceName = ConvertFrom-UiFixtureBase64 "U+G7rWEga2jDs2EgdOG6rW4gbsahaQ=="
$serviceDescription = ConvertFrom-UiFixtureBase64 "ROG7i2NoIHbhu6Ugc+G7rWEga2jDs2EgdOG6oWkgxJHhu4thIGNo4buJIGPhu6dhIGLhuqFuLg=="
$resaleName = ConvertFrom-UiFixtureBase64 "S+G7hyBn4buXIHRoYW5oIGzDvQ=="
$resaleDescription = ConvertFrom-UiFixtureBase64 "S+G7hyBn4buXIMSRw6Mgc+G7rSBk4bulbmcsIGPDsm4gdOG7kXQu"
$productVariantName = ConvertFrom-UiFixtureBase64 "R8OzaSAxIGtn"
$serviceVariantOneName = ConvertFrom-UiFixtureBase64 "MSBtw6F5"
$serviceVariantTwoName = ConvertFrom-UiFixtureBase64 "MiBtw6F5"
$serviceVariantThreeName = ConvertFrom-UiFixtureBase64 "MyBtw6F5"
$resaleVariantName = ConvertFrom-UiFixtureBase64 "TeG7mXQgY2hp4bq/Yw=="
$candidateNeedTitle = ConvertFrom-UiFixtureBase64 "Q+G6p24gcmF1IGPhu6cgdGhlbyBtw7lh"
$respondedNeedTitle = ConvertFrom-UiFixtureBase64 "Q+G6p24ga+G7hyBn4buXIHRoYW5oIGzDvQ=="
$emptyCandidateNeedTitle = ConvertFrom-UiFixtureBase64 "Q+G6p24gbeG6rXQgb25nIMSR4buLYSBwaMawxqFuZw=="
$needAreaLabel = ConvertFrom-UiFixtureBase64 "S2h1IFM1LCBHcmFuZCBQYXJr"
$cityAreaName = ConvertFrom-UiFixtureBase64 "VFAuIEjhu5MgQ2jDrSBNaW5o"
$districtAreaName = ConvertFrom-UiFixtureBase64 "VFAuIFRo4bunIMSQ4bupYw=="
$wardAreaName = ConvertFrom-UiFixtureBase64 "TG9uZyBCw6xuaA=="

$javaExecutable = (Get-Command java -ErrorAction Stop).Source
$env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $javaExecutable)
$apiDirectory = Join-Path $repoRoot "apps/api"
Push-Location $apiDirectory
try {
    $fixtureDependencyDirectory = Join-Path $apiDirectory "target/ui-fixture-dependencies"
    & ./mvnw -q dependency:copy-dependencies `
        "-DincludeArtifactIds=spring-security-crypto,bcprov-jdk18on,commons-logging" `
        "-DoutputDirectory=target/ui-fixture-dependencies" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not resolve the Argon2 fixture dependencies." }
    $fixtureDependencyJars = @(Get-ChildItem -LiteralPath $fixtureDependencyDirectory -Filter '*.jar' | Sort-Object Name)
    if ($fixtureDependencyJars.Count -ne 3) {
        throw "Could not resolve the minimal Argon2 fixture classpath."
    }
    $fixtureClasspath = ($fixtureDependencyJars | ForEach-Object {
        "target/ui-fixture-dependencies/$($_.Name)"
    }) -join [IO.Path]::PathSeparator
    $snippet = @"
/env -class-path $fixtureClasspath
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
var encoder = new Argon2PasswordEncoder(16,32,1,19456,2);
System.out.println("FIXTURE_BUYER_HASH=" + "{argon2id-v1}" + encoder.encode("$buyerPassword"));
System.out.println("FIXTURE_ZERO_BUYER_HASH=" + "{argon2id-v1}" + encoder.encode("$zeroReputationBuyerPassword"));
System.out.println("FIXTURE_WARNING_BUYER_HASH=" + "{argon2id-v1}" + encoder.encode("$warningReputationBuyerPassword"));
System.out.println("FIXTURE_SELLER_HASH=" + "{argon2id-v1}" + encoder.encode("$sellerPassword"));
System.out.println("FIXTURE_ADMIN_HASH=" + "{argon2id-v1}" + encoder.encode("$adminPassword"));
/exit
"@
    $jshell = Join-Path $env:JAVA_HOME "bin/jshell.exe"
    # Configure classpath through JShell input instead of a native argument;
    # Windows PowerShell 5 can silently drop --class-path values.
    $snippetPath = Join-Path $apiDirectory "target/ui-fixture-hash.jsh"
    [IO.File]::WriteAllText($snippetPath, $snippet, [Text.UTF8Encoding]::new($false))
    try {
        $hashOutput = & $jshell $snippetPath 2>&1
    } finally {
        Remove-Item -LiteralPath $snippetPath -ErrorAction SilentlyContinue
    }
    $joinedHashOutput = $hashOutput -join "`n"
    $buyerHashMatch = [regex]::Match($joinedHashOutput, 'FIXTURE_BUYER_HASH=(\{argon2id-v1\}\$argon2id\$[^\r\n]+)')
    $zeroBuyerHashMatch = [regex]::Match($joinedHashOutput, 'FIXTURE_ZERO_BUYER_HASH=(\{argon2id-v1\}\$argon2id\$[^\r\n]+)')
    $warningBuyerHashMatch = [regex]::Match($joinedHashOutput, 'FIXTURE_WARNING_BUYER_HASH=(\{argon2id-v1\}\$argon2id\$[^\r\n]+)')
    $sellerHashMatch = [regex]::Match($joinedHashOutput, 'FIXTURE_SELLER_HASH=(\{argon2id-v1\}\$argon2id\$[^\r\n]+)')
    $adminHashMatch = [regex]::Match($joinedHashOutput, 'FIXTURE_ADMIN_HASH=(\{argon2id-v1\}\$argon2id\$[^\r\n]+)')
    if (-not $buyerHashMatch.Success -or -not $zeroBuyerHashMatch.Success -or -not $warningBuyerHashMatch.Success -or -not $sellerHashMatch.Success -or -not $adminHashMatch.Success) {
        $matchState = @(
            "buyer=$($buyerHashMatch.Success)",
            "zeroBuyer=$($zeroBuyerHashMatch.Success)",
            "warningBuyer=$($warningBuyerHashMatch.Success)",
            "seller=$($sellerHashMatch.Success)",
            "admin=$($adminHashMatch.Success)"
        ) -join ", "
        $diagnosticLines = @($hashOutput | Where-Object { "$_" -match "FIXTURE_|Exception|Error|cannot find|symbol:" } | ForEach-Object {
            (("$_" -replace '"Cv![^"]+"', '"[REDACTED]"') -replace '=\{argon2id-v1\}.*$', '=[REDACTED]')
        }) -join ' | '
        throw "Could not generate the disposable multi-role Argon2 fixture credentials ($matchState; jshellExit=$LASTEXITCODE; outputLines=$($hashOutput.Count); diagnostics=$diagnosticLines)."
    }
    $passwordHash = $buyerHashMatch.Groups[1].Value.Trim()
    $zeroReputationBuyerPasswordHash = $zeroBuyerHashMatch.Groups[1].Value.Trim()
    $warningReputationBuyerPasswordHash = $warningBuyerHashMatch.Groups[1].Value.Trim()
    $sellerPasswordHash = $sellerHashMatch.Groups[1].Value.Trim()
    $adminPasswordHash = $adminHashMatch.Groups[1].Value.Trim()
} finally {
    Pop-Location
}

$sql = @"
\set ON_ERROR_STOP on
begin;
-- A seed run starts a fresh acceptance journey without deleting unrelated local
-- data. The buyer is disposable, so remove only its non-canonical checkout
-- orders and their dependent rows before restoring the seven canonical orders.
update carts
set cart_status='ABANDONED', updated_at=now(), version=version+1
where user_id='$buyerId' and cart_status='ACTIVE';

create temporary table ui_transient_orders on commit drop as
select id
from orders
where buyer_user_id='$buyerId'
  and id not in (
    '$pendingProductOrderId','$deliveryProductOrderId','$pickupProductOrderId','$serviceOrderId',
    '$reviewEligibleOrderId','$alreadyReviewedOrderId','$reviewDisabledOrderId'
  );

update need_requests
set fulfilled_order_id=null,
    request_status=case when request_status='FULFILLED' then 'ACTIVE' else request_status end,
    closed_at=case when request_status='FULFILLED' then null else closed_at end,
    updated_at=now()
where buyer_user_id='$buyerId'
  and fulfilled_order_id in (select id from ui_transient_orders);
delete from inventory_movements where order_id in (select id from ui_transient_orders);
delete from inventory_reservations where order_id in (select id from ui_transient_orders);
delete from reputation_order_outcomes where order_id in (select id from ui_transient_orders);
delete from reviews where order_id in (select id from ui_transient_orders);
delete from order_cancellation_requests where order_id in (select id from ui_transient_orders);
delete from order_issues where order_id in (select id from ui_transient_orders);
delete from order_events where order_id in (select id from ui_transient_orders);
delete from product_fulfillments where order_id in (select id from ui_transient_orders);
delete from service_fulfillments where order_id in (select id from ui_transient_orders);
delete from order_items where order_id in (select id from ui_transient_orders);
delete from orders where id in (select id from ui_transient_orders);

insert into users(id,display_name,account_status,created_at,updated_at,version) values
('$buyerId','Minh Buyer','ACTIVE','$fixtureDirectoryTimestamp','$fixtureDirectoryTimestamp',0),
('$zeroReputationBuyerId','$zeroReputationBuyerDisplayName','ACTIVE','$fixtureDirectoryTimestamp','$fixtureDirectoryTimestamp',0),
('$warningReputationBuyerId','$warningReputationBuyerDisplayName','ACTIVE','$fixtureDirectoryTimestamp','$fixtureDirectoryTimestamp',0),
('$sellerId','$sellerDisplayName','ACTIVE','$fixtureDirectoryTimestamp','$fixtureDirectoryTimestamp',0),
('$adminId','Admin CoVie','ACTIVE','$fixtureDirectoryTimestamp','$fixtureDirectoryTimestamp',0)
on conflict(id) do update set display_name=excluded.display_name,account_status='ACTIVE',updated_at=excluded.updated_at;

insert into system_role_assignments(id,user_id,role,created_at) values
('11111111-1111-4111-8111-333333333333','$buyerId','USER',now()),
('11111111-1111-4111-8111-333333333334','$zeroReputationBuyerId','USER',now()),
('11111111-1111-4111-8111-333333333335','$warningReputationBuyerId','USER',now()),
('22222222-2222-4222-8222-333333333333','$sellerId','USER',now()),
('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa02','$adminId','USER',now()),
('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa04','$adminId','ADMIN',now())
on conflict(user_id,role) do nothing;

insert into user_identities(id,user_id,provider,email_normalized,password_hash,verified_at,created_at,last_used_at) values
('11111111-1111-4111-8111-555555555555','$buyerId','EMAIL','$buyerEmail','$passwordHash',now(),now(),now()),
('11111111-1111-4111-8111-555555555556','$zeroReputationBuyerId','EMAIL','$zeroReputationBuyerEmail','$zeroReputationBuyerPasswordHash',now(),now(),now()),
('11111111-1111-4111-8111-555555555557','$warningReputationBuyerId','EMAIL','$warningReputationBuyerEmail','$warningReputationBuyerPasswordHash',now(),now(),now()),
('22222222-2222-4222-8222-555555555555','$sellerId','EMAIL','$sellerEmail','$sellerPasswordHash',now(),now(),now()),
('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa03','$adminId','EMAIL','$adminEmail','$adminPasswordHash',now(),now(),now())
on conflict(id) do update set email_normalized=excluded.email_normalized,password_hash=excluded.password_hash,verified_at=now(),last_used_at=now();

update system_settings set value_json='true'::jsonb,version=0,updated_by_user_id='$adminId',updated_at=now()
where setting_key='review.enabled' and scope_type='GLOBAL';
insert into system_settings(id,setting_key,value_type,value_json,scope_type,updated_by_user_id,created_at,updated_at,version)
select '20202020-2020-4202-8202-202020202021','review.enabled','BOOLEAN','true'::jsonb,'GLOBAL','$adminId',now(),now(),0
where not exists(select 1 from system_settings where setting_key='review.enabled' and scope_type='GLOBAL');
update system_settings set value_json='8000'::jsonb,version=0,updated_by_user_id='$adminId',updated_at=now()
where setting_key='reputation.warning_threshold_basis_points' and scope_type='GLOBAL';
insert into system_settings(id,setting_key,value_type,value_json,scope_type,updated_by_user_id,created_at,updated_at,version)
select '20202020-2020-4202-8202-202020202022','reputation.warning_threshold_basis_points','INTEGER','8000'::jsonb,'GLOBAL','$adminId',now(),now(),0
where not exists(select 1 from system_settings where setting_key='reputation.warning_threshold_basis_points' and scope_type='GLOBAL');
update system_settings set value_json='3'::jsonb,version=0,updated_by_user_id='$adminId',updated_at=now()
where setting_key='reputation.minimum_eligible_orders_for_warning' and scope_type='GLOBAL';
insert into system_settings(id,setting_key,value_type,value_json,scope_type,updated_by_user_id,created_at,updated_at,version)
select '20202020-2020-4202-8202-202020202023','reputation.minimum_eligible_orders_for_warning','INTEGER','3'::jsonb,'GLOBAL','$adminId',now(),now(),0
where not exists(select 1 from system_settings where setting_key='reputation.minimum_eligible_orders_for_warning' and scope_type='GLOBAL');

-- Browser acceptance creates real addresses. Retire only non-canonical rows owned
-- by the disposable fixture buyer so every seed returns BUY-003 to two addresses
-- without deleting records that may be referenced by historical local orders.
update user_addresses
set status='DELETED', is_default=false, updated_at=now(), version=version+1
where user_id='$buyerId'
  and id not in ('99999999-9999-4999-8999-999999999999','99999999-9999-4999-8999-999999999998')
  and status='ACTIVE';

insert into user_addresses(id,user_id,label,recipient_name,phone,address_text,geo_point,pin_source,is_default,status,created_at,updated_at,version)
values('$addressId','$buyerId','$addressLabel','$recipientName','0909 123 456','$homeAddressText',ST_SetSRID(ST_MakePoint(106.842,10.842),4326)::geography,'USER_ADJUSTED',true,'ACTIVE',now(),now(),0)
on conflict(id) do update set label=excluded.label,recipient_name=excluded.recipient_name,phone=excluded.phone,address_text=excluded.address_text,note=null,status='ACTIVE',is_default=true,updated_at=now();

insert into user_addresses(id,user_id,label,recipient_name,address_text,geo_point,pin_source,note,is_default,status,created_at,updated_at,version)
values('99999999-9999-4999-8999-999999999998','$buyerId','$companyAddressLabel','$recipientName','$companyAddressText',ST_SetSRID(ST_MakePoint(106.805,10.835),4326)::geography,'USER_ADJUSTED','$companyAddressNote',false,'ACTIVE',now(),now(),0)
on conflict(id) do update set label=excluded.label,recipient_name=excluded.recipient_name,address_text=excluded.address_text,note=excluded.note,status='ACTIVE',is_default=false,updated_at=now();

insert into merchant_profiles(id,name,slug,description,merchant_status,moderation_status,created_at,updated_at,version)
values('$merchantId','$merchantName','ui-cho-xanh','$merchantDescription','ACTIVE','APPROVED','$fixtureDirectoryTimestamp','$fixtureDirectoryTimestamp',0)
on conflict(id) do update set name=excluded.name,description=excluded.description,merchant_status='ACTIVE',moderation_status='APPROVED',updated_at=excluded.updated_at;

-- Release the former temporary slug before assigning the canonical PUB-009
-- service and empty merchants on repeat seeds.
update merchant_profiles
set slug='ui-public-service', updated_at='$fixtureDirectoryTimestamp'
where id='$publicServiceMerchantId';

insert into merchant_profiles(id,name,slug,description,merchant_status,moderation_status,created_at,updated_at,version)
values
('$publicServiceMerchantId','$publicServiceMerchantName','ui-public-service','$publicServiceMerchantDescription','ACTIVE','APPROVED','$fixtureDirectoryTimestamp','$fixtureDirectoryTimestamp',0),
('$emptyNoticeMerchantId','$publicEmptyMerchantName','ui-empty-notices','$publicEmptyMerchantDescription','ACTIVE','APPROVED','$fixtureDirectoryTimestamp','$fixtureDirectoryTimestamp',0),
('$publicMixedMerchantId','$publicMixedMerchantName','ui-public-mixed','$publicMixedMerchantDescription','ACTIVE','APPROVED','$fixtureDirectoryTimestamp','$fixtureDirectoryTimestamp',0)
on conflict(id) do update set name=excluded.name,slug=excluded.slug,description=excluded.description,merchant_status='ACTIVE',moderation_status='APPROVED',updated_at=excluded.updated_at;

insert into merchant_memberships(id,merchant_profile_id,user_id,role,membership_status,joined_at,created_at,version)
values('33333333-3333-4333-8333-444444444444','$merchantId','$sellerId','OWNER','ACTIVE',now(),now(),0)
on conflict(id) do update set membership_status='ACTIVE';

insert into merchant_memberships(id,merchant_profile_id,user_id,role,membership_status,joined_at,created_at,version)
values('33333333-3333-4333-8333-444444444445','$emptyNoticeMerchantId','$sellerId','OWNER','ACTIVE',now(),now(),0)
on conflict(id) do update set merchant_profile_id=excluded.merchant_profile_id,membership_status='ACTIVE';

insert into merchant_contact_methods(id,merchant_profile_id,merchant_location_id,contact_type,value,qr_media_id,is_primary,is_public,status,created_at,version) values
('33333333-3333-4333-8333-555555555551','$merchantId',null,'ZALO_LINK','https://zalo.me/0909123456',null,true,true,'ACTIVE',now(),0),
('33333333-3333-4333-8333-555555555552','$merchantId',null,'MESSENGER_LINK','https://m.me/covie.ui.fixture',null,false,true,'ACTIVE',now(),0),
('33333333-3333-4333-8333-555555555553','$merchantId',null,'PHONE','0909 123 456',null,false,true,'ACTIVE',now(),0)
on conflict(id) do update set value=excluded.value,is_public=true,status='ACTIVE';

insert into merchant_locations(id,merchant_profile_id,name,address_text,geo_point,pin_source,timezone,location_status,pickup_enabled,seller_delivery_enabled,service_enabled,minimum_delivery_order,currency_code,created_at,updated_at,version)
values('$locationId','$merchantId','$locationName','S5.03, Vinhomes Grand Park',ST_SetSRID(ST_MakePoint(106.842,10.842),4326)::geography,'USER_ADJUSTED','Asia/Ho_Chi_Minh','ACTIVE',true,true,true,0,'VND',now(),now(),0)
on conflict(id) do update set name=excluded.name,address_text=excluded.address_text,location_status='ACTIVE',pickup_enabled=true,seller_delivery_enabled=true,service_enabled=true,updated_at=now();

insert into merchant_locations(id,merchant_profile_id,name,address_text,geo_point,pin_source,timezone,location_status,pickup_enabled,seller_delivery_enabled,service_enabled,minimum_delivery_order,currency_code,created_at,updated_at,version)
values
('$publicServiceLocationId','$publicServiceMerchantId','$publicServiceLocationName','S5.02, Vinhomes Grand Park',ST_SetSRID(ST_MakePoint(106.843,10.843),4326)::geography,'USER_ADJUSTED','Asia/Ho_Chi_Minh','ACTIVE',false,false,true,0,'VND',now(),now(),0),
('$publicMixedLocationId','$publicMixedMerchantId','$publicMixedLocationName','S3.01, Vinhomes Grand Park',ST_SetSRID(ST_MakePoint(106.844,10.844),4326)::geography,'USER_ADJUSTED','Asia/Ho_Chi_Minh','ACTIVE',true,true,true,0,'VND',now(),now(),0)
on conflict(id) do update set merchant_profile_id=excluded.merchant_profile_id,name=excluded.name,address_text=excluded.address_text,geo_point=excluded.geo_point,location_status='ACTIVE',pickup_enabled=excluded.pickup_enabled,seller_delivery_enabled=excluded.seller_delivery_enabled,service_enabled=excluded.service_enabled,updated_at=now();

insert into location_areas(id,parent_id,area_type,name,slug,path_cache,geo_boundary,status,sort_order,created_at,updated_at,version) values
('23232323-2323-4232-8232-232323232301',null,'COMMUNITY','$cityAreaName','ui-ho-chi-minh','/ui-ho-chi-minh',null,'ACTIVE',1,'$fixtureDirectoryTimestamp','$fixtureDirectoryTimestamp',0),
('23232323-2323-4232-8232-232323232302','23232323-2323-4232-8232-232323232301','ZONE','$districtAreaName','ui-thu-duc','/ui-ho-chi-minh/ui-thu-duc',null,'ACTIVE',1,'$fixtureDirectoryTimestamp','$fixtureDirectoryTimestamp',0),
('23232323-2323-4232-8232-232323232303','23232323-2323-4232-8232-232323232302','WARD','$wardAreaName','ui-long-binh','/ui-ho-chi-minh/ui-thu-duc/ui-long-binh',null,'ACTIVE',1,'$fixtureDirectoryTimestamp','$fixtureDirectoryTimestamp',0)
on conflict(id) do update set
    parent_id=excluded.parent_id,area_type=excluded.area_type,name=excluded.name,slug=excluded.slug,
    path_cache=excluded.path_cache,geo_boundary=null,status='ACTIVE',sort_order=excluded.sort_order,
    updated_at=excluded.updated_at,version=0;

insert into categories(id,name,slug,catalog_type,status,sort_order,version)
values
('$foodCategoryId','$foodCategoryName','ui-do-an','PRODUCT','ACTIVE',-400,0),
('$drinkCategoryId','$drinkCategoryName','ui-do-uong','PRODUCT','ACTIVE',-399,0),
('$categoryId','$categoryName','ui-tap-hoa','PRODUCT','ACTIVE',-398,0),
('$serviceCategoryId','$serviceCategoryName','ui-dich-vu','SERVICE','ACTIVE',-397,0)
on conflict(id) do update set name=excluded.name,slug=excluded.slug,status='ACTIVE',catalog_type=excluded.catalog_type,sort_order=excluded.sort_order;

insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,description,listing_status,moderation_status,search_vector,created_at,updated_at,version) values
('$productItemId','$merchantId','PRODUCT','STANDARD','$categoryId','$productName','ui-rau-cu-theo-mua','$productDescription','ACTIVE','APPROVED',to_tsvector('simple','rau cu theo mua'),now(),now(),0),
('$serviceItemId','$publicServiceMerchantId','SERVICE','STANDARD','$serviceCategoryId','$serviceName','ui-sua-khoa-tan-noi','$serviceDescription','ACTIVE','APPROVED',to_tsvector('simple','sua khoa tan noi'),now(),now(),0),
('$resaleItemId','$merchantId','PRODUCT','RESALE','$categoryId','$resaleName','ui-ke-go-thanh-ly','$resaleDescription','ACTIVE','APPROVED',to_tsvector('simple','ke go thanh ly'),now(),now(),0),
('$thirdProductItemId','$merchantId','PRODUCT','STANDARD','$categoryId','$thirdProductName','ui-trai-cay-dia-phuong','$thirdProductDescription','ACTIVE','APPROVED',to_tsvector('simple','trai cay dia phuong'),now(),now(),0),
('$fourthProductItemId','$merchantId','PRODUCT','STANDARD','$categoryId','$fourthProductName','ui-mat-ong-dia-phuong','$fourthProductDescription','ACTIVE','APPROVED',to_tsvector('simple','mat ong dia phuong'),now(),now(),0),
('$mixedProductItemId','$publicMixedMerchantId','PRODUCT','STANDARD','$categoryId','$mixedProductName','ui-gao-huu-co','$mixedProductDescription','ACTIVE','APPROVED',to_tsvector('simple','gao huu co'),now(),now(),0),
('$mixedServiceItemId','$publicMixedMerchantId','SERVICE','STANDARD','$serviceCategoryId','$mixedServiceName','ui-ve-sinh-nha-cua','$mixedServiceDescription','ACTIVE','APPROVED',to_tsvector('simple','ve sinh nha cua'),now(),now(),0)
on conflict(id) do update set merchant_profile_id=excluded.merchant_profile_id,item_type=excluded.item_type,listing_kind=excluded.listing_kind,primary_category_id=excluded.primary_category_id,name=excluded.name,description=excluded.description,listing_status='ACTIVE',moderation_status='APPROVED',updated_at=now();

insert into variants(id,catalog_item_id,variant_name,is_default,variant_status,sort_order,created_at,updated_at,version) values
('$productVariantId','$productItemId','$productVariantName',true,'ACTIVE',0,now(),now(),0),
('$serviceVariantOneId','$serviceItemId','$serviceVariantOneName',false,'ACTIVE',0,now(),now(),0),
('$serviceVariantTwoId','$serviceItemId','$serviceVariantTwoName',true,'ACTIVE',1,now(),now(),0),
('$serviceVariantThreeId','$serviceItemId','$serviceVariantThreeName',false,'ACTIVE',2,now(),now(),0),
('$resaleVariantId','$resaleItemId','$resaleVariantName',true,'ACTIVE',0,now(),now(),0),
('$thirdProductVariantId','$thirdProductItemId','$defaultVariantName',true,'ACTIVE',0,now(),now(),0),
('$fourthProductVariantId','$fourthProductItemId','$defaultVariantName',true,'ACTIVE',0,now(),now(),0),
('$mixedProductVariantId','$mixedProductItemId','$defaultVariantName',true,'ACTIVE',0,now(),now(),0),
('$mixedServiceVariantId','$mixedServiceItemId','$standardServicePackageName',true,'ACTIVE',0,now(),now(),0)
on conflict(id) do update set variant_name=excluded.variant_name,is_default=excluded.is_default,variant_status='ACTIVE',sort_order=excluded.sort_order,updated_at=now();

insert into location_variants(id,merchant_location_id,variant_id,price,currency_code,is_available,inventory_mode,preparation_time_minutes,created_at,updated_at,version) values
('$productOfferId','$locationId','$productVariantId',85000,'VND',true,'TRACKED',30,now(),now(),0),
('$serviceOfferOneId','$publicServiceLocationId','$serviceVariantOneId',150000,'VND',true,null,60,now(),now(),0),
('$serviceOfferTwoId','$publicServiceLocationId','$serviceVariantTwoId',280000,'VND',true,null,60,now(),now(),0),
('$serviceOfferThreeId','$publicServiceLocationId','$serviceVariantThreeId',400000,'VND',true,null,60,now(),now(),0),
('$resaleOfferId','$locationId','$resaleVariantId',320000,'VND',true,'TRACKED',15,now(),now(),0),
('$thirdProductOfferId','$locationId','$thirdProductVariantId',120000,'VND',true,'UNTRACKED',20,now(),now(),0),
('$fourthProductOfferId','$locationId','$fourthProductVariantId',135000,'VND',true,'UNTRACKED',20,now(),now(),0),
('$mixedProductOfferId','$publicMixedLocationId','$mixedProductVariantId',95000,'VND',true,'UNTRACKED',20,now(),now(),0),
('$mixedServiceOfferId','$publicMixedLocationId','$mixedServiceVariantId',180000,'VND',true,null,90,now(),now(),0)
on conflict(id) do update set merchant_location_id=excluded.merchant_location_id,price=excluded.price,is_available=true,inventory_mode=excluded.inventory_mode,preparation_time_minutes=excluded.preparation_time_minutes,updated_at=now();

insert into inventory_balances(location_variant_id,stock_on_hand,reserved_quantity,updated_at,version) values
('$productOfferId',50,2,now(),0),('$resaleOfferId',1,0,now(),0)
on conflict(location_variant_id) do update set stock_on_hand=excluded.stock_on_hand,reserved_quantity=excluded.reserved_quantity,updated_at=now();

insert into delivery_coverages(id,merchant_location_id,coverage_type,radius_enabled,radius_meters,area_enabled,coverage_status,created_at,updated_at,version) values
('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1','$locationId','PRODUCT_DELIVERY',true,10000,false,'ACTIVE',now(),now(),0),
('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2','$locationId','SERVICE_AREA',true,10000,false,'ACTIVE',now(),now(),0),
('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa3','$publicServiceLocationId','SERVICE_AREA',true,10000,false,'ACTIVE',now(),now(),0),
('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa4','$publicMixedLocationId','PRODUCT_DELIVERY',true,10000,false,'ACTIVE',now(),now(),0),
('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa5','$publicMixedLocationId','SERVICE_AREA',true,10000,false,'ACTIVE',now(),now(),0)
on conflict(id) do update set merchant_location_id=excluded.merchant_location_id,coverage_type=excluded.coverage_type,coverage_status='ACTIVE',radius_enabled=true,radius_meters=10000,updated_at=now();

insert into shipping_policies(id,merchant_location_id,mode,base_fee,currency_code,policy_status,created_at,updated_at,version)
values('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','$locationId','FIXED',25000,'VND','ACTIVE',now(),now(),0)
on conflict(id) do update set mode='FIXED',base_fee=25000,policy_status='ACTIVE',updated_at=now();

insert into merchant_reputation_stats(merchant_profile_id,eligible_order_count,completed_order_count,seller_cancel_after_confirm_count,excluded_issue_count,completion_rate_basis_points,updated_at)
values('$merchantId',125,120,5,0,9600,now())
on conflict(merchant_profile_id) do update set eligible_order_count=125,completed_order_count=120,seller_cancel_after_confirm_count=5,excluded_issue_count=0,completion_rate_basis_points=9600,updated_at=now();

insert into user_reputation_stats(user_id,eligible_order_count,completed_order_count,buyer_no_show_count,buyer_cancel_after_confirm_count,excluded_issue_count,completion_rate_basis_points,updated_at)
values('$buyerId',25,24,0,1,0,9600,now())
on conflict(user_id) do update set eligible_order_count=25,completed_order_count=24,buyer_no_show_count=0,buyer_cancel_after_confirm_count=1,excluded_issue_count=0,completion_rate_basis_points=9600,updated_at=now();

delete from user_reputation_stats where user_id='$zeroReputationBuyerId';
insert into user_reputation_stats(user_id,eligible_order_count,completed_order_count,buyer_no_show_count,buyer_cancel_after_confirm_count,excluded_issue_count,completion_rate_basis_points,updated_at)
values('$warningReputationBuyerId',5,3,0,2,0,6000,now())
on conflict(user_id) do update set eligible_order_count=5,completed_order_count=3,buyer_no_show_count=0,buyer_cancel_after_confirm_count=2,excluded_issue_count=0,completion_rate_basis_points=6000,updated_at=now();

insert into merchant_rating_stats(merchant_profile_id,review_count,rating_sum,average_rating,updated_at)
values('$merchantId',96,461,4.80,now())
on conflict(merchant_profile_id) do update set review_count=96,rating_sum=461,average_rating=4.80,updated_at=now();

insert into notifications(
    id,user_id,notification_type,title,body,related_entity_type,related_entity_id,payload,
    source_outbox_event_id,read_at,created_at,expires_at
) values(
    '18181818-1818-4181-8181-181818181221','$sellerId','MODERATION_UPDATED',
    'Moderation updated','Merchant moderation state changed.','MERCHANT','$merchantId',
    jsonb_build_object('targetType','MERCHANT','targetId','$merchantId'),null,null,
    '2026-09-16T06:00:00Z',null
)
on conflict(id) do update set
    user_id=excluded.user_id,notification_type=excluded.notification_type,title=excluded.title,body=excluded.body,
    related_entity_type=excluded.related_entity_type,related_entity_id=excluded.related_entity_id,
    payload=excluded.payload,source_outbox_event_id=null,read_at=null,created_at=excluded.created_at,expires_at=null;

-- Return the three deterministic Seller Need journeys to their initial state.
delete from need_response_items
where need_response_id in (
    select id from need_responses
    where need_request_id in ('$candidateNeedId','$respondedNeedId','$emptyCandidateNeedId')
);
delete from need_responses
where need_request_id in ('$candidateNeedId','$respondedNeedId','$emptyCandidateNeedId');

insert into need_requests(
    id,buyer_user_id,need_type,category_id,title,query_text,description,requested_quantity,
    location_area_id,geo_point,location_snapshot,search_radius_meters,request_status,
    expires_at,fulfilled_order_id,created_at,updated_at,closed_at,moderation_status
) values
('$candidateNeedId','$buyerId','PRODUCT','$categoryId','$candidateNeedTitle','rau cu','Mua rau cu theo mua',2,null,ST_SetSRID(ST_MakePoint(106.842,10.842),4326)::geography,jsonb_build_object('label','$needAreaLabel'),10000,'ACTIVE','2099-01-01T00:00:00Z',null,'2026-09-16T05:00:00Z','2026-09-16T05:00:00Z',null,'APPROVED'),
('$respondedNeedId','$buyerId','PRODUCT','$categoryId','$respondedNeedTitle','ke go','Tim ke go con tot',1,null,ST_SetSRID(ST_MakePoint(106.842,10.842),4326)::geography,jsonb_build_object('label','$needAreaLabel'),10000,'ACTIVE','2099-01-01T00:00:00Z',null,'2026-09-16T05:05:00Z','2026-09-16T05:05:00Z',null,'APPROVED'),
('$emptyCandidateNeedId','$buyerId','PRODUCT','$categoryId','$emptyCandidateNeedTitle','mat ong','Can mat ong dia phuong',1,null,ST_SetSRID(ST_MakePoint(106.842,10.842),4326)::geography,jsonb_build_object('label','$needAreaLabel'),10000,'ACTIVE','2099-01-01T00:00:00Z',null,'2026-09-16T05:10:00Z','2026-09-16T05:10:00Z',null,'APPROVED')
on conflict(id) do update set
    buyer_user_id=excluded.buyer_user_id,need_type=excluded.need_type,category_id=excluded.category_id,
    title=excluded.title,query_text=excluded.query_text,description=excluded.description,
    requested_quantity=excluded.requested_quantity,location_area_id=null,geo_point=excluded.geo_point,
    location_snapshot=excluded.location_snapshot,search_radius_meters=excluded.search_radius_meters,
    request_status='ACTIVE',expires_at=excluded.expires_at,fulfilled_order_id=null,
    created_at=excluded.created_at,updated_at=excluded.updated_at,closed_at=null,moderation_status='APPROVED';

insert into need_responses(
    id,need_request_id,merchant_profile_id,merchant_location_id,response_status,note,
    created_by_user_id,created_at,updated_at,withdrawn_at,version
) values
('$respondedNeedResponseId','$respondedNeedId','$merchantId','$locationId','ACTIVE','Da gui ke go phu hop','$sellerId','2026-09-16T05:15:00Z','2026-09-16T05:15:00Z',null,0);

insert into need_response_items(
    id,need_response_id,catalog_item_id,variant_id,location_variant_id,item_status,
    price_snapshot,currency_code,availability_snapshot,created_at,retired_at
) values
('17171717-1717-4171-8171-171717171222','$respondedNeedResponseId','$resaleItemId','$resaleVariantId','$resaleOfferId','ACTIVE',320000,'VND',jsonb_build_object('available',true,'availableQuantity',1),'2026-09-16T05:15:00Z',null);

-- Reset only mutable lifecycle rows owned by the disposable acceptance
-- orders. This keeps repeated runs deterministic without touching other data.
delete from reviews where order_item_id in ('$reviewEligibleOrderItemId','$alreadyReviewedOrderItemId','$reviewDisabledOrderItemId');
delete from order_cancellation_requests where order_id in ('$pendingProductOrderId','$deliveryProductOrderId','$pickupProductOrderId','$serviceOrderId','$reviewEligibleOrderId','$alreadyReviewedOrderId','$reviewDisabledOrderId');
delete from order_issues where order_id in ('$pendingProductOrderId','$deliveryProductOrderId','$pickupProductOrderId','$serviceOrderId','$reviewEligibleOrderId','$alreadyReviewedOrderId','$reviewDisabledOrderId');
delete from order_events where order_id in ('$pendingProductOrderId','$deliveryProductOrderId','$pickupProductOrderId','$serviceOrderId','$reviewEligibleOrderId','$alreadyReviewedOrderId','$reviewDisabledOrderId');

insert into orders(
    id,order_code,order_type,buyer_user_id,merchant_profile_id,merchant_location_id,status,source_type,
    payment_method,subtotal,shipping_fee_type,shipping_fee,known_total,total_amount,currency_code,
    confirmation_expires_at,confirmed_at,completed_at,closed_at,created_at,updated_at,version
) values
('$pendingProductOrderId','UI-ORD-1221','PRODUCT','$buyerId','$merchantId','$locationId','PENDING_CONFIRMATION','MARKETPLACE','COD',170000,'FIXED',25000,195000,195000,'VND','2099-01-01T00:00:00Z',null,null,null,'2026-09-16T01:00:00Z','2026-09-16T01:00:00Z',0),
('$deliveryProductOrderId','UI-ORD-1222','PRODUCT','$buyerId','$merchantId','$locationId','CONFIRMED','MARKETPLACE','COD',85000,'FIXED',25000,110000,110000,'VND',null,'2026-09-16T02:05:00Z',null,null,'2026-09-16T02:00:00Z','2026-09-16T02:05:00Z',1),
('$pickupProductOrderId','UI-ORD-1223','PRODUCT','$buyerId','$merchantId','$locationId','CONFIRMED','MARKETPLACE','COD',320000,'NOT_APPLICABLE',null,320000,320000,'VND',null,'2026-09-16T03:05:00Z',null,null,'2026-09-16T03:00:00Z','2026-09-16T03:05:00Z',1),
('$serviceOrderId','UI-ORD-1224','SERVICE','$buyerId','$merchantId','$locationId','CONFIRMED','MARKETPLACE','COD',280000,'NOT_APPLICABLE',null,280000,280000,'VND',null,'2026-09-16T04:05:00Z',null,null,'2026-09-16T04:00:00Z','2026-09-16T04:05:00Z',1),
('$reviewEligibleOrderId','UI-ORD-1211','PRODUCT','$buyerId','$merchantId','$locationId','COMPLETED','MARKETPLACE','COD',85000,'FREE',0,85000,85000,'VND',null,'2026-09-15T01:05:00Z','2026-09-15T02:00:00Z','2026-09-15T02:00:00Z','2026-09-15T01:00:00Z','2026-09-15T02:00:00Z',3),
('$alreadyReviewedOrderId','UI-ORD-1212','PRODUCT','$buyerId','$merchantId','$locationId','COMPLETED','MARKETPLACE','COD',85000,'FREE',0,85000,85000,'VND',null,'2026-09-15T03:05:00Z','2026-09-15T04:00:00Z','2026-09-15T04:00:00Z','2026-09-15T03:00:00Z','2026-09-15T04:00:00Z',3),
('$reviewDisabledOrderId','UI-ORD-1213','PRODUCT','$buyerId','$merchantId','$locationId','COMPLETED','MARKETPLACE','COD',85000,'FREE',0,85000,85000,'VND',null,'2026-09-15T05:05:00Z','2026-09-15T06:00:00Z','2026-09-15T06:00:00Z','2026-09-15T05:00:00Z','2026-09-15T06:00:00Z',3)
on conflict(id) do update set
    order_code=excluded.order_code,order_type=excluded.order_type,buyer_user_id=excluded.buyer_user_id,
    merchant_profile_id=excluded.merchant_profile_id,merchant_location_id=excluded.merchant_location_id,
    status=excluded.status,source_type=excluded.source_type,payment_method=excluded.payment_method,
    subtotal=excluded.subtotal,shipping_fee_type=excluded.shipping_fee_type,shipping_fee=excluded.shipping_fee,
    known_total=excluded.known_total,total_amount=excluded.total_amount,currency_code=excluded.currency_code,
    confirmation_expires_at=excluded.confirmation_expires_at,confirmed_at=excluded.confirmed_at,
    completed_at=excluded.completed_at,closed_at=excluded.closed_at,created_at=excluded.created_at,updated_at=excluded.updated_at,version=excluded.version;

insert into order_items(
    id,order_id,catalog_item_id,variant_id,location_variant_id,item_type,quantity,unit_price,line_total,
    currency_code,item_snapshot,created_at
) values
('13131313-1313-4131-8131-131313131221','$pendingProductOrderId','$productItemId','$productVariantId','$productOfferId','PRODUCT',2,85000,170000,'VND',jsonb_build_object('schemaVersion',1,'catalogItemId','$productItemId','name','$productName','itemType','PRODUCT','listingKind','STANDARD','catalogItemVersion',0,'variantId','$productVariantId','variantName','$productVariantName','variantVersion',0,'locationVariantId','$productOfferId','unitPrice',85000,'currencyCode','VND','preparationTimeMinutes',30,'locationVariantVersion',0),'2026-09-16T01:00:00Z'),
('13131313-1313-4131-8131-131313131222','$deliveryProductOrderId','$productItemId','$productVariantId','$productOfferId','PRODUCT',1,85000,85000,'VND',jsonb_build_object('schemaVersion',1,'catalogItemId','$productItemId','name','$productName','itemType','PRODUCT','listingKind','STANDARD','catalogItemVersion',0,'variantId','$productVariantId','variantName','$productVariantName','variantVersion',0,'locationVariantId','$productOfferId','unitPrice',85000,'currencyCode','VND','preparationTimeMinutes',30,'locationVariantVersion',0),'2026-09-16T02:00:00Z'),
('13131313-1313-4131-8131-131313131223','$pickupProductOrderId','$resaleItemId','$resaleVariantId','$resaleOfferId','PRODUCT',1,320000,320000,'VND',jsonb_build_object('schemaVersion',1,'catalogItemId','$resaleItemId','name','$resaleName','itemType','PRODUCT','listingKind','RESALE','catalogItemVersion',0,'variantId','$resaleVariantId','variantName','$resaleVariantName','variantVersion',0,'locationVariantId','$resaleOfferId','unitPrice',320000,'currencyCode','VND','preparationTimeMinutes',15,'locationVariantVersion',0),'2026-09-16T03:00:00Z'),
('13131313-1313-4131-8131-131313131224','$serviceOrderId','$serviceItemId','$serviceVariantTwoId','$serviceOfferTwoId','SERVICE',1,280000,280000,'VND',jsonb_build_object('schemaVersion',1,'catalogItemId','$serviceItemId','name','$serviceName','itemType','SERVICE','listingKind','STANDARD','catalogItemVersion',0,'variantId','$serviceVariantTwoId','variantName','$serviceVariantTwoName','variantVersion',0,'locationVariantId','$serviceOfferTwoId','unitPrice',280000,'currencyCode','VND','preparationTimeMinutes',60,'locationVariantVersion',0),'2026-09-16T04:00:00Z'),
('$reviewEligibleOrderItemId','$reviewEligibleOrderId','$productItemId','$productVariantId','$productOfferId','PRODUCT',1,85000,85000,'VND',jsonb_build_object('schemaVersion',1,'catalogItemId','$productItemId','name','$productName','itemType','PRODUCT','listingKind','STANDARD','catalogItemVersion',0,'variantId','$productVariantId','variantName','$productVariantName','variantVersion',0,'locationVariantId','$productOfferId','unitPrice',85000,'currencyCode','VND','preparationTimeMinutes',30,'locationVariantVersion',0),'2026-09-15T01:00:00Z'),
('$alreadyReviewedOrderItemId','$alreadyReviewedOrderId','$productItemId','$productVariantId','$productOfferId','PRODUCT',1,85000,85000,'VND',jsonb_build_object('schemaVersion',1,'catalogItemId','$productItemId','name','$productName','itemType','PRODUCT','listingKind','STANDARD','catalogItemVersion',0,'variantId','$productVariantId','variantName','$productVariantName','variantVersion',0,'locationVariantId','$productOfferId','unitPrice',85000,'currencyCode','VND','preparationTimeMinutes',30,'locationVariantVersion',0),'2026-09-15T03:00:00Z'),
('$reviewDisabledOrderItemId','$reviewDisabledOrderId','$productItemId','$productVariantId','$productOfferId','PRODUCT',1,85000,85000,'VND',jsonb_build_object('schemaVersion',1,'catalogItemId','$productItemId','name','$productName','itemType','PRODUCT','listingKind','STANDARD','catalogItemVersion',0,'variantId','$productVariantId','variantName','$productVariantName','variantVersion',0,'locationVariantId','$productOfferId','unitPrice',85000,'currencyCode','VND','preparationTimeMinutes',30,'locationVariantVersion',0),'2026-09-15T05:00:00Z')
on conflict(id) do update set
    order_id=excluded.order_id,catalog_item_id=excluded.catalog_item_id,variant_id=excluded.variant_id,
    location_variant_id=excluded.location_variant_id,item_type=excluded.item_type,quantity=excluded.quantity,
    unit_price=excluded.unit_price,line_total=excluded.line_total,currency_code=excluded.currency_code,
    item_snapshot=excluded.item_snapshot,created_at=excluded.created_at;

insert into product_fulfillments(
    order_id,fulfillment_method,pickup_location_id,buyer_address_id,distance_meters,pickup_location_snapshot,
    delivery_address_snapshot,buyer_contact_snapshot,merchant_contact_snapshot,shipping_policy_snapshot,
    seller_marked_ready_at,seller_marked_delivered_at,awaiting_buyer_confirmation_at,out_for_delivery_at,
    auto_complete_at,no_show_eligible_at
) values
('$pendingProductOrderId','SELLER_DELIVERY','$locationId','$addressId',0,jsonb_build_object('schemaVersion',1,'id','$locationId','merchantProfileId','$merchantId','name','$locationName','addressText','S5.03, Vinhomes Grand Park','latitude',10.842,'longitude',106.842,'timezone','Asia/Ho_Chi_Minh','currencyCode','VND','version',0),jsonb_build_object('schemaVersion',1,'id','$addressId','label','$addressLabel','recipientName','$recipientName','phone','0909 123 456','addressText','$homeAddressText','latitude',10.842,'longitude',106.842,'pinSource','USER_ADJUSTED','version',0),jsonb_build_object('schemaVersion',1,'recipientName','$recipientName','phone','0909 123 456'),jsonb_build_object('schemaVersion',1,'contacts',jsonb_build_array(jsonb_build_object('type','PHONE','value','0909 123 456','isPrimary',true))),jsonb_build_object('schemaVersion',1,'mode','FIXED','baseFee',25000,'currencyCode','VND','effectiveFee',25000),null,null,null,null,null,null),
('$deliveryProductOrderId','SELLER_DELIVERY','$locationId','$addressId',4200,jsonb_build_object('schemaVersion',1,'id','$locationId','merchantProfileId','$merchantId','name','$locationName','addressText','S5.03, Vinhomes Grand Park','latitude',10.842,'longitude',106.842,'timezone','Asia/Ho_Chi_Minh','currencyCode','VND','version',0),jsonb_build_object('schemaVersion',1,'id','$addressId','label','$addressLabel','recipientName','$recipientName','phone','0909 123 456','addressText','$homeAddressText','latitude',10.842,'longitude',106.842,'pinSource','USER_ADJUSTED','version',0),jsonb_build_object('schemaVersion',1,'recipientName','$recipientName','phone','0909 123 456'),jsonb_build_object('schemaVersion',1,'contacts',jsonb_build_array(jsonb_build_object('type','PHONE','value','0909 123 456','isPrimary',true))),jsonb_build_object('schemaVersion',1,'mode','FIXED','baseFee',25000,'currencyCode','VND','effectiveFee',25000),null,null,null,null,null,null),
('$pickupProductOrderId','PICKUP','$locationId',null,null,jsonb_build_object('schemaVersion',1,'id','$locationId','merchantProfileId','$merchantId','name','$locationName','addressText','S5.03, Vinhomes Grand Park','latitude',10.842,'longitude',106.842,'timezone','Asia/Ho_Chi_Minh','currencyCode','VND','version',0),null,jsonb_build_object('schemaVersion',1,'recipientName','$recipientName','phone','0909 123 456'),jsonb_build_object('schemaVersion',1,'contacts',jsonb_build_array(jsonb_build_object('type','PHONE','value','0909 123 456','isPrimary',true))),null,null,null,null,null,null,null),
('$reviewEligibleOrderId','PICKUP','$locationId',null,null,jsonb_build_object('schemaVersion',1,'id','$locationId','merchantProfileId','$merchantId','name','$locationName','addressText','S5.03, Vinhomes Grand Park','latitude',10.842,'longitude',106.842,'timezone','Asia/Ho_Chi_Minh','currencyCode','VND','version',0),null,jsonb_build_object('schemaVersion',1,'recipientName','$recipientName','phone','0909 123 456'),jsonb_build_object('schemaVersion',1,'contacts',jsonb_build_array(jsonb_build_object('type','PHONE','value','0909 123 456','isPrimary',true))),null,'2026-09-15T01:30:00Z',null,null,null,null,null),
('$alreadyReviewedOrderId','PICKUP','$locationId',null,null,jsonb_build_object('schemaVersion',1,'id','$locationId','merchantProfileId','$merchantId','name','$locationName','addressText','S5.03, Vinhomes Grand Park','latitude',10.842,'longitude',106.842,'timezone','Asia/Ho_Chi_Minh','currencyCode','VND','version',0),null,jsonb_build_object('schemaVersion',1,'recipientName','$recipientName','phone','0909 123 456'),jsonb_build_object('schemaVersion',1,'contacts',jsonb_build_array(jsonb_build_object('type','PHONE','value','0909 123 456','isPrimary',true))),null,'2026-09-15T03:30:00Z',null,null,null,null,null),
('$reviewDisabledOrderId','PICKUP','$locationId',null,null,jsonb_build_object('schemaVersion',1,'id','$locationId','merchantProfileId','$merchantId','name','$locationName','addressText','S5.03, Vinhomes Grand Park','latitude',10.842,'longitude',106.842,'timezone','Asia/Ho_Chi_Minh','currencyCode','VND','version',0),null,jsonb_build_object('schemaVersion',1,'recipientName','$recipientName','phone','0909 123 456'),jsonb_build_object('schemaVersion',1,'contacts',jsonb_build_array(jsonb_build_object('type','PHONE','value','0909 123 456','isPrimary',true))),null,'2026-09-15T05:30:00Z',null,null,null,null,null)
on conflict(order_id) do update set
    fulfillment_method=excluded.fulfillment_method,pickup_location_id=excluded.pickup_location_id,
    buyer_address_id=excluded.buyer_address_id,distance_meters=excluded.distance_meters,
    pickup_location_snapshot=excluded.pickup_location_snapshot,delivery_address_snapshot=excluded.delivery_address_snapshot,
    buyer_contact_snapshot=excluded.buyer_contact_snapshot,merchant_contact_snapshot=excluded.merchant_contact_snapshot,
    shipping_policy_snapshot=excluded.shipping_policy_snapshot,seller_marked_ready_at=null,seller_marked_delivered_at=null,
    awaiting_buyer_confirmation_at=null,out_for_delivery_at=null,auto_complete_at=null,no_show_eligible_at=null;

insert into service_fulfillments(
    order_id,service_address_id,distance_meters,service_address_snapshot,provider_location_snapshot,
    buyer_contact_snapshot,merchant_contact_snapshot,created_at,provider_marked_performed_at,
    awaiting_buyer_confirmation_at,auto_complete_at
) values
('$serviceOrderId','$addressId',4200,jsonb_build_object('schemaVersion',1,'id','$addressId','label','$addressLabel','recipientName','$recipientName','phone','0909 123 456','addressText','$homeAddressText','latitude',10.842,'longitude',106.842,'pinSource','USER_ADJUSTED','version',0),jsonb_build_object('schemaVersion',1,'id','$locationId','merchantProfileId','$merchantId','name','$locationName','addressText','S5.03, Vinhomes Grand Park','latitude',10.842,'longitude',106.842,'timezone','Asia/Ho_Chi_Minh','currencyCode','VND','version',0),jsonb_build_object('schemaVersion',1,'recipientName','$recipientName','phone','0909 123 456'),jsonb_build_object('schemaVersion',1,'contacts',jsonb_build_array(jsonb_build_object('type','PHONE','value','0909 123 456','isPrimary',true))),'2026-09-16T04:00:00Z',null,null,null)
on conflict(order_id) do update set
    service_address_id=excluded.service_address_id,distance_meters=excluded.distance_meters,
    service_address_snapshot=excluded.service_address_snapshot,provider_location_snapshot=excluded.provider_location_snapshot,
    buyer_contact_snapshot=excluded.buyer_contact_snapshot,merchant_contact_snapshot=excluded.merchant_contact_snapshot,
    created_at=excluded.created_at,provider_marked_performed_at=null,awaiting_buyer_confirmation_at=null,auto_complete_at=null;

insert into order_events(id,order_id,event_type,actor_user_id,actor_context,from_status,to_status,metadata,created_at) values
('14141414-1414-4141-8141-141414141221','$pendingProductOrderId','ORDER_CREATED','$buyerId','BUYER',null,'PENDING_CONFIRMATION','{}'::jsonb,'2026-09-16T01:00:00Z'),
('14141414-1414-4141-8141-141414141222','$deliveryProductOrderId','ORDER_CREATED','$buyerId','BUYER',null,'PENDING_CONFIRMATION','{}'::jsonb,'2026-09-16T02:00:00Z'),
('14141414-1414-4141-8141-141414141223','$deliveryProductOrderId','SELLER_CONFIRMED','$sellerId','MERCHANT','PENDING_CONFIRMATION','CONFIRMED','{}'::jsonb,'2026-09-16T02:05:00Z'),
('14141414-1414-4141-8141-141414141224','$pickupProductOrderId','ORDER_CREATED','$buyerId','BUYER',null,'PENDING_CONFIRMATION','{}'::jsonb,'2026-09-16T03:00:00Z'),
('14141414-1414-4141-8141-141414141225','$pickupProductOrderId','SELLER_CONFIRMED','$sellerId','MERCHANT','PENDING_CONFIRMATION','CONFIRMED','{}'::jsonb,'2026-09-16T03:05:00Z'),
('14141414-1414-4141-8141-141414141226','$serviceOrderId','ORDER_CREATED','$buyerId','BUYER',null,'PENDING_CONFIRMATION','{}'::jsonb,'2026-09-16T04:00:00Z'),
('14141414-1414-4141-8141-141414141227','$serviceOrderId','SELLER_CONFIRMED','$sellerId','MERCHANT','PENDING_CONFIRMATION','CONFIRMED','{}'::jsonb,'2026-09-16T04:05:00Z'),
('14141414-1414-4141-8141-141414141211','$reviewEligibleOrderId','ORDER_CREATED','$buyerId','BUYER',null,'PENDING_CONFIRMATION','{}'::jsonb,'2026-09-15T01:00:00Z'),
('14141414-1414-4141-8141-141414141212','$reviewEligibleOrderId','SELLER_CONFIRMED','$sellerId','MERCHANT','PENDING_CONFIRMATION','CONFIRMED','{}'::jsonb,'2026-09-15T01:05:00Z'),
('14141414-1414-4141-8141-141414141213','$reviewEligibleOrderId','BUYER_CONFIRMED_RECEIVED','$buyerId','BUYER','CONFIRMED','COMPLETED','{}'::jsonb,'2026-09-15T02:00:00Z'),
('14141414-1414-4141-8141-141414141214','$alreadyReviewedOrderId','ORDER_CREATED','$buyerId','BUYER',null,'PENDING_CONFIRMATION','{}'::jsonb,'2026-09-15T03:00:00Z'),
('14141414-1414-4141-8141-141414141215','$alreadyReviewedOrderId','SELLER_CONFIRMED','$sellerId','MERCHANT','PENDING_CONFIRMATION','CONFIRMED','{}'::jsonb,'2026-09-15T03:05:00Z'),
('14141414-1414-4141-8141-141414141216','$alreadyReviewedOrderId','BUYER_CONFIRMED_RECEIVED','$buyerId','BUYER','CONFIRMED','COMPLETED','{}'::jsonb,'2026-09-15T04:00:00Z'),
('14141414-1414-4141-8141-141414141217','$reviewDisabledOrderId','ORDER_CREATED','$buyerId','BUYER',null,'PENDING_CONFIRMATION','{}'::jsonb,'2026-09-15T05:00:00Z'),
('14141414-1414-4141-8141-141414141218','$reviewDisabledOrderId','SELLER_CONFIRMED','$sellerId','MERCHANT','PENDING_CONFIRMATION','CONFIRMED','{}'::jsonb,'2026-09-15T05:05:00Z'),
('14141414-1414-4141-8141-141414141219','$reviewDisabledOrderId','BUYER_CONFIRMED_RECEIVED','$buyerId','BUYER','CONFIRMED','COMPLETED','{}'::jsonb,'2026-09-15T06:00:00Z');

insert into reviews(id,order_item_id,order_id,buyer_user_id,catalog_item_id,variant_id,merchant_profile_id,rating,comment,review_status,created_at,updated_at,deleted_at)
values('19191919-1919-4191-8191-191919191212','$alreadyReviewedOrderItemId','$alreadyReviewedOrderId','$buyerId','$productItemId','$productVariantId','$merchantId',5,'San pham dung mo ta.','ACTIVE','2026-09-15T04:05:00Z','2026-09-15T04:05:00Z',null);

insert into moderation_cases(
    id,case_type,reporter_user_id,target_type,target_user_id,target_merchant_profile_id,
    target_catalog_item_id,target_need_request_id,target_review_id,reason_code,description,
    case_status,assigned_admin_user_id,opened_at,resolved_at,closed_at
) values
('20510000-0000-4000-8000-000000000001','USER_REPORT','$buyerId','CATALOG_ITEM',null,null,'$productItemId',null,null,'SPAM_OR_MISLEADING','Deterministic catalog report for Admin visual acceptance.','OPEN',null,'2026-09-16T06:00:00Z',null,null),
('20500000-0000-4000-8000-000000000002','USER_REPORT','$buyerId','MERCHANT',null,'$merchantId',null,null,null,'POLICY_VIOLATION','Deterministic merchant report for Admin visual acceptance.','OPEN',null,'2026-09-16T05:00:00Z',null,null)
on conflict(id) do update set
    case_type=excluded.case_type,reporter_user_id=excluded.reporter_user_id,target_type=excluded.target_type,
    target_user_id=excluded.target_user_id,target_merchant_profile_id=excluded.target_merchant_profile_id,
    target_catalog_item_id=excluded.target_catalog_item_id,target_need_request_id=excluded.target_need_request_id,
    target_review_id=excluded.target_review_id,reason_code=excluded.reason_code,description=excluded.description,
    case_status=excluded.case_status,assigned_admin_user_id=null,opened_at=excluded.opened_at,resolved_at=null,closed_at=null;

insert into audit_logs(
    id,actor_user_id,actor_type,event_category,action,entity_type,entity_id,
    before_snapshot,after_snapshot,request_id,ip_hash_or_metadata,created_at
) values
('17171717-1717-4171-8171-171717171701','$adminId','USER','ADMIN','SYSTEM_SETTING_UPDATED','UI_VISUAL_ACCEPTANCE','20202020-2020-4202-8202-202020202023',null,'{"targetType":"SYSTEM_SETTING","previous":5,"current":3}'::jsonb,'ui-admin-017-01',null,'2026-09-16T07:04:00Z'),
('17171717-1717-4171-8171-171717171702','$adminId','USER','ADMIN','MODERATION_CASE_HIDDEN','UI_VISUAL_ACCEPTANCE','20510000-0000-4000-8000-000000000001',null,'{"targetType":"MODERATION_CASE","reason":"POLICY_VIOLATION"}'::jsonb,'ui-admin-017-02',null,'2026-09-16T07:03:00Z'),
('17171717-1717-4171-8171-171717171703','$adminId','USER','ADMIN','MERCHANT_SUSPENDED','UI_VISUAL_ACCEPTANCE','$merchantId',null,'{"targetType":"MERCHANT","reason":"REVIEW_REQUIRED"}'::jsonb,'ui-admin-017-03',null,'2026-09-16T07:02:00Z'),
('17171717-1717-4171-8171-171717171704','$adminId','USER','ADMIN','CATALOG_ITEM_RESTORED','UI_VISUAL_ACCEPTANCE','$productItemId',null,'{"targetType":"CATALOG_ITEM","result":"RESTORED"}'::jsonb,'ui-admin-017-04',null,'2026-09-16T07:01:00Z'),
('17171717-1717-4171-8171-171717171705','$adminId','USER','ADMIN','ORDER_ISSUE_CLOSED','UI_VISUAL_ACCEPTANCE','$deliveryProductOrderId',null,'{"targetType":"ORDER","result":"CLOSED_BY_POLICY"}'::jsonb,'ui-admin-017-05',null,'2026-09-16T07:00:00Z')
on conflict(id) do update set
    actor_user_id=excluded.actor_user_id,actor_type=excluded.actor_type,event_category=excluded.event_category,
    action=excluded.action,entity_type=excluded.entity_type,entity_id=excluded.entity_id,
    before_snapshot=excluded.before_snapshot,after_snapshot=excluded.after_snapshot,
    request_id=excluded.request_id,ip_hash_or_metadata=excluded.ip_hash_or_metadata,created_at=excluded.created_at;
commit;
"@

$previousOutputEncoding = $OutputEncoding
try {
    # Windows PowerShell otherwise sends native-command stdin through the legacy
    # code page and permanently turns Vietnamese fixture text into question marks.
    $OutputEncoding = New-Object System.Text.UTF8Encoding($false)
    $result = $sql | & docker exec -i $PostgresContainer psql -v ON_ERROR_STOP=1 -U covie -d covie 2>&1
    $dockerExitCode = $LASTEXITCODE
} finally {
    $OutputEncoding = $previousOutputEncoding
}
if ($dockerExitCode -ne 0) { throw ($result -join [Environment]::NewLine) }

$now = [DateTimeOffset]::UtcNow
$fixture = [ordered]@{
    generatedAt = $now.ToString("o")
    buyerId = $buyerId
    buyerEmail = $buyerEmail
    buyerPassword = $buyerPassword
    zeroReputationBuyerEmail = $zeroReputationBuyerEmail
    zeroReputationBuyerPassword = $zeroReputationBuyerPassword
    warningReputationBuyerEmail = $warningReputationBuyerEmail
    warningReputationBuyerPassword = $warningReputationBuyerPassword
    sellerId = $sellerId
    sellerEmail = $sellerEmail
    sellerPassword = $sellerPassword
    adminId = $adminId
    adminEmail = $adminEmail
    adminPassword = $adminPassword
    merchantId = $merchantId
    publicServiceMerchantId = $publicServiceMerchantId
    emptyNoticeMerchantId = $emptyNoticeMerchantId
    publicMixedMerchantId = $publicMixedMerchantId
    locationId = $locationId
    productItemId = $productItemId
    serviceItemId = $serviceItemId
    resaleItemId = $resaleItemId
    productOfferId = $productOfferId
    serviceOfferId = $serviceOfferTwoId
    addressId = $addressId
    pendingProductOrderId = $pendingProductOrderId
    deliveryProductOrderId = $deliveryProductOrderId
    pickupProductOrderId = $pickupProductOrderId
    serviceOrderId = $serviceOrderId
    reviewEligibleOrderId = $reviewEligibleOrderId
    alreadyReviewedOrderId = $alreadyReviewedOrderId
    reviewDisabledOrderId = $reviewDisabledOrderId
    candidateNeedId = $candidateNeedId
    respondedNeedId = $respondedNeedId
    emptyCandidateNeedId = $emptyCandidateNeedId
}
$directory = Split-Path -Parent $outputPath
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$fixtureJson = $fixture | ConvertTo-Json
$utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($outputPath, $fixtureJson, $utf8WithoutBom)

# Fail here instead of later in Node/Playwright if the generated handoff file is invalid.
$validatedFixture = Get-Content -LiteralPath $outputPath -Raw | ConvertFrom-Json
if ($validatedFixture.buyerId -ne $buyerId -or $validatedFixture.productItemId -ne $productItemId) {
    throw "Generated UI fixture metadata failed validation: $outputPath"
}

Write-Output "Local UI fixture ready: $outputPath"
Write-Output "Buyer: $buyerEmail (password stored only in the ignored fixture file)"
