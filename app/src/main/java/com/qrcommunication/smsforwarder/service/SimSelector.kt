package com.qrcommunication.smsforwarder.service

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.SubscriptionManager
import android.util.Log
import com.qrcommunication.smsforwarder.data.preferences.PreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Decides which SIM sends a forwarded SMS. Separate from sending (Interface Segregation). */
interface SimSelector {
    /** @return the subscription id to send from, or null for the phone's default SMS SIM. */
    fun subscriptionIdFor(incomingSimSlot: Int): Int?
}

/** Pure part of the SIM choice, unit-testable without Android. */
object SimChoice {
    const val DEFAULT = -1
    const val SAME_AS_INCOMING = -2

    /** Setting -> SIM slot to send from (null = default SIM). */
    fun slotFor(setting: Int, incomingSimSlot: Int): Int? = when {
        setting >= 0 -> setting
        setting == SAME_AS_INCOMING && incomingSimSlot >= 0 -> incomingSimSlot
        else -> null
    }
}

@Singleton
class PreferenceSimSelector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager,
) : SimSelector {

    @SuppressLint("MissingPermission")
    override fun subscriptionIdFor(incomingSimSlot: Int): Int? {
        val slot = SimChoice.slotFor(preferencesManager.selectedSimSlot, incomingSimSlot) ?: return null
        return try {
            context.getSystemService(SubscriptionManager::class.java)
                ?.getActiveSubscriptionInfoForSimSlotIndex(slot)
                ?.subscriptionId
        } catch (e: SecurityException) {
            Log.w("SimSelector", "READ_PHONE_STATE missing, using default SIM", e)
            null
        }
    }
}
