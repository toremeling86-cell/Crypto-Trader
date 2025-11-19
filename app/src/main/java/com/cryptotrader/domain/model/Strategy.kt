package com.cryptotrader.domain.model

import com.cryptotrader.utils.toBigDecimalMoney
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * Domain model for a trading strategy
 *
 * BigDecimal Migration (Phase 2.9):
 * - All monetary and percentage fields now use BigDecimal for exact calculations
 * - Double fields deprecated but kept for backward compatibility
 * - New code should use *Decimal fields exclusively
 */
data class Strategy(
    val id: String,
    val name: String,
    val description: String,
    val entryConditions: List<String>,
    val exitConditions: List<String>,

    // Position sizing - exact decimal arithmetic
    @Deprecated("Use positionSizePercentDecimal for exact calculations", ReplaceWith("positionSizePercentDecimal"))
    val positionSizePercent: Double,
    val positionSizePercentDecimal: BigDecimal = positionSizePercent.toBigDecimalMoney(),

    // Risk management - exact decimal arithmetic
    @Deprecated("Use stopLossPercentDecimal for exact calculations", ReplaceWith("stopLossPercentDecimal"))
    val stopLossPercent: Double,
    val stopLossPercentDecimal: BigDecimal = stopLossPercent.toBigDecimalMoney(),

    @Deprecated("Use takeProfitPercentDecimal for exact calculations", ReplaceWith("takeProfitPercentDecimal"))
    val takeProfitPercent: Double,
    val takeProfitPercentDecimal: BigDecimal = takeProfitPercent.toBigDecimalMoney(),

    val tradingPairs: List<String>,
    val isActive: Boolean = false,
    val tradingMode: TradingMode = TradingMode.INACTIVE, // INACTIVE, PAPER, LIVE
    val createdAt: Long = System.currentTimeMillis(),
    val lastExecuted: Long? = null,
    val totalTrades: Int = 0,
    val successfulTrades: Int = 0,
    val failedTrades: Int = 0,

    // Performance metrics - exact decimal arithmetic
    @Deprecated("Use winRateDecimal for exact calculations", ReplaceWith("winRateDecimal"))
    val winRate: Double = 0.0,
    val winRateDecimal: BigDecimal = winRate.toBigDecimalMoney(),

    @Deprecated("Use totalProfitDecimal for exact calculations", ReplaceWith("totalProfitDecimal"))
    val totalProfit: Double = 0.0,
    val totalProfitDecimal: BigDecimal = totalProfit.toBigDecimalMoney(),

    val riskLevel: RiskLevel = RiskLevel.MEDIUM,
    // AI Analysis and Approval
    val analysisReport: String? = null, // Claude's detailed market analysis and strategy explanation
    val approvalStatus: ApprovalStatus = ApprovalStatus.APPROVED, // For AI-generated strategies
    val source: StrategySource = StrategySource.USER, // Who created the strategy
    // Trailing stop-loss settings - exact decimal arithmetic
    val useTrailingStop: Boolean = false,
    @Deprecated("Use trailingStopPercentDecimal for exact calculations", ReplaceWith("trailingStopPercentDecimal"))
    val trailingStopPercent: Double = 5.0, // Move stop-loss when profit reaches this percent
    val trailingStopPercentDecimal: BigDecimal = trailingStopPercent.toBigDecimalMoney(),

    // Multi-timeframe analysis settings
    val useMultiTimeframe: Boolean = false,
    val primaryTimeframe: Int = 60, // Main timeframe in minutes (default: 1 hour)
    val confirmatoryTimeframes: List<Int> = listOf(15, 240), // Additional timeframes for confirmation (15m, 4h)

    // Volatility-adjusted stop-loss settings - exact decimal arithmetic
    val useVolatilityStops: Boolean = false,
    @Deprecated("Use atrMultiplierDecimal for exact calculations", ReplaceWith("atrMultiplierDecimal"))
    val atrMultiplier: Double = 2.0, // ATR multiplier for stop-loss distance (1.0-4.0)
    val atrMultiplierDecimal: BigDecimal = atrMultiplier.toBigDecimalMoney(),

    // Market regime filtering
    val useRegimeFilter: Boolean = false,
    val allowedRegimes: List<String> = listOf("TRENDING_BULLISH", "TRENDING_BEARISH"), // Which regimes to trade in

    // Phase 3C: Performance Tracking & Strategy Lineage - exact decimal arithmetic
    val metaAnalysisId: Long? = null, // Link to meta-analysis that created this strategy
    val sourceReportCount: Int = 0, // How many expert reports went into creating this

    @Deprecated("Use maxDrawdownDecimal for exact calculations", ReplaceWith("maxDrawdownDecimal"))
    val maxDrawdown: Double = 0.0, // Maximum drawdown percentage
    val maxDrawdownDecimal: BigDecimal = maxDrawdown.toBigDecimalMoney(),

    @Deprecated("Use avgWinAmountDecimal for exact calculations", ReplaceWith("avgWinAmountDecimal"))
    val avgWinAmount: Double = 0.0, // Average winning trade amount
    val avgWinAmountDecimal: BigDecimal = avgWinAmount.toBigDecimalMoney(),

    @Deprecated("Use avgLossAmountDecimal for exact calculations", ReplaceWith("avgLossAmountDecimal"))
    val avgLossAmount: Double = 0.0, // Average losing trade amount
    val avgLossAmountDecimal: BigDecimal = avgLossAmount.toBigDecimalMoney(),

    @Deprecated("Use profitFactorDecimal for exact calculations", ReplaceWith("profitFactorDecimal"))
    val profitFactor: Double = 0.0, // Total wins / Total losses
    val profitFactorDecimal: BigDecimal = profitFactor.toBigDecimalMoney(),

    @Deprecated("Use sharpeRatioDecimal for exact calculations", ReplaceWith("sharpeRatioDecimal"))
    val sharpeRatio: Double? = null, // Risk-adjusted return metric
    val sharpeRatioDecimal: BigDecimal? = sharpeRatio?.toBigDecimalMoney(),

    @Deprecated("Use largestWinDecimal for exact calculations", ReplaceWith("largestWinDecimal"))
    val largestWin: Double = 0.0, // Largest single winning trade
    val largestWinDecimal: BigDecimal = largestWin.toBigDecimalMoney(),

    @Deprecated("Use largestLossDecimal for exact calculations", ReplaceWith("largestLossDecimal"))
    val largestLoss: Double = 0.0, // Largest single losing trade
    val largestLossDecimal: BigDecimal = largestLoss.toBigDecimalMoney(),

    val currentStreak: Int = 0, // Current win/loss streak (positive = wins, negative = losses)
    val longestWinStreak: Int = 0, // Longest consecutive win streak
    val longestLossStreak: Int = 0, // Longest consecutive loss streak

    @Deprecated("Use performanceScoreDecimal for exact calculations", ReplaceWith("performanceScoreDecimal"))
    val performanceScore: Double = 0.0, // Composite performance metric (0.0-100.0)
    val performanceScoreDecimal: BigDecimal = performanceScore.toBigDecimalMoney(),

    val isTopPerformer: Boolean = false, // Flag for top 10% performing strategies

    @Deprecated("Use totalProfitPercentDecimal for exact calculations", ReplaceWith("totalProfitPercentDecimal"))
    val totalProfitPercent: Double = 0.0, // Total profit as percentage
    val totalProfitPercentDecimal: BigDecimal = totalProfitPercent.toBigDecimalMoney(),

    // Kraken Order Execution Settings
    val postOnly: Boolean = false, // Only place maker orders (lower fees, may be rejected)
    val timeInForce: TimeInForce = TimeInForce.GTC, // Order time-in-force behavior
    val orderExpiration: Long? = null, // Expiration timestamp for GTD orders (milliseconds)
    val feeAsset: FeeAsset = FeeAsset.QUOTE, // Prefer fees in base or quote currency
    val maxLeverage: Int? = null, // Maximum leverage to use (null = no leverage, 2-5 for margin)
    val useNativeTrailingStop: Boolean = false, // Use Kraken's native trailing stops vs app-based
    val volumeInQuote: Boolean = false // For market buy orders: specify volume in quote currency
)

