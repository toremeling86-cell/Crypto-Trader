package com.cryptotrader.di

import com.cryptotrader.data.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Learning repository bindings
 *
 * Provides dependency injection bindings for all Learning-related repositories
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LearningRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLearningRepository(
        impl: LearningRepositoryImpl
    ): LearningRepository

    @Binds
    @Singleton
    abstract fun bindKnowledgeBaseRepository(
        impl: KnowledgeBaseRepositoryImpl
    ): KnowledgeBaseRepository

    @Binds
    @Singleton
    abstract fun bindProgressRepository(
        impl: ProgressRepositoryImpl
    ): ProgressRepository
}
