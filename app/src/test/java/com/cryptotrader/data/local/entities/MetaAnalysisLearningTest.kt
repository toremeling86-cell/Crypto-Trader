package com.cryptotrader.data.local.entities

import org.junit.Test
import org.junit.Assert.*

/**
 * Meta-Analysis Learning Test (v19+)
 *
 * Tests the meta-analysis learning system and knowledge base integration.
 * Ensures that meta-analyses can contribute to the AI learning system.
 *
 * Phase 1.8 - P0-2: Meta-Analysis Integration
 */
class MetaAnalysisLearningTest {

    @Test
    fun `meta-analysis entity includes learningEnabled field with default TRUE`() {
        // GIVEN: A meta-analysis entity
        val metaAnalysis = MetaAnalysisEntity(
            reportIds = "[1,2,3]",
            reportCount = 3,
            findings = "Test findings",
            recommendedStrategyJson = "{}",
            strategyName = "Test Strategy",
            tradingPairs = "[\"XBTUSD\"]",
            confidence = 0.8,
            riskLevel = "MEDIUM",
            status = "PENDING"
        )

        // THEN: learningEnabled should default to TRUE
        assertTrue("learningEnabled should default to true", metaAnalysis.learningEnabled)
    }

    @Test
    fun `meta-analysis can disable learning`() {
        // GIVEN: A meta-analysis with learning disabled
        val metaAnalysis = MetaAnalysisEntity(
            reportIds = "[4,5,6]",
            reportCount = 3,
            findings = "Test findings",
            recommendedStrategyJson = "{}",
            strategyName = "Test Strategy",
            tradingPairs = "[\"ETHUSD\"]",
            confidence = 0.7,
            riskLevel = "LOW",
            status = "COMPLETED",
            learningEnabled = false
        )

        // THEN: learningEnabled should be false
        assertFalse("learningEnabled should be false when explicitly set", metaAnalysis.learningEnabled)
    }

    @Test
    fun `knowledge base entity stores cross-strategy learning`() {
        // GIVEN: A knowledge base entry
        val knowledge = KnowledgeBaseEntity(
            category = "INDICATOR",
            title = "RSI Oversold in Bull Markets",
            insight = "RSI < 30 shows 75% win rate in bull markets for BTC",
            recommendation = "Use RSI < 30 as entry signal during confirmed uptrends",
            marketRegime = "BULL",
            tradingPairs = "[\"XBTUSD\"]",
            confidence = 0.75,
            evidenceCount = 10,
            successRate = 0.75,
            avgReturn = 3.5,
            sourceType = "META_ANALYSIS",
            sourceIds = "[1,2,3,4,5,6,7,8,9,10]"
        )

        // THEN: Knowledge should be stored correctly
        assertEquals("INDICATOR", knowledge.category)
        assertEquals(0.75, knowledge.confidence, 0.001)
        assertEquals(10, knowledge.evidenceCount)
        assertEquals(0.75, knowledge.successRate!!, 0.001)
        assertEquals(3.5, knowledge.avgReturn!!, 0.001)
        assertTrue("Knowledge should be active by default", knowledge.isActive)
    }

    @Test
    fun `knowledge base supports multiple categories`() {
        // GIVEN: Different knowledge categories
        val categories = listOf(
            KnowledgeCategory.PATTERN,
            KnowledgeCategory.INDICATOR,
            KnowledgeCategory.RISK_MANAGEMENT,
            KnowledgeCategory.MARKET_REGIME,
            KnowledgeCategory.COST_MODEL,
            KnowledgeCategory.STRATEGY_COMBO,
            KnowledgeCategory.TIMING,
            KnowledgeCategory.CORRELATION,
            KnowledgeCategory.GENERAL
        )

        // THEN: All categories should be available
        assertEquals(9, categories.size)
        assertTrue(categories.contains(KnowledgeCategory.INDICATOR))
        assertTrue(categories.contains(KnowledgeCategory.RISK_MANAGEMENT))
    }

