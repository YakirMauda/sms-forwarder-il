package com.qrcommunication.smsforwarder.di

import com.qrcommunication.smsforwarder.service.PreferenceSimSelector
import com.qrcommunication.smsforwarder.service.SimSelector
import com.qrcommunication.smsforwarder.service.sender.MessageSender
import com.qrcommunication.smsforwarder.service.sender.SmsMessageSender
import com.qrcommunication.smsforwarder.service.sender.TelegramSender
import com.qrcommunication.smsforwarder.service.sender.WebhookSender
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/** Registers one MessageSender per channel; DestinationDispatcher receives them as a Set. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SenderModule {

    @Binds @IntoSet
    abstract fun bindSms(impl: SmsMessageSender): MessageSender

    @Binds @IntoSet
    abstract fun bindTelegram(impl: TelegramSender): MessageSender

    @Binds @IntoSet
    abstract fun bindWebhook(impl: WebhookSender): MessageSender

    @Binds @Singleton
    abstract fun bindSimSelector(impl: PreferenceSimSelector): SimSelector
}