enum class ApprovalStatus {
    PENDING,   // Waiting for user approval
    APPROVED,  // Approved and can be activated
    REJECTED;  // User rejected the strategy

    override fun toString(): String = name
}

enum class StrategySource {
    USER,      // Created by user manually
    AI_CLAUDE; // Generated by Claude AI

    override fun toString(): String = name
}

@Serializable
enum class RiskLevel {
    LOW, MEDIUM, HIGH;

    override fun toString(): String = name

    companion object {
        fun fromString(value: String): RiskLevel {
            return when (value.uppercase()) {
                "LOW" -> LOW
                "MEDIUM" -> MEDIUM
                "HIGH" -> HIGH
                else -> MEDIUM
            }
        }
    }
}

/**
 * Time-in-force for orders
 */
@Serializable
enum class TimeInForce {
    GTC,  // Good Till Canceled - remains until filled or cancelled (default)
    IOC,  // Immediate Or Cancel - fill immediately, cancel remainder
    GTD;  // Good Till Date - expires at specified time (requires orderExpiration)

    override fun toString(): String = name

    fun toKrakenFormat(): String = name.lowercase()

    companion object {
        fun fromString(value: String): TimeInForce {
            return when (value.uppercase()) {
                "GTC" -> GTC
                "IOC" -> IOC
                "GTD" -> GTD
                else -> GTC
            }
        }
    }
}

