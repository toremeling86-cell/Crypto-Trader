package com.cryptotrader.data.mapper

import com.cryptotrader.data.local.entities.TradeEntity
import com.cryptotrader.domain.model.Trade
import com.cryptotrader.domain.model.TradeStatus
import com.cryptotrader.domain.model.TradeType

/**
 * Extension function to convert TradeEntity to Trade domain model
 */
fun TradeEntity.toDomain() = Trade(
    id = id,
    orderId = orderId,
    pair = pair,
    type = TradeType.fromString(type),
    price = price,
    volume = volume,
    cost = cost,
    fee = fee,
    timestamp = timestamp,
    strategyId = strategyId,
    status = TradeStatus.fromString(status),
    profit = profit,
    notes = notes,
    entryTime = null, // Not stored in entity, can be populated if needed
    exitTime = null   // Not stored in entity, can be populated if needed
)
