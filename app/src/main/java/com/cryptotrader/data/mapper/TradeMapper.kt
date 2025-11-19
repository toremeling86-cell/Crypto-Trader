package com.cryptotrader.data.mapper

import com.cryptotrader.data.local.entities.TradeEntity
import com.cryptotrader.domain.model.Trade
import com.cryptotrader.domain.model.TradeStatus
import com.cryptotrader.domain.model.TradeType
import com.cryptotrader.utils.toBigDecimalMoney

/**
 * Extension function to convert TradeEntity to Trade domain model
 *
 * BigDecimal Migration:
 * - Prioritizes BigDecimal fields if available (exact calculations)
 * - Falls back to Double fields for backward compatibility
 */
fun TradeEntity.toDomain() = Trade(
    id = id,
    orderId = orderId,
    pair = pair,
    type = TradeType.fromString(type),

    // Price - prefer BigDecimal, fallback to Double
    price = priceDecimal?.toDouble() ?: price,
    priceDecimal = priceDecimal ?: price.toBigDecimalMoney(),

    // Volume - prefer BigDecimal, fallback to Double
    volume = volumeDecimal?.toDouble() ?: volume,
    volumeDecimal = volumeDecimal ?: volume.toBigDecimalMoney(),

    // Cost - prefer BigDecimal, fallback to Double
    cost = costDecimal?.toDouble() ?: cost,
    costDecimal = costDecimal ?: cost.toBigDecimalMoney(),

    // Fee - prefer BigDecimal, fallback to Double
    fee = feeDecimal?.toDouble() ?: fee,
    feeDecimal = feeDecimal ?: fee.toBigDecimalMoney(),

    timestamp = timestamp,
    strategyId = strategyId,
    status = TradeStatus.fromString(status),

    // Profit - prefer BigDecimal, fallback to Double
    profit = profit,
    profitDecimal = profit?.toBigDecimalMoney(),

    notes = notes,
    entryTime = null, // Not stored in entity, can be populated if needed
    exitTime = null   // Not stored in entity, can be populated if needed
)

/**
 * Extension function to convert Trade domain model to TradeEntity
 *
 * BigDecimal Migration:
 * - Stores both BigDecimal and Double values
 * - BigDecimal is the source of truth
 * - Double kept for backward compatibility
 */
fun Trade.toEntity() = TradeEntity(
    id = id,
    orderId = orderId,
    pair = pair,
    type = type.toString(),

    // Store both BigDecimal (source of truth) and Double (backward compatibility)
    price = priceDecimal.toDouble(),
    priceDecimal = priceDecimal,

    volume = volumeDecimal.toDouble(),
    volumeDecimal = volumeDecimal,

    cost = costDecimal.toDouble(),
    costDecimal = costDecimal,

    fee = feeDecimal.toDouble(),
    feeDecimal = feeDecimal,

    timestamp = timestamp,
    strategyId = strategyId,
    status = status.toString(),
    profit = profitDecimal?.toDouble(),
    notes = notes
)