    @Test
    fun `knowledge base tracks confidence and evidence`() {
        // GIVEN: High-confidence knowledge with strong evidence
        val highConfidence = KnowledgeBaseEntity(
            category = "PATTERN",
            title = "Double Bottom Reversal",
            insight = "Double bottom pattern shows 80% success rate",
            recommendation = "Enter on neckline breakout with volume confirmation",
            confidence = 0.85,
            evidenceCount = 50,
            successRate = 0.80,
            avgReturn = 5.2,
            sourceType = "BACKTEST",
            sourceIds = "[1,2,3,...,50]"
        )

        // WHEN: Compare to low-confidence knowledge
        val lowConfidence = KnowledgeBaseEntity(
            category = "PATTERN",
            title = "Unconfirmed Pattern",
            insight = "New pattern, limited data",
            recommendation = "Use with caution",
            confidence = 0.40,
            evidenceCount = 2,
            successRate = null,
            avgReturn = null,
            sourceType = "AI_DISCOVERY",
            sourceIds = "[1,2]"
        )

        // THEN: High confidence should have more evidence
        assertTrue(highConfidence.confidence > lowConfidence.confidence)
        assertTrue(highConfidence.evidenceCount > lowConfidence.evidenceCount)
        assertNotNull(highConfidence.successRate)
        assertNull(lowConfidence.successRate)

        println("✅ High confidence knowledge:")
        println("   Confidence: ${highConfidence.confidence}")
        println("   Evidence: ${highConfidence.evidenceCount} sources")
        println("   Success Rate: ${highConfidence.successRate}")
    }

    @Test
    fun `knowledge base supports market regime filtering`() {
        // GIVEN: Knowledge specific to bull markets
        val bullKnowledge = KnowledgeBaseEntity(
            category = "INDICATOR",
            title = "Moving Average Crossover in Bulls",
            insight = "MA crossovers work well in trending markets",
            recommendation = "Use 50/200 MA crossover for entry signals",
            marketRegime = "BULL",
            confidence = 0.70,
            evidenceCount = 20,
            sourceType = "META_ANALYSIS",
            sourceIds = "[1,2,3,...,20]"
        )

        // AND: General knowledge applicable to all regimes
        val generalKnowledge = KnowledgeBaseEntity(
            category = "RISK_MANAGEMENT",
            title = "Stop Loss Best Practices",
            insight = "Always use stop losses regardless of market conditions",
            recommendation = "Set stop loss at 2% below entry",
            marketRegime = null, // Applies to all regimes
            confidence = 0.90,
            evidenceCount = 100,
            sourceType = "MANUAL",
            sourceIds = "[]"
        )

        // THEN: Market regime filtering should work
        assertEquals("BULL", bullKnowledge.marketRegime)
        assertNull(generalKnowledge.marketRegime)

        println("✅ Market regime filtering:")
        println("   Bull-specific: ${bullKnowledge.title}")
        println("   General: ${generalKnowledge.title}")
    }

    @Test
    fun `knowledge base supports soft delete via invalidation`() {
        // GIVEN: An active knowledge entry
        val knowledge = KnowledgeBaseEntity(
            category = "INDICATOR",
            title = "Outdated Indicator",
            insight = "This worked in 2020",
            recommendation = "Use with caution",
            confidence = 0.50,
            evidenceCount = 5,
            sourceType = "META_ANALYSIS",
            sourceIds = "[1,2,3,4,5]",
            isActive = true
        )

        // WHEN: Knowledge is invalidated
        val invalidated = knowledge.copy(
            isActive = false,
            invalidatedAt = System.currentTimeMillis(),
            invalidationReason = "Market conditions changed, no longer effective"
        )

        // THEN: Knowledge should be soft-deleted
        assertTrue(knowledge.isActive)
        assertFalse(invalidated.isActive)
        assertNotNull(invalidated.invalidatedAt)
        assertEquals("Market conditions changed, no longer effective", invalidated.invalidationReason)

        println("✅ Soft delete via invalidation:")
        println("   Original: Active = ${knowledge.isActive}")
        println("   Invalidated: Active = ${invalidated.isActive}")
        println("   Reason: ${invalidated.invalidationReason}")
    }

    @Test
    fun `knowledge base tracks source types correctly`() {
        // GIVEN: Different source types
        val metaAnalysisSource = SourceType.META_ANALYSIS
        val backtestSource = SourceType.BACKTEST
        val manualSource = SourceType.MANUAL
        val aiSource = SourceType.AI_DISCOVERY

        // THEN: All source types should be available
        assertEquals("META_ANALYSIS", metaAnalysisSource.name)
        assertEquals("BACKTEST", backtestSource.name)
        assertEquals("MANUAL", manualSource.name)
        assertEquals("AI_DISCOVERY", aiSource.name)
    }
}
