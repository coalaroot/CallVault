/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.transcription

import android.content.Context
import android.os.SystemClock
import com.baba.callvault.R
import com.baba.callvault.data.AppPreferences
import com.baba.callvault.data.calls.SimChoice
import com.baba.callvault.data.calls.SimLabels
import com.baba.callvault.data.recordings.RecordingsRepository
import com.baba.callvault.utils.AppLogger

/**
 * Applies [AfterCallSimPolicy] to one finished recording.
 *
 * The SIM comes from the call log, the same way the opt-in SIM label does, and it is read whether
 * or not that label is turned on. Android writes the call-log row only after the call ends
 * ([CallLog.Calls](https://developer.android.com/reference/android/provider/CallLog.Calls)), so this
 * waits briefly for the row of THIS call. A previous call is not used. If the row never appears,
 * the call is transcribed. A recording is never deleted from here.
 *
 * App calls are left alone: they are not in the call log, and a phone call at the same minute must
 * not lend them its SIM.
 */
object AfterCallSimGate {

    private const val TAG = "CV:AfterCallSim"

    /** A few tries while Android writes the call-log row, then give up. */
    private const val ATTEMPTS = 8
    private const val RETRY_MS = 500L

    /**
     * Skip after-call transcription for this recording.
     *
     * "None" skips every call. One SIM skips a call whose slot is known and different.
     * An unknown SIM is transcribed. This never deletes a recording.
     */
    fun skipTranscription(context: Context, displayName: String): Boolean {
        val prefs = AppPreferences(context)
        if (!prefs.getTranscriptionMode().transcribesOnCallEnd) return false
        val choice = prefs.getTranscriptionAutoSim()
        if (choice == SimChoice.All) return false
        if (choice == SimChoice.None) {
            AppLogger.i(TAG, "Not transcribing '$displayName': auto-transcribe is none")
            return true
        }
        val only = (choice as SimChoice.Only).slot
        val slot = resolvedSlot(context, displayName)
        if (slot == null) {
            AppLogger.i(TAG, "SIM for '$displayName' unknown; transcribing it")
            return false
        }
        val skip = slot != only
        if (skip) AppLogger.i(TAG, "Not transcribing '$displayName': SIM $slot, auto-transcribe is SIM $only")
        return skip
    }

    /**
     * The 1-based slot of the carrier call behind [displayName], or null. Waits briefly for the
     * call-log row. Not a carrier recording, or a SIM that cannot be told, is null.
     */
    fun resolvedSlot(context: Context, displayName: String): Int? {
        if (!RecordingsRepository.isCarrierCall(displayName)) return null
        val startedAt = RecordingsRepository.parseName(displayName).startedAtMillis ?: return null
        return slotWaiting(context, startedAt)
    }

    /** The 1-based slot, retrying while the call log is still being written. */
    private fun slotWaiting(context: Context, startedAt: Long): Int? {
        val simWord = context.getString(R.string.sim_label_word)
        repeat(ATTEMPTS) { attempt ->
            SimLabels.slotForJustEnded(context, startedAt, simWord)?.let { return it }
            if (attempt < ATTEMPTS - 1) SystemClock.sleep(RETRY_MS)
        }
        return null
    }
}
