# Create Handoff Package for Expert Review
# Creates a clean ZIP under 100MB containing only source code and documentation

$ProjectRoot = $PSScriptRoot
$OutputZip = Join-Path $ProjectRoot "CryptoTrader-Handoff-$(Get-Date -Format 'yyyy-MM-dd').zip"
$TempDir = Join-Path $env:TEMP "CryptoTrader-Handoff"

Write-Host "Creating expert handoff package..." -ForegroundColor Cyan
Write-Host "Source: $ProjectRoot" -ForegroundColor Gray
Write-Host "Output: $OutputZip" -ForegroundColor Gray

# Clean temp directory
if (Test-Path $TempDir) {
    Remove-Item $TempDir -Recurse -Force
}
New-Item -ItemType Directory -Path $TempDir | Out-Null

# Copy source files
Write-Host "`nCopying source files..." -ForegroundColor Yellow

# Kotlin source files
Write-Host "  - Kotlin source (.kt)"
Copy-Item -Path "$ProjectRoot\app\src\main\java\com\cryptotrader" -Destination "$TempDir\app\src\main\java\com\cryptotrader" -Recurse -Force

# Python scripts
Write-Host "  - Python scripts (.py)"
if (Test-Path "$ProjectRoot\scripts") {
    Copy-Item -Path "$ProjectRoot\scripts" -Destination "$TempDir\scripts" -Recurse -Force
}

# Gradle build files
Write-Host "  - Gradle build files"
Copy-Item -Path "$ProjectRoot\build.gradle.kts" -Destination "$TempDir\" -Force
Copy-Item -Path "$ProjectRoot\settings.gradle.kts" -Destination "$TempDir\" -Force
Copy-Item -Path "$ProjectRoot\gradle.properties" -Destination "$TempDir\" -Force
Copy-Item -Path "$ProjectRoot\app\build.gradle.kts" -Destination "$TempDir\app\" -Force

# Android manifest
Write-Host "  - Android manifest"
Copy-Item -Path "$ProjectRoot\app\src\main\AndroidManifest.xml" -Destination "$TempDir\app\src\main\" -Force

# ProGuard rules
if (Test-Path "$ProjectRoot\app\proguard-rules.pro") {
    Write-Host "  - ProGuard rules"
    Copy-Item -Path "$ProjectRoot\app\proguard-rules.pro" -Destination "$TempDir\app\" -Force
}

# Documentation
Write-Host "  - Documentation (.md)"
Get-ChildItem -Path $ProjectRoot -Filter "*.md" | ForEach-Object {
    Copy-Item $_.FullName -Destination $TempDir -Force
}

# Resources (small files only)
Write-Host "  - Android resources"
if (Test-Path "$ProjectRoot\app\src\main\res") {
    # Copy only XML files (layouts, values, etc.) - no images/drawables
    $resDir = "$ProjectRoot\app\src\main\res"
    Get-ChildItem -Path $resDir -Recurse -Include "*.xml" | ForEach-Object {
        $relativePath = $_.FullName.Replace($resDir, "")
        $destPath = Join-Path "$TempDir\app\src\main\res" $relativePath
        $destDir = Split-Path $destPath
        if (!(Test-Path $destDir)) {
            New-Item -ItemType Directory -Path $destDir -Force | Out-Null
        }
        Copy-Item $_.FullName -Destination $destPath -Force
    }
}

# Create README for handoff
$handoffReadme = @"
# CryptoTrader - Expert Review Package

**Created:** $(Get-Date -Format 'yyyy-MM-dd HH:mm')
**Purpose:** Expert code review and technical assessment

## Contents

This package contains:
- ✅ All Kotlin source code (.kt files)
- ✅ Python scripts (R2 upload, etc.)
- ✅ Gradle build configuration
- ✅ Android manifest
- ✅ Documentation (PROJECT_OVERVIEW.md, etc.)
- ✅ Android resource files (XML only)

**NOT included:**
- ❌ Build artifacts (APKs, binaries)
- ❌ Gradle cache (.gradle/)
- ❌ IDE files (.idea/)
- ❌ Historical data files (30GB+)
- ❌ Generated code (build/)

## Quick Start

1. Open in Android Studio
2. Sync Gradle (may take 5-10 minutes for dependencies)
3. Review PROJECT_OVERVIEW.md for complete project documentation
4. Key files to review:
   - \`domain/backtesting/BacktestEngine.kt\` - Core backtesting logic
   - \`data/cloud/CloudStorageClient.kt\` - R2 cloud integration
   - \`workers/AIAdvisorWorker.kt\` - AI trading advisor
   - \`domain/trading/OrderManager.kt\` - Order execution

## Architecture

Clean Architecture with 3 layers:
- **Presentation:** Jetpack Compose UI
- **Domain:** Business logic, use cases
- **Data:** Repositories, DAOs, API clients

## Technology Stack

- Kotlin 1.9.20
- Jetpack Compose
- Hilt (DI)
- Room Database
- Retrofit + OkHttp
- AWS SDK (for Cloudflare R2)
- Apache Arrow (Parquet files)

## Documentation

See \`PROJECT_OVERVIEW.md\` for:
- Complete feature list
- Implementation status
- Known issues
- Database schema
- API integrations
- Security considerations

## Contact

This is a personal project for private crypto trading.
No public distribution planned.

---

**Note:** API keys are NOT included. The app requires:
- Kraken API key (for trading)
- Anthropic API key (for AI analysis)
- Cloudflare R2 credentials (for data storage)

These must be configured in the app's settings after installation.
"@

$handoffReadme | Out-File -FilePath "$TempDir\HANDOFF_README.md" -Encoding UTF8

# Create the ZIP
Write-Host "`nCreating ZIP archive..." -ForegroundColor Yellow
Compress-Archive -Path "$TempDir\*" -DestinationPath $OutputZip -Force

# Get size
$zipSize = (Get-Item $OutputZip).Length / 1MB
Write-Host "`n✅ Handoff package created successfully!" -ForegroundColor Green
Write-Host "   File: $OutputZip" -ForegroundColor Cyan
Write-Host "   Size: $([math]::Round($zipSize, 2)) MB" -ForegroundColor Cyan

if ($zipSize -gt 100) {
    Write-Host "`n⚠️  Warning: Package exceeds 100MB!" -ForegroundColor Yellow
    Write-Host "   Consider removing additional files if needed." -ForegroundColor Yellow
} else {
    Write-Host "`n✅ Package size is under 100MB limit." -ForegroundColor Green
}

# Cleanup
Remove-Item $TempDir -Recurse -Force

Write-Host "`nPackage ready for expert review!" -ForegroundColor Green
Write-Host "You can send: $OutputZip" -ForegroundColor Cyan
