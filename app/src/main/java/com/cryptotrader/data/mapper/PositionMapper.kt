package com.cryptotrader.data.mapper

import com.cryptotrader.data.local.entities.PositionEntity
import com.cryptotrader.domain.model.Position
import com.cryptotrader.domain.model.PositionSide
import com.cryptotrader.domain.model.PositionStatus
import com.cryptotrader.utils.toBigDecimalMoney

/**
 * Extension function to convert PositionEntity to Position domain model
 *
 * BigDecimal Migration:
 * - Prioritizes BigDecimal fields if available (exact calculations)
 * - Falls back to Double fields for backward compatibility
 */
fun PositionEntity.toDomain() = Position(
    id = id,
    strategyId = strategyId,
    pair = pair,
    side = PositionSide.fromString(type),

    // Quantity - prefer BigDecimal, fallback to Double
    quantity = quantityDecimal?.toDouble() ?: quantity,
    quantityDecimal = quantityDecimal ?: quantity.toBigDecimalMoney(),

    // Entry price - prefer BigDecimal, fallback to Double
    entryPrice = entryPriceDecimal?.toDouble() ?: entryPrice,
    entryPriceDecimal = entryPriceDecimal ?: entryPrice.toBigDecimalMoney(),

    entryTradeId = entryTradeId,
    openedAt = openedAt,

    // Risk management - prefer BigDecimal, fallback to Double
    stopLossPrice = stopLossPriceDecimal?.toDouble() ?: stopLossPrice,
    stopLossPriceDecimal = stopLossPriceDecimal ?: stopLossPrice?.toBigDecimalMoney(),

    takeProfitPrice = takeProfitPriceDecimal?.toDouble() ?: takeProfitPrice,
    takeProfitPriceDecimal = takeProfitPriceDecimal ?: takeProfitPrice?.toBigDecimalMoney(),

    stopLossOrderId = stopLossOrderId,
    takeProfitOrderId = takeProfitOrderId,

    // Exit - prefer BigDecimal, fallback to Double
    exitPrice = exitPriceDecimal?.toDouble() ?: exitPrice,
    exitPriceDecimal = exitPriceDecimal ?: exitPrice?.toBigDecimalMoney(),

    exitTradeId = exitTradeId,
    closedAt = closedAt,
    closeReason = closeReason,

    // P&L - prefer BigDecimal, fallback to Double
    unrealizedPnL = unrealizedPnLDecimal?.toDouble() ?: unrealizedPnL,
    unrealizedPnLDecimal = unrealizedPnLDecimal ?: unrealizedPnL.toBigDecimalMoney(),

    unrealizedPnLPercent = unrealizedPnLPercentDecimal?.toDouble() ?: unrealizedPnLPercent,
    unrealizedPnLPercentDecimal = unrealizedPnLPercentDecimal ?: unrealizedPnLPercent.toBigDecimalMoney(),

    realizedPnL = realizedPnLDecimal?.toDouble() ?: realizedPnL,
    realizedPnLDecimal = realizedPnLDecimal ?: realizedPnL?.toBigDecimalMoney(),

    realizedPnLPercent = realizedPnLPercentDecimal?.toDouble() ?: realizedPnLPercent,
    realizedPnLPercentDecimal = realizedPnLPercentDecimal ?: realizedPnLPercent?.toBigDecimalMoney(),

    status = PositionStatus.fromString(status),
    lastUpdated = lastUpdated
)

/**
 * Extension function to convert Position domain model to PositionEntity
 *
 * BigDecimal Migration:
 * - Stores both BigDecimal and Double values
 * - BigDecimal is the source of truth
 * - Double kept for backward compatibility
 */
fun Position.toEntity() = PositionEntity(
    id = id,
    strategyId = strategyId,
    pair = pair,
    type = side.toString(),

    // Store both BigDecimal (source of truth) and Double (backward compatibility)
    quantity = quantityDecimal.toDouble(),
    quantityDecimal = quantityDecimal,

    entryPrice = entryPriceDecimal.toDouble(),
    entryPriceDecimal = entryPriceDecimal,

    entryTradeId = entryTradeId,
    openedAt = openedAt,

    stopLossPrice = stopLossPriceDecimal?.toDouble(),
    stopLossPriceDecimal = stopLossPriceDecimal,

    takeProfitPrice = takeProfitPriceDecimal?.toDouble(),
    takeProfitPriceDecimal = takeProfitPriceDecimal,

    stopLossOrderId = stopLossOrderId,
    takeProfitOrderId = takeProfitOrderId,

    exitPrice = exitPriceDecimal?.toDouble(),
    exitPriceDecimal = exitPriceDecimal,

    exitTradeId = exitTradeId,
    closedAt = closedAt,
    closeReason = closeReason,

    unrealizedPnL = unrealizedPnLDecimal.toDouble(),
    unrealizedPnLDecimal = unrealizedPnLDecimal,

    unrealizedPnLPercent = unrealizedPnLPercentDecimal.toDouble(),
    unrealizedPnLPercentDecimal = unrealizedPnLPercentDecimal,

    realizedPnL = realizedPnLDecimal?.toDouble(),
    realizedPnLDecimal = realizedPnLDecimal,

    realizedPnLPercent = realizedPnLPercentDecimal?.toDouble(),
    realizedPnLPercentDecimal = realizedPnLPercentDecimal,

    status = status.toString(),
    lastUpdated = lastUpdated
)