/**
 * Fee currency preference for orders
 */
@Serializable
enum class FeeAsset {
    BASE,   // Fee charged in base currency (e.g., BTC for XBTUSD)
    QUOTE;  // Fee charged in quote currency (e.g., USD for XBTUSD) - default

    override fun toString(): String = name

    fun toKrakenFlag(): String = when (this) {
        BASE -> "fcib"
        QUOTE -> "fciq"
    }

    companion object {
        fun fromString(value: String): FeeAsset {
            return when (value.uppercase()) {
                "BASE" -> BASE
                "QUOTE" -> QUOTE
                else -> QUOTE
            }
        }
    }
}

/**
 * Signal generated by a strategy
 *
 * BigDecimal Migration (Phase 2.9):
 * All monetary fields use BigDecimal for exact calculations
 */
data class TradeSignal(
    val strategyId: String,
    val pair: String,
    val action: TradeAction,

    @Deprecated("Use confidenceDecimal for exact calculations", ReplaceWith("confidenceDecimal"))
    val confidence: Double, // 0.0 to 1.0
    val confidenceDecimal: BigDecimal = confidence.toBigDecimalMoney(),

    @Deprecated("Use targetPriceDecimal for exact calculations", ReplaceWith("targetPriceDecimal"))
    val targetPrice: Double? = null,
    val targetPriceDecimal: BigDecimal? = targetPrice?.toBigDecimalMoney(),

    @Deprecated("Use suggestedVolumeDecimal for exact calculations", ReplaceWith("suggestedVolumeDecimal"))
    val suggestedVolume: Double,
    val suggestedVolumeDecimal: BigDecimal = suggestedVolume.toBigDecimalMoney(),

    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class TradeAction {
    BUY, SELL, HOLD
}

/**
 * Trading mode for strategies
 */
@Serializable
enum class TradingMode {
    INACTIVE,  // Strategy saved but not running
    PAPER,     // Simulated trading with fake money
    LIVE;      // Real trading with real money

    override fun toString(): String = name

    companion object {
        fun fromString(value: String): TradingMode {
            return when (value.uppercase()) {
                "INACTIVE" -> INACTIVE
                "PAPER" -> PAPER
                "LIVE" -> LIVE
                else -> INACTIVE
            }
        }
    }
}
