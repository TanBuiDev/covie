[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$scriptPath = Join-Path $repositoryRoot "scripts/inventory-visual-evidence.ps1"
$sourcePath = Join-Path $repositoryRoot ".local/dev/visual-review/chunk3"
$outputPath = Join-Path $repositoryRoot ".local/dev/visual-review/acceptance-2026-09-13/prior-inventory.json"

if (-not (Test-Path -LiteralPath $scriptPath)) {
    throw "Missing visual evidence inventory script: $scriptPath"
}

$before = @(Get-ChildItem -LiteralPath $sourcePath -Filter "*.png" -File | Sort-Object Name | ForEach-Object {
    "{0}|{1}|{2}" -f $_.Name, $_.Length, (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
})

& $scriptPath -SourceDirectory $sourcePath -OutputPath $outputPath | Out-Host
if (-not $?) { throw "Visual evidence inventory failed." }

$after = @(Get-ChildItem -LiteralPath $sourcePath -Filter "*.png" -File | Sort-Object Name | ForEach-Object {
    "{0}|{1}|{2}" -f $_.Name, $_.Length, (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
})
if (Compare-Object -ReferenceObject $before -DifferenceObject $after) {
    throw "Inventory must not rename, remove, overwrite, or otherwise modify source PNG files."
}

$bytes = [System.IO.File]::ReadAllBytes($outputPath)
if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
    throw "Inventory JSON must be UTF-8 without BOM."
}

$inventory = Get-Content -LiteralPath $outputPath -Raw | ConvertFrom-Json
if ($inventory.schemaVersion -ne 1) { throw "Unexpected inventory schema version." }
if ($inventory.sourceClassification -ne "REFERENCE_ONLY") { throw "Prior screenshots must remain REFERENCE_ONLY." }
if ($inventory.totalFiles -ne 267 -or @($inventory.files).Count -ne 267) {
    throw "Expected 267 inventoried PNGs, found '$($inventory.totalFiles)' metadata and '$(@($inventory.files).Count)' records."
}

$relativePaths = @($inventory.files | ForEach-Object { $_.relativePath })
if (@($relativePaths | Sort-Object -Unique).Count -ne 267) { throw "Every PNG must have one unique relative path." }
foreach ($file in $inventory.files) {
    if ($file.classification -ne "REFERENCE_ONLY") { throw "Every prior PNG must be REFERENCE_ONLY: $($file.relativePath)" }
    if ($file.sha256 -notmatch '^[0-9a-f]{64}$') { throw "Invalid SHA-256 for $($file.relativePath)" }
    if ($file.width -le 0 -or $file.height -le 0) { throw "Missing PNG dimensions for $($file.relativePath)" }
    if (-not $file.lastWriteTimeUtc) { throw "Missing timestamp for $($file.relativePath)" }
    if (-not $file.screenPrefix) { throw "Missing screen prefix for $($file.relativePath)" }
}

$expectedGroups = [ordered]@{
    Admin = 45
    Seller = 90
    Public = 36
    Buyer = 30
    Need = 24
    Auth = 18
    Other = 24
}
foreach ($entry in $expectedGroups.GetEnumerator()) {
    $group = $inventory.groups | Where-Object { $_.name -eq $entry.Key }
    $actual = [int]$group.count
    if ($actual -ne $entry.Value) { throw "Expected group $($entry.Key)=$($entry.Value), found '$actual'." }
}

Write-Output "inventory-visual-evidence regression checks passed"
