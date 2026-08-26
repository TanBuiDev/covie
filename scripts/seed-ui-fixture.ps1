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
$sellerId = "22222222-2222-4222-8222-222222222222"
$buyerEmail = "ui.buyer@covie.local"
$buyerPassword = "Cv!$([Guid]::NewGuid().ToString('N'))"
$merchantId = "33333333-3333-4333-8333-333333333333"
$locationId = "44444444-4444-4444-8444-444444444444"
$foodCategoryId = "55555555-5555-4555-8555-555555555551"
$drinkCategoryId = "55555555-5555-4555-8555-555555555552"
$categoryId = "55555555-5555-4555-8555-555555555553"
$serviceCategoryId = "55555555-5555-4555-8555-555555555554"
$productItemId = "66666666-6666-4666-8666-666666666661"
$serviceItemId = "66666666-6666-4666-8666-666666666662"
$resaleItemId = "66666666-6666-4666-8666-666666666663"
$productVariantId = "77777777-7777-4777-8777-777777777771"
$serviceVariantTwoId = "77777777-7777-4777-8777-777777777772"
$resaleVariantId = "77777777-7777-4777-8777-777777777773"
$serviceVariantOneId = "77777777-7777-4777-8777-777777777774"
$serviceVariantThreeId = "77777777-7777-4777-8777-777777777775"
$productOfferId = "88888888-8888-4888-8888-888888888881"
$serviceOfferTwoId = "88888888-8888-4888-8888-888888888882"
$resaleOfferId = "88888888-8888-4888-8888-888888888883"
$serviceOfferOneId = "88888888-8888-4888-8888-888888888884"
$serviceOfferThreeId = "88888888-8888-4888-8888-888888888885"

# Keep the script source ASCII-safe for Windows PowerShell 5, which reads UTF-8
# files without a BOM through the active legacy code page.
function ConvertFrom-UiFixtureBase64([string]$value) {
    return [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($value))
}
$sellerDisplayName = ConvertFrom-UiFixtureBase64 "Q2jhu6MgWGFuaCBPd25lcg=="
$addressLabel = ConvertFrom-UiFixtureBase64 "TmjDoA=="
$recipientName = ConvertFrom-UiFixtureBase64 "Tmd1eeG7hW4gVsSDbiBNaW5o"
$merchantName = ConvertFrom-UiFixtureBase64 "Q+G7rWEgaMOgbmcgQ2jhu6MgWGFuaA=="
$merchantDescription = ConvertFrom-UiFixtureBase64 "UmF1IGPhu6cgdsOgIGThu4tjaCB24bulIMSR4buLYSBwaMawxqFuZyBjaG8gdmlzdWFsIHJldmlldy4="
$locationName = ConvertFrom-UiFixtureBase64 "Q2jhu6MgWGFuaCBHcmFuZCBQYXJr"
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

$javaExecutable = (Get-Command java -ErrorAction Stop).Source
$env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $javaExecutable)
$apiDirectory = Join-Path $repoRoot "apps/api"
$classpathFile = Join-Path $apiDirectory "target/ui-fixture-classpath.txt"
Push-Location $apiDirectory
try {
    & ./mvnw -q dependency:build-classpath "-Dmdep.outputFile=target/ui-fixture-classpath.txt" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not resolve the API runtime classpath." }
    $classpath = Get-Content -Raw -LiteralPath $classpathFile
    $snippet = @"
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
System.out.println("FIXTURE_HASH=" + "{argon2id-v1}" + new Argon2PasswordEncoder(16,32,1,19456,2).encode("$buyerPassword"));
/exit
"@
    $jshell = Join-Path $env:JAVA_HOME "bin/jshell.exe"
    $hashOutput = $snippet | & $jshell --class-path $classpath 2>&1
    $hashMatch = [regex]::Match(($hashOutput -join "`n"), 'FIXTURE_HASH=(\{argon2id-v1\}\$argon2id\$[^\r\n]+)')
    if (-not $hashMatch.Success) { throw "Could not generate the disposable Argon2 fixture credential." }
    $passwordHash = $hashMatch.Groups[1].Value.Trim()
} finally {
    Pop-Location
}

$sql = @"
\set ON_ERROR_STOP on
begin;
-- A seed run starts a fresh acceptance journey without deleting historical
-- orders or unrelated local data. Scope the reset to the deterministic buyer.
update carts
set cart_status='ABANDONED', updated_at=now(), version=version+1
where user_id='$buyerId' and cart_status='ACTIVE';

insert into users(id,display_name,account_status,created_at,updated_at,version) values
('$buyerId','Minh Buyer','ACTIVE',now(),now(),0),
('$sellerId','$sellerDisplayName','ACTIVE',now(),now(),0)
on conflict(id) do update set display_name=excluded.display_name,account_status='ACTIVE',updated_at=now();

