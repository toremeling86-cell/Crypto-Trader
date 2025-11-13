package com.cryptotrader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.cryptotrader.data.local.dao.*
import com.cryptotrader.data.local.entities.*

@Database(
    entities = [
        TradeEntity::class,
        StrategyEntity::class
    ],
    version = 3, // Incremented version for AI strategy fields
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    // ApiKeyDao removed - API keys are now stored only in EncryptedSharedPreferences
    abstract fun tradeDao(): TradeDao
    abstract fun strategyDao(): StrategyDao

    companion object {
        const val DATABASE_NAME = "crypto_trader_db"
    }
}
