[CmdletBinding()]
param(
    [string]$SourceDirectory,
    [string]$OutputPath
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
if (-not $SourceDirectory) {
    $SourceDirectory = Join-Path $repositoryRoot ".local/dev/visual-review/chunk3"
}
if (-not $OutputPath) {
    $OutputPath = Join-Path $repositoryRoot ".local/dev/visual-review/acceptance-2026-09-13/prior-inventory.json"
}

$resolvedSource = (Resolve-Path -LiteralPath $SourceDirectory).Path
$pngFiles = @(Get-ChildItem -LiteralPath $resolvedSource -Filter "*.png" -File | Sort-Object Name)
if ($pngFiles.Count -eq 0) {
    throw "No PNG files found in visual evidence source: $resolvedSource"
}

Add-Type -AssemblyName System.Drawing

function Get-RelativePath([string]$basePath, [string]$targetPath) {
    $baseUri = New-Object System.Uri(($basePath.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar))
    $targetUri = New-Object System.Uri($targetPath)
    return [System.Uri]::UnescapeDataString($baseUri.MakeRelativeUri($targetUri).ToString())
}

function Get-ScreenPrefix([string]$baseName) {
    if ($baseName -match '^(?<prefix>.+)-\d+-\d+-\d+$') {
        return $Matches.prefix
    }
    return $baseName
}

function Get-EvidenceGroup([string]$prefix) {
    if ($prefix.StartsWith("adm-")) { return "Admin" }
    if ($prefix.StartsWith("sel-")) { return "Seller" }
    if ($prefix.StartsWith("pub-")) { return "Public" }
    if ($prefix.StartsWith("buy-")) { return "Buyer" }
    if ($prefix.StartsWith("need-")) { return "Need" }
    if ($prefix.StartsWith("auth-")) { return "Auth" }
    return "Other"
}

$records = foreach ($file in $pngFiles) {
    $image = [System.Drawing.Image]::FromFile($file.FullName)
    try {
        $width = $image.Width
        $height = $image.Height
    } finally {
        $image.Dispose()
    }

    $prefix = Get-ScreenPrefix $file.BaseName
    [ordered]@{
        relativePath = Get-RelativePath $repositoryRoot $file.FullName
        fileName = $file.Name
        screenPrefix = $prefix
        group = Get-EvidenceGroup $prefix
        classification = "REFERENCE_ONLY"
        sha256 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        width = $width
        height = $height
        bytes = $file.Length
        lastWriteTimeUtc = $file.LastWriteTimeUtc.ToString("o")
    }
}

$groupOrder = @("Admin", "Seller", "Public", "Buyer", "Need", "Auth", "Other")
$groups = foreach ($name in $groupOrder) {
    [ordered]@{
        name = $name
        count = @($records | Where-Object { $_.group -eq $name }).Count
    }
}

$inventory = [ordered]@{
    schemaVersion = 1
    generatedAtUtc = [DateTimeOffset]::UtcNow.ToString("o")
    sourceDirectory = Get-RelativePath $repositoryRoot $resolvedSource
    sourceClassification = "REFERENCE_ONLY"
    totalFiles = $records.Count
    groups = $groups
    files = $records
}

$outputDirectory = Split-Path -Parent $OutputPath
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$tempPath = "$OutputPath.tmp-$([Guid]::NewGuid().ToString('N'))"
$utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
try {
    [System.IO.File]::WriteAllText($tempPath, ($inventory | ConvertTo-Json -Depth 8), $utf8WithoutBom)
    Move-Item -LiteralPath $tempPath -Destination $OutputPath -Force
} finally {
    if (Test-Path -LiteralPath $tempPath) { Remove-Item -LiteralPath $tempPath -Force }
}

Write-Output "Inventoried $($records.Count) reference-only PNG files: $OutputPath"
