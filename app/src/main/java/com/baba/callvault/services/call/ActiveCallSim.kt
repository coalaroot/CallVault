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

    @SuppressLint("MissingPermission") // checked just below
    fun slot(context: Context, hint: Int? = null): Int? {
        query(context)?.let { return it }
        return hint?.takeIf { it > 0 }
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
        if (infos.size < 2) return null
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return null
        val active = infos.mapNotNull { info ->
            val state = runCatching { tm.createForSubscriptionId(info.subscriptionId).callState }.getOrNull()
                ?: return@mapNotNull null
            if (state == TelephonyManager.CALL_STATE_IDLE) null else info to state
        }
        val offhook = active.filter { it.second == TelephonyManager.CALL_STATE_OFFHOOK }
        val chosen = when {
            offhook.size == 1 -> offhook[0].first
            offhook.isEmpty() && active.size == 1 -> active[0].first
            else -> null
        }
        return chosen?.simSlotIndex?.takeIf { it >= 0 }?.plus(1)
    }
}