insert into system_role_assignments(id,user_id,role,created_at) values
('11111111-1111-4111-8111-333333333333','$buyerId','USER',now()),
('22222222-2222-4222-8222-333333333333','$sellerId','USER',now())
on conflict(user_id,role) do nothing;

insert into user_identities(id,user_id,provider,email_normalized,password_hash,verified_at,created_at,last_used_at)
values('11111111-1111-4111-8111-555555555555','$buyerId','EMAIL','$buyerEmail','$passwordHash',now(),now(),now())
on conflict(id) do update set password_hash=excluded.password_hash,verified_at=now(),last_used_at=now();

insert into user_addresses(id,user_id,label,recipient_name,phone,address_text,geo_point,pin_source,is_default,status,created_at,updated_at,version)
values('99999999-9999-4999-8999-999999999999','$buyerId','$addressLabel','$recipientName','0909 123 456','S5.03, Vinhomes Grand Park',ST_SetSRID(ST_MakePoint(106.842,10.842),4326)::geography,'USER_ADJUSTED',true,'ACTIVE',now(),now(),0)
on conflict(id) do update set label=excluded.label,recipient_name=excluded.recipient_name,phone=excluded.phone,address_text=excluded.address_text,status='ACTIVE',is_default=true,updated_at=now();

insert into merchant_profiles(id,name,slug,description,merchant_status,moderation_status,created_at,updated_at,version)
values('$merchantId','$merchantName','ui-cho-xanh','$merchantDescription','ACTIVE','APPROVED',now(),now(),0)
on conflict(id) do update set name=excluded.name,description=excluded.description,merchant_status='ACTIVE',moderation_status='APPROVED',updated_at=now();

insert into merchant_memberships(id,merchant_profile_id,user_id,role,membership_status,joined_at,created_at,version)
values('33333333-3333-4333-8333-444444444444','$merchantId','$sellerId','OWNER','ACTIVE',now(),now(),0)
on conflict(id) do update set membership_status='ACTIVE';

insert into merchant_contact_methods(id,merchant_profile_id,merchant_location_id,contact_type,value,qr_media_id,is_primary,is_public,status,created_at,version) values
('33333333-3333-4333-8333-555555555551','$merchantId',null,'ZALO_LINK','https://zalo.me/0909123456',null,true,true,'ACTIVE',now(),0),
('33333333-3333-4333-8333-555555555552','$merchantId',null,'MESSENGER_LINK','https://m.me/covie.ui.fixture',null,false,true,'ACTIVE',now(),0),
('33333333-3333-4333-8333-555555555553','$merchantId',null,'PHONE','0909 123 456',null,false,true,'ACTIVE',now(),0)
on conflict(id) do update set value=excluded.value,is_public=true,status='ACTIVE';

insert into merchant_locations(id,merchant_profile_id,name,address_text,geo_point,pin_source,timezone,location_status,pickup_enabled,seller_delivery_enabled,service_enabled,minimum_delivery_order,currency_code,created_at,updated_at,version)
values('$locationId','$merchantId','$locationName','S5.03, Vinhomes Grand Park',ST_SetSRID(ST_MakePoint(106.842,10.842),4326)::geography,'USER_ADJUSTED','Asia/Ho_Chi_Minh','ACTIVE',true,true,true,0,'VND',now(),now(),0)
on conflict(id) do update set name=excluded.name,address_text=excluded.address_text,location_status='ACTIVE',pickup_enabled=true,seller_delivery_enabled=true,service_enabled=true,updated_at=now();

insert into categories(id,name,slug,catalog_type,status,sort_order,version)
values
('$foodCategoryId','$foodCategoryName','ui-do-an','PRODUCT','ACTIVE',-400,0),
('$drinkCategoryId','$drinkCategoryName','ui-do-uong','PRODUCT','ACTIVE',-399,0),
('$categoryId','$categoryName','ui-tap-hoa','PRODUCT','ACTIVE',-398,0),
('$serviceCategoryId','$serviceCategoryName','ui-dich-vu','SERVICE','ACTIVE',-397,0)
on conflict(id) do update set name=excluded.name,slug=excluded.slug,status='ACTIVE',catalog_type=excluded.catalog_type,sort_order=excluded.sort_order;

insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,description,listing_status,moderation_status,search_vector,created_at,updated_at,version) values
('$productItemId','$merchantId','PRODUCT','STANDARD','$categoryId','$productName','ui-rau-cu-theo-mua','$productDescription','ACTIVE','APPROVED',to_tsvector('simple','rau cu theo mua'),now(),now(),0),
('$serviceItemId','$merchantId','SERVICE','STANDARD','$serviceCategoryId','$serviceName','ui-sua-khoa-tan-noi','$serviceDescription','ACTIVE','APPROVED',to_tsvector('simple','sua khoa tan noi'),now(),now(),0),
('$resaleItemId','$merchantId','PRODUCT','RESALE','$categoryId','$resaleName','ui-ke-go-thanh-ly','$resaleDescription','ACTIVE','APPROVED',to_tsvector('simple','ke go thanh ly'),now(),now(),0)
on conflict(id) do update set primary_category_id=excluded.primary_category_id,name=excluded.name,description=excluded.description,listing_status='ACTIVE',moderation_status='APPROVED',updated_at=now();

insert into variants(id,catalog_item_id,variant_name,is_default,variant_status,sort_order,created_at,updated_at,version) values
('$productVariantId','$productItemId','$productVariantName',true,'ACTIVE',0,now(),now(),0),
('$serviceVariantOneId','$serviceItemId','$serviceVariantOneName',false,'ACTIVE',0,now(),now(),0),
('$serviceVariantTwoId','$serviceItemId','$serviceVariantTwoName',true,'ACTIVE',1,now(),now(),0),
('$serviceVariantThreeId','$serviceItemId','$serviceVariantThreeName',false,'ACTIVE',2,now(),now(),0),
('$resaleVariantId','$resaleItemId','$resaleVariantName',true,'ACTIVE',0,now(),now(),0)
on conflict(id) do update set variant_name=excluded.variant_name,is_default=excluded.is_default,variant_status='ACTIVE',sort_order=excluded.sort_order,updated_at=now();

insert into location_variants(id,merchant_location_id,variant_id,price,currency_code,is_available,inventory_mode,preparation_time_minutes,created_at,updated_at,version) values
('$productOfferId','$locationId','$productVariantId',85000,'VND',true,'TRACKED',30,now(),now(),0),
('$serviceOfferOneId','$locationId','$serviceVariantOneId',150000,'VND',true,null,60,now(),now(),0),
('$serviceOfferTwoId','$locationId','$serviceVariantTwoId',280000,'VND',true,null,60,now(),now(),0),
('$serviceOfferThreeId','$locationId','$serviceVariantThreeId',400000,'VND',true,null,60,now(),now(),0),
('$resaleOfferId','$locationId','$resaleVariantId',320000,'VND',true,'TRACKED',15,now(),now(),0)
on conflict(id) do update set price=excluded.price,is_available=true,updated_at=now();

insert into inventory_balances(location_variant_id,stock_on_hand,reserved_quantity,updated_at,version) values
('$productOfferId',50,0,now(),0),('$resaleOfferId',1,0,now(),0)
on conflict(location_variant_id) do update set stock_on_hand=excluded.stock_on_hand,reserved_quantity=0,updated_at=now();

insert into delivery_coverages(id,merchant_location_id,coverage_type,radius_enabled,radius_meters,area_enabled,coverage_status,created_at,updated_at,version) values
('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1','$locationId','PRODUCT_DELIVERY',true,10000,false,'ACTIVE',now(),now(),0),
('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2','$locationId','SERVICE_AREA',true,10000,false,'ACTIVE',now(),now(),0)
on conflict(id) do update set coverage_status='ACTIVE',radius_enabled=true,radius_meters=10000,updated_at=now();

insert into shipping_policies(id,merchant_location_id,mode,base_fee,currency_code,policy_status,created_at,updated_at,version)
values('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','$locationId','FIXED',25000,'VND','ACTIVE',now(),now(),0)
on conflict(id) do update set mode='FIXED',base_fee=25000,policy_status='ACTIVE',updated_at=now();

insert into merchant_reputation_stats(merchant_profile_id,eligible_order_count,completed_order_count,seller_cancel_after_confirm_count,excluded_issue_count,completion_rate_basis_points,updated_at)
values('$merchantId',125,120,5,0,9600,now())
on conflict(merchant_profile_id) do update set eligible_order_count=125,completed_order_count=120,seller_cancel_after_confirm_count=5,excluded_issue_count=0,completion_rate_basis_points=9600,updated_at=now();

insert into merchant_rating_stats(merchant_profile_id,review_count,rating_sum,average_rating,updated_at)
values('$merchantId',96,461,4.80,now())
on conflict(merchant_profile_id) do update set review_count=96,rating_sum=461,average_rating=4.80,updated_at=now();
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
    merchantId = $merchantId
    productItemId = $productItemId
    serviceItemId = $serviceItemId
    resaleItemId = $resaleItemId
    productOfferId = $productOfferId
    serviceOfferId = $serviceOfferTwoId
    addressId = "99999999-9999-4999-8999-999999999999"
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
