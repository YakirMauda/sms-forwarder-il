package com.qrcommunication.smsforwarder.di

import com.qrcommunication.smsforwarder.service.RetryScheduler
import com.qrcommunication.smsforwarder.work.WorkManagerRetryScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkModule {

    @Binds
    @Singleton
    abstract fun bindRetryScheduler(impl: WorkManagerRetryScheduler): RetryScheduler
}
