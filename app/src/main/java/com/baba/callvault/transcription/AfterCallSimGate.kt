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
import com.baba.callvault.data.calls.SimLabels
import com.baba.callvault.data.recordings.RecordingsRepository
import com.baba.callvault.utils.AppLogger

/**
 * Applies [AfterCallSimPolicy] to one finished recording.
 *
 * The SIM comes from the call log, the same way the opt-in SIM label does
 * ([SimLabels.slotForCall]), and it is read whether or not that label is turned on. Android writes
 * the call-log row only after the call ends
 * ([CallLog.Calls](https://developer.android.com/reference/android/provider/CallLog.Calls)), so a
 * decision that would delete waits briefly for the row. If it never appears, the call is
 * transcribed and kept.
 *
 * App calls are left alone: they are not in the call log, and a phone call at the same minute must
 * not lend them its SIM.
 */
object AfterCallSimGate {

    private const val TAG = "CV:AfterCallSim"

    /** Same budget as the post-call number lookup: a few tries, a short gap, then give up. */
    private const val ATTEMPTS = 4
    private const val RETRY_MS = 400L

    /**
     * True when resolving the SIM may delay the caller, because a call on the other SIM would be
     * deleted. The known-number path runs on the service thread otherwise; this is the signal to
     * move that path off it. False for every install that has not opted in, so that path is unchanged.
     */
    fun mayBlockForDelete(context: Context): Boolean {
        val prefs = AppPreferences(context)
        return prefs.getTranscriptionMode().transcribesOnCallEnd &&
            prefs.getTranscriptionAfterCallSimSlot() != null &&
            prefs.getTranscriptionOtherSim() == OtherSimAction.DELETE
    }

    /** Delete this recording instead of cataloguing it. False unless [mayBlockForDelete] is true. */
    fun shouldDelete(context: Context, displayName: String): Boolean {
        if (!mayBlockForDelete(context)) return false
        val decision = decide(context, displayName)
        if (decision.deleteRecording) {
            AppLogger.i(TAG, "Deleting '$displayName': it is not on the SIM selected for after-call transcription")
        }
        return decision.deleteRecording
    }

    /**
     * Skip after-call transcription for this recording. A no-op unless a single SIM is selected,
     * so [TranscriptionScheduler.transcribeAfterCallIfEnabled] stays unconditional for everyone else.
     */
    fun skipTranscription(context: Context, displayName: String): Boolean {
        val prefs = AppPreferences(context)
        if (!prefs.getTranscriptionMode().transcribesOnCallEnd || prefs.getTranscriptionAfterCallSimSlot() == null) {
            return false
        }
        val decision = decide(context, displayName)
        if (!decision.transcribe) {
            AppLogger.i(TAG, "Not transcribing '$displayName': it is not on the SIM selected for after-call transcription")
        }
        return !decision.transcribe
    }

    private fun decide(context: Context, displayName: String): AfterCallSimPolicy.Decision {
        val prefs = AppPreferences(context)
        val target = prefs.getTranscriptionAfterCallSimSlot()
        if (!RecordingsRepository.isCarrierCall(displayName)) {
            return AfterCallSimPolicy.Decision(transcribe = true, deleteRecording = false)
        }
        val slot = resolvedSlot(context, displayName)
        val decision = AfterCallSimPolicy.decide(
            targetSlot = target,
            otherDeletes = prefs.getTranscriptionOtherSim() == OtherSimAction.DELETE,
            resolvedSlot = slot,
        )
        if (slot == null && target != null) {
            AppLogger.i(TAG, "SIM for '$displayName' unknown; transcribing and keeping it")
        }
        return decision
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
            SimLabels.slotForCall(context, startedAt, simWord)?.let { return it }
            if (attempt < ATTEMPTS - 1) SystemClock.sleep(RETRY_MS)
        }
        return null
    }
}
