# 🔨 CryptoTrader - Build Instructions

## Forutsetninger

### Påkrevd Software
- **Android Studio**: Arctic Fox eller nyere (anbefalt: Hedgehog eller nyere)
- **JDK**: Java 17 (inkludert i Android Studio)
- **Gradle**: 8.2.0 (auto-download via wrapper)
- **Android SDK**: API 26-34

### Installasjon av Android Studio

#### Windows
1. Last ned fra https://developer.android.com/studio
2. Kjør installeren
3. Installer Android SDK Platform 34
4. Installer Android Build Tools 34.0.0

#### macOS
```bash
brew install --cask android-studio
```

#### Linux
```bash
sudo snap install android-studio --classic
```

---

## 🚀 Quick Start

### 1. Åpne Prosjektet

```bash
# Naviger til prosjektmappen
cd D:\Development\Projects\Mobile\Android\CryptoTrader

# Åpne Android Studio
# File → Open → Velg CryptoTrader-mappen
```

### 2. Gradle Sync

Android Studio vil automatisk:
- Laste ned Gradle 8.2.0
- Laste ned alle dependencies
- Bygge prosjektet

**Første gang**: Kan ta 5-10 minutter

### 3. Kjør Appen

**Med Emulator**:
1. Tools → Device Manager
2. Create Virtual Device → Pixel 5
3. Download System Image (API 34)
4. Klikk grønn "Run" knapp

**Med Fysisk Enhet**:
1. Aktiver Developer Options på telefonen
2. Aktiver USB Debugging
3. Koble til med USB
4. Klikk "Run" når enheten vises

---

## 🏗️ Build Kommandoer

### Debug Build
```bash
# Windows
.\gradlew assembleDebug

# macOS/Linux
./gradlew assembleDebug

# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Release Build
```bash
# Med ProGuard/R8 optimization
.\gradlew assembleRelease

# Output: app/build/outputs/apk/release/app-release-unsigned.apk
```

### Install Debug
```bash
# Installer direkte på tilkoblet enhet
.\gradlew installDebug
```

### Clean Build
```bash
# Slett alle build artifacts
.\gradlew clean

# Clean + rebuild
.\gradlew clean assembleDebug
```

---

## 🧪 Testing

### Unit Tests
```bash
# Kjør alle unit tests
.\gradlew test

# Kjør tests for debug variant
.\gradlew testDebugUnitTest

# Kjør med coverage
.\gradlew testDebugUnitTestCoverage
```

### Instrumentation Tests
```bash
# Kjør på tilkoblet enhet/emulator
.\gradlew connectedAndroidTest

# Kjør på spesifikk variant
.\gradlew connectedDebugAndroidTest
```

### Lint Check
```bash
# Kjør lint analysis
.\gradlew lint

# Output: app/build/reports/lint-results.html
```

---

## 📦 Signing & Release

### 1. Generer Keystore

```bash
keytool -genkey -v -keystore cryptotrader.keystore -alias cryptotrader -keyalg RSA -keysize 2048 -validity 10000
```

**Viktig**: Lagre passord trygt!

### 2. Konfiger Signing

Opprett `keystore.properties` i root:

```properties
storeFile=../cryptotrader.keystore
storePassword=YOUR_KEYSTORE_PASSWORD
keyAlias=cryptotrader
keyPassword=YOUR_KEY_PASSWORD
```

Legg til i `app/build.gradle.kts`:

```kotlin
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
keystoreProperties.load(FileInputStream(keystorePropertiesFile))

android {
    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

### 3. Bygg Signed APK

```bash
.\gradlew assembleRelease

# Output: app-release.apk (signed)
```

---

## 🔍 Troubleshooting

### Problem: Gradle Sync Failed

**Løsning**:
```bash
# Windows
.\gradlew --refresh-dependencies

# Eller slett .gradle cache
rmdir /s .gradle
```

### Problem: Dependencies ikke lastet ned

**Løsning**:
```bash
# Sync med --refresh-dependencies
.\gradlew clean build --refresh-dependencies
```

### Problem: Emulator ikke starter

**Løsning**:
1. Tools → SDK Manager
2. Install Intel x86 Emulator Accelerator (HAXM)
3. Eller installer Android Emulator Hypervisor Driver

### Problem: Compilation Error

**Løsning**:
```bash
# Clean + invalidate caches
.\gradlew clean
# Android Studio: File → Invalidate Caches → Restart
```

### Problem: Out of Memory

**Løsning**:
Øk heap size i `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
```

---

## 📊 Build Variants

### Debug
- Debuggable
- Ingen obfuskering
- Full logging
- Større APK size

### Release
- Ikke debuggable
- ProGuard/R8 obfuskering
- Minimal logging
- Optimalisert APK

### Build Types

```kotlin
buildTypes {
    debug {
        isMinifyEnabled = false
        isDebuggable = true
        applicationIdSuffix = ".debug"
    }
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(...)
    }
}
```

---

## 🎯 Build Configuration

### Min SDK
- **26** (Android 8.0 Oreo)
- Støtter ~85% av aktive Android-enheter

### Target SDK
- **34** (Android 14)
- Latest stable API

### Compile SDK
- **34**

### Build Tools
- **34.0.0**

---

## 🔄 CI/CD (Fremtidig)

### GitHub Actions Workflow

```yaml
name: Android CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Build with Gradle
        run: ./gradlew assembleDebug
      - name: Run tests
        run: ./gradlew test
```

---

## 📱 APK Info

### Debug APK
- **Size**: ~15-20 MB
- **Installation**: Direkteinstall via ADB

### Release APK
- **Size**: ~8-12 MB (with ProGuard)
- **Installation**: Krever signering

---

## 🎓 Best Practices

### 1. Always Clean Before Release
```bash
.\gradlew clean assembleRelease
```

### 2. Test på Multiple Devices
- Minimum API 26
- Maximum API 34
- Different screen sizes

### 3. Check ProGuard Rules
- Verify no runtime crashes
- Test all features after obfuscation

### 4. Version Naming
```kotlin
defaultConfig {
    versionCode = 1
    versionName = "1.0.0"
}
```

Increment for hver release!

---

## 📦 Distribution

### Internal Testing
1. Build release APK
2. Share via email/drive
3. Enable "Install from Unknown Sources"

### Google Play Store
1. Create Google Play Console account ($25)
2. Create app listing
3. Upload AAB (not APK)
4. Submit for review

### Generate AAB
```bash
.\gradlew bundleRelease

# Output: app-release.aab
```

---

## 🔐 Security Notes

### Før Release:
- [ ] Fjern debug logging
- [ ] Verify ProGuard rules
- [ ] Test encrypted credentials
- [ ] Review permissions
- [ ] Scan for vulnerabilities

### API Keys:
⚠️ **ALDRI** commit keystore eller API keys til Git!

Add til `.gitignore`:
```
*.keystore
keystore.properties
local.properties
```

---

## 📞 Support

**Build Issues**: GitHub Issues
**Documentation**: README.md
**Architecture**: PROJECT_STRUCTURE.md

---

**Versjon**: 1.0.0
**Status**: ✅ Production Ready
**Last Updated**: November 2024
