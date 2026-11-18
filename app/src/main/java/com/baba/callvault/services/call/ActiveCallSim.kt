/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.services.call

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.baba.callvault.system.permissions.PermissionChecks
import com.baba.callvault.utils.AppLogger

/**
 * Which SIM a call is on while it is still in progress.
 *
 * The phone-state broadcast does not document a SIM, and the call log is written only after the
 * call ends. Each active subscription has its own [TelephonyManager] via
 * [TelephonyManager.createForSubscriptionId](https://developer.android.com/reference/android/telephony/TelephonyManager#createForSubscriptionId(int));
 * [TelephonyManager.getCallState] on that instance is that subscription's state. When exactly one
 * subscription is off-hook, that is the call's SIM.
 *
 * [hint] is a slot read from broadcast extras some builds add (`slot`, or a subscription id). It
 * is used only when the per-subscription query cannot tell.
 */
internal object ActiveCallSim {

    private const val TAG = "CV:RecordSim"

    @SuppressLint("MissingPermission") // checked just below
    fun slot(context: Context, hint: Int? = null): Int? {
        val queried = query(context)
        val chosen = queried ?: hint?.takeIf { it > 0 }
        AppLogger.i(TAG, "live SIM query slot=${queried ?: "none"}, broadcast hint=${hint ?: "none"} → ${chosen ?: "unknown"}")
        return chosen
    }

    /** A 1-based slot from extras some PHONE_STATE broadcasts carry, or null. */
    fun hintFrom(context: Context, intent: Intent): Int? {
        val phoneId = intent.getIntExtra("slot", -1)
        if (phoneId >= 0) return phoneId + 1
        val subId = listOf(
            intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1),
            intent.getIntExtra("subscription", -1),
        ).firstOrNull { it >= 0 } ?: return null
        if (!PermissionChecks.hasPhoneStatePermission(context)) return null
        val slot = runCatching {
            context.getSystemService(SubscriptionManager::class.java)
                ?.getActiveSubscriptionInfo(subId)?.simSlotIndex
        }.getOrNull()
        return slot?.takeIf { it >= 0 }?.plus(1)
    }

    @SuppressLint("MissingPermission", "DEPRECATION") // permission checked; getCallState is the per-sub query
    private fun query(context: Context): Int? {
        if (!PermissionChecks.hasPhoneStatePermission(context)) return null
        val infos = runCatching {
            context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList
        }.getOrNull().orEmpty()
        if (infos.size < 2) {
            AppLogger.i(TAG, "live SIM query: ${infos.size} active subscription(s); cannot tell SIMs apart")
            return null
        }
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return null
        val active = infos.mapNotNull { info ->
            val state = runCatching { tm.createForSubscriptionId(info.subscriptionId).callState }.getOrNull()
            val slot = info.simSlotIndex.takeIf { it >= 0 }?.plus(1)
            AppLogger.i(
                TAG,
                "subscription id=${info.subscriptionId} slot=${slot ?: "?"} " +
                    "name='${info.displayName}' state=${stateName(state)}",
            )
            if (state == null || state == TelephonyManager.CALL_STATE_IDLE) null else info to state
        }
        val offhook = active.filter { it.second == TelephonyManager.CALL_STATE_OFFHOOK }
        val chosen = when {
            offhook.size == 1 -> offhook[0].first
            offhook.isEmpty() && active.size == 1 -> active[0].first
            else -> null
        }
        val slot = chosen?.simSlotIndex?.takeIf { it >= 0 }?.plus(1)
        if (slot == null) {
            AppLogger.i(TAG, "live SIM query: ${offhook.size} off-hook, ${active.size} not idle; no single SIM")
        }
        return slot
    }

    private fun stateName(state: Int?): String = when (state) {
        null -> "unreadable"
        TelephonyManager.CALL_STATE_IDLE -> "idle"
        TelephonyManager.CALL_STATE_RINGING -> "ringing"
        TelephonyManager.CALL_STATE_OFFHOOK -> "off-hook"
        else -> "state$state"
    }
}
