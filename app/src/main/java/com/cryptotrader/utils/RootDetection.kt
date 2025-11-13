package com.cryptotrader.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import timber.log.Timber
import java.io.File

/**
 * Root/Jailbreak detection utility
 *
 * Detects if the device is rooted, which poses security risks for trading apps:
 * - Modified system files can intercept API calls
 * - Root apps can read memory and steal API keys
 * - Xposed/Frida frameworks can modify app behavior
 *
 * PRODUCTION: Consider using commercial root detection libraries like:
 * - RootBeer: https://github.com/scottyab/rootbeer
 * - SafetyNet Attestation API
 */
object RootDetection {

    /**
     * Check if device is rooted
     *
     * @return true if device appears to be rooted
     */
    fun isDeviceRooted(): Boolean {
        return checkRootMethod1() ||
               checkRootMethod2() ||
               checkForDangerousApps() ||
               checkForRWPaths()
    }

    /**
     * Method 1: Check for common root binaries
     */
    private fun checkRootMethod1(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )

        for (path in paths) {
            if (File(path).exists()) {
                Timber.w("Root detected: Found su binary at $path")
                return true
            }
        }
        return false
    }

    /**
     * Method 2: Check for root management apps
     */
    private fun checkRootMethod2(): Boolean {
        val rootApps = arrayOf(
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.koushikdutta.rommanager",
            "com.koushikdutta.rommanager.license",
            "com.dimonvideo.luckypatcher",
            "com.chelpus.lackypatch",
            "com.ramdroid.appquarantine",
            "com.ramdroid.appquarantinepro"
        )

        // Note: This method won't work without context
        // It's kept here as a template
        return false
    }

    /**
     * Method 3: Check for dangerous apps (context required)
     */
    fun checkRootWithContext(context: Context): Boolean {
        return checkForDangerousAppsWithContext(context) ||
               checkForRootManagementApps(context)
    }

    /**
     * Check for root management apps using PackageManager
     */
    private fun checkForRootManagementApps(context: Context): Boolean {
        val rootApps = arrayOf(
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.topjohnwu.magisk" // Magisk
        )

        val packageManager = context.packageManager
        for (packageName in rootApps) {
            try {
                packageManager.getPackageInfo(packageName, 0)
                Timber.w("Root detected: Found root app $packageName")
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                // App not found, continue
            }
        }
        return false
    }

    /**
     * Check for dangerous apps that can modify behavior
     */
    private fun checkForDangerousApps(): Boolean {
        val dangerousApps = arrayOf(
            "de.robv.android.xposed.installer",
            "com.saurik.substrate",
            "com.zachspong.temprootremovejb",
            "com.amphoras.hidemyroot",
            "com.amphoras.hidemyrootadfree",
            "com.formyhm.hiderootPremium"
        )

        // This needs context to work properly
        // Kept as template
        return false
    }

    /**
     * Check for dangerous apps using context
     */
    private fun checkForDangerousAppsWithContext(context: Context): Boolean {
        val dangerousApps = arrayOf(
            "de.robv.android.xposed.installer", // Xposed
            "com.saurik.substrate", // Substrate
            "io.va.exposed" // VirtualXposed
        )

        val packageManager = context.packageManager
        for (packageName in dangerousApps) {
            try {
                packageManager.getPackageInfo(packageName, 0)
                Timber.w("Root detected: Found dangerous app $packageName")
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                // App not found, continue
            }
        }
        return false
    }

    /**
     * Method 4: Check for RW paths (read-write system directories)
     */
    private fun checkForRWPaths(): Boolean {
        val paths = arrayOf(
            "/system",
            "/system/bin",
            "/system/sbin",
            "/system/xbin",
            "/vendor/bin",
            "/sbin",
            "/etc"
        )

        for (path in paths) {
            try {
                val file = File(path)
                if (file.exists() && file.canWrite()) {
                    Timber.w("Root detected: System path is writable: $path")
                    return true
                }
            } catch (e: Exception) {
                // Ignore errors
            }
        }
        return false
    }

    /**
     * Check if running in emulator (additional security check)
     */
    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
                "google_sdk" == Build.PRODUCT)
    }

    /**
     * Comprehensive security check
     */
    fun performSecurityCheck(context: Context): SecurityCheckResult {
        val isRooted = isDeviceRooted() || checkRootWithContext(context)
        val isEmulator = isEmulator()

        val threats = mutableListOf<String>()
        if (isRooted) {
            threats.add("Device is rooted - API keys and trading data may be at risk")
        }
        if (isEmulator) {
            threats.add("Running on emulator - not recommended for production trading")
        }

        return SecurityCheckResult(
            isRooted = isRooted,
            isEmulator = isEmulator,
            isSafe = !isRooted && !isEmulator,
            threats = threats
        )
    }

    /**
     * Get security recommendation based on threat level
     */
    fun getSecurityRecommendation(result: SecurityCheckResult): String {
        return when {
            result.isSafe -> "Device security: OK"
            result.isRooted && result.isEmulator ->
                "⚠️ CRITICAL: Rooted device AND emulator detected. DO NOT use for real trading!"
            result.isRooted ->
                "⚠️ WARNING: Rooted device detected. Your API keys and funds may be at risk. " +
                "Consider using a non-rooted device for trading."
            result.isEmulator ->
                "⚠️ NOTICE: Running on emulator. Fine for testing, but use real device for production."
            else -> "Unknown security status"
        }
    }
}

/**
 * Result of security check
 */
data class SecurityCheckResult(
    val isRooted: Boolean,
    val isEmulator: Boolean,
    val isSafe: Boolean,
    val threats: List<String>
)
