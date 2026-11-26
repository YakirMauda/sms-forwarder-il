package com.qrcommunication.smsforwarder.di

import com.qrcommunication.smsforwarder.domain.rules.MatcherRegistry
import com.qrcommunication.smsforwarder.domain.rules.RuleEvaluator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Wires the pure-Kotlin rule engine (which has no DI annotations of its own). */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    @Singleton
    fun provideRuleEvaluator(): RuleEvaluator = RuleEvaluator(MatcherRegistry.DEFAULT)
}
