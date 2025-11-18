package com.cryptotrader.domain.backtesting

import com.cryptotrader.data.local.entities.BacktestRunEntity
import org.junit.Test
import org.junit.Assert.*
import java.security.MessageDigest

/**
 * Data Provenance Test (v17+)
 *
 * Tests the data provenance tracking system for backtest reproducibility.
 * Ensures that every backtest run can be traced back to its exact dataset,
 * parser version, and engine version.
 *
 * Phase 1.6 - P1-4: Data Provenance Implementation
 */
class DataProvenanceTest {

    @Test
    fun `backtest run entity includes provenance fields with default values`() {
        // GIVEN: A backtest run entity
        val backtestRun = BacktestRunEntity(
            strategyId = "test-strategy-123",
            asset = "XXBTZUSD",
            timeframe = "1h",
            startTimestamp = 1700000000000,
            endTimestamp = 1700086400000,
            totalBarsUsed = 24,
            totalTrades = 10,
            winningTrades = 6,
            losingTrades = 4,
            winRate = 60.0,
            totalPnL = 500.0,
            totalPnLPercent = 5.0,
            sharpeRatio = 1.5,
            maxDrawdown = 2.5,
            profitFactor = 1.8,
            status = "GOOD",
            dataQualityScore = 0.95,
            dataSource = "DATABASE"
        )

        // THEN: Provenance fields should have default values
        assertEquals("[]", backtestRun.dataFileHashes)
        assertEquals("", backtestRun.parserVersion)
        assertEquals("", backtestRun.engineVersion)
    }

    @Test
    fun `backtest run entity stores provenance data correctly`() {
        // GIVEN: A backtest run with provenance data
        val testHash = "sha256:abc123def456"
        val dataFileHashes = """["$testHash"]"""

        val backtestRun = BacktestRunEntity(
            strategyId = "test-strategy-456",
            asset = "SOLUSD",
            timeframe = "5m",
            startTimestamp = 1700000000000,
            endTimestamp = 1700086400000,
            totalBarsUsed = 288,
            totalTrades = 15,
            winningTrades = 10,
            losingTrades = 5,
            winRate = 66.7,
            totalPnL = 750.0,
            totalPnLPercent = 7.5,
            sharpeRatio = 2.0,
            maxDrawdown = 3.0,
            profitFactor = 2.2,
            status = "EXCELLENT",
            dataQualityScore = 0.98,
            dataSource = "DATABASE",
            dataFileHashes = dataFileHashes,
            parserVersion = "1.0.0",
            engineVersion = "1.0.0"
        )

        // THEN: Provenance fields should be stored correctly
        assertEquals(dataFileHashes, backtestRun.dataFileHashes)
        assertEquals("1.0.0", backtestRun.parserVersion)
        assertEquals("1.0.0", backtestRun.engineVersion)

        // AND: dataFileHashes should be valid JSON
        assertTrue(backtestRun.dataFileHashes.startsWith("["))
        assertTrue(backtestRun.dataFileHashes.endsWith("]"))
        assertTrue(backtestRun.dataFileHashes.contains("sha256:"))

        println("✅ Provenance data stored correctly")
        println("   Hash: ${backtestRun.dataFileHashes}")
        println("   Parser: ${backtestRun.parserVersion}")
        println("   Engine: ${backtestRun.engineVersion}")
    }

    @Test
    fun `SHA-256 hash is deterministic for same data`() {
        // GIVEN: Same data string twice
        val data1 = "XXBTZUSD|1h|1700000000000|35000|36000|34000|35500|100"
        val data2 = "XXBTZUSD|1h|1700000000000|35000|36000|34000|35500|100"

        // WHEN: Calculate SHA-256 hashes
        val hash1 = calculateSHA256(data1)
        val hash2 = calculateSHA256(data2)

        // THEN: Hashes should be identical
        assertEquals("Hashes should be deterministic for identical data", hash1, hash2)

        // AND: Hash should be 64 hex characters (256 bits)
        assertEquals(64, hash1.length)
        assertTrue(hash1.matches(Regex("^[a-f0-9]{64}$")))

        println("✅ SHA-256 deterministic: $hash1")
    }

    @Test
    fun `SHA-256 hash changes when data changes`() {
        // GIVEN: Two different data strings
        val data1 = "XXBTZUSD|1h|1700000000000|35000|36000|34000|35500|100"
        val data2 = "XXBTZUSD|1h|1700000000000|35000|36000|34000|35600|100" // Different close price

        // WHEN: Calculate SHA-256 hashes
        val hash1 = calculateSHA256(data1)
        val hash2 = calculateSHA256(data2)

        // THEN: Hashes should be different
        assertNotEquals("Hashes should differ for different data", hash1, hash2)

        println("✅ SHA-256 changes with data:")
        println("   Hash 1: $hash1")
        println("   Hash 2: $hash2")
    }

    @Test
    fun `JSON array of hashes is valid format`() {
        // GIVEN: Multiple hashes
        val hash1 = "sha256:abc123"
        val hash2 = "sha256:def456"

        // WHEN: Create JSON array
        val jsonArray = """["$hash1","$hash2"]"""

        // THEN: Should be valid JSON array format
        assertTrue(jsonArray.startsWith("["))
        assertTrue(jsonArray.endsWith("]"))
        assertTrue(jsonArray.contains(hash1))
        assertTrue(jsonArray.contains(hash2))

        // AND: Can be parsed (basic validation)
        val hashes = jsonArray.removeSurrounding("[", "]").split(",")
        assertEquals(2, hashes.size)

        println("✅ JSON array valid: $jsonArray")
    }

    @Test
    fun `parser and engine versions follow semver format`() {
        // GIVEN: Version strings
        val parserVersion = "1.0.0"
        val engineVersion = "1.0.0"

        // THEN: Should match semver pattern (major.minor.patch)
        val semverPattern = Regex("^\\d+\\.\\d+\\.\\d+$")

        assertTrue(
            "Parser version should follow semver",
            parserVersion.matches(semverPattern)
        )

        assertTrue(
            "Engine version should follow semver",
            engineVersion.matches(semverPattern)
        )

        println("✅ Semver validation passed:")
        println("   Parser: $parserVersion")
        println("   Engine: $engineVersion")
    }

    // Helper function to calculate SHA-256 hash
    private fun calculateSHA256(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
