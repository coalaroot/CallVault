/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.services.recording

import android.content.Context
import android.os.SystemClock
import com.baba.callvault.R
import com.baba.callvault.data.AppPreferences
import com.baba.callvault.data.calls.PhoneCall
import com.baba.callvault.data.calls.PhoneCallMatch
import com.baba.callvault.data.calls.PhoneCallReader
import com.baba.callvault.data.calls.SimLabels
import com.baba.callvault.data.recordings.RecordingsRepository
import com.baba.callvault.services.call.ActiveCallSim
import com.baba.callvault.utils.AppLogger

/**
 * Applies [RecordSimPolicy] when a carrier call goes off-hook, and drops a finished recording
 * whose call-log SIM is not the chosen one.
 *
 * The SIM is the same one the recordings list prints: [SimLabels.Resolver] on the call log's
 * phone account. The row has to be *this* call ([PhoneCallMatch.justEnded]), not merely the
 * nearest call inside two minutes — that window still contains the previous call while this
 * call's row is missing, and the list only looks right once both rows exist.
 */
internal object RecordSimGate {

    private const val TAG = "CV:RecordSim"

    /** Long enough for a call log that is written a few seconds after the call, not after the next one. */
    private const val ATTEMPTS = 16
    private const val RETRY_MS = 500L

    fun onCallStarting(context: Context, hintSlot: Int?): RecordSimPolicy.Decision {
        val target = AppPreferences(context).getRecordAutoSimSlot()
        val active = if (target == null) null else ActiveCallSim.slot(context, hintSlot)
        val decision = RecordSimPolicy.decide(target, active)
        if (target != null) {
            val sims = runCatching {
                SimLabels.activeSims(context, context.getString(R.string.sim_label_word))
                    .joinToString { "slot ${it.slot}=${it.label}" }
            }.getOrDefault("?")
            AppLogger.i(
                TAG,
                "off-hook: stored slot=$target ($sims), seen slot=${active ?: "unknown"}, broadcast hint=${hintSlot ?: "none"} → $decision",
            )
        }
        return decision
    }

    /** True when judging the finished file may wait for the call log. Off unless a SIM was chosen. */
    fun needsEndCheck(context: Context): Boolean = AppPreferences(context).getRecordAutoSimSlot() != null

    /**
     * Delete this finished carrier recording because the call log says it was not the chosen SIM.
     * Unknown stays. No-op when automatic recording is not limited to one SIM.
     */
    fun shouldDiscard(context: Context, displayName: String): Boolean {
        val target = AppPreferences(context).getRecordAutoSimSlot() ?: return false
        if (!RecordingsRepository.isCarrierCall(displayName)) {
            AppLogger.i(TAG, "end: '$displayName' is not a phone-call recording; not applying the SIM rule")
            return false
        }
        val startedAt = RecordingsRepository.parseName(displayName).startedAtMillis
        if (startedAt == null) {
            AppLogger.i(TAG, "end: '$displayName' has no start time; keeping it")
            return false
        }
        val simWord = context.getString(R.string.sim_label_word)
        val resolver = SimLabels.Resolver(context, simWord)
        AppLogger.i(TAG, "end: stored slot=$target, recording start=$startedAt, waiting for this call's row")
        repeat(ATTEMPTS) { attempt ->
            val calls = PhoneCallReader.since(context, startedAt - PhoneCallMatch.TOLERANCE_MS)
            val fresh = PhoneCallMatch.justEnded(calls, startedAt)
            val wide = PhoneCallMatch.nearest(calls, startedAt)
            val freshSim = fresh?.let { resolver.simFor(it.accountComponent, it.accountId) }
            AppLogger.i(
                TAG,
                "end attempt ${attempt + 1}/$ATTEMPTS: rows=${calls.size} " +
                    "thisCall=${describe(fresh, startedAt, freshSim)} " +
                    "twoMinuteMatch=${describe(wide, startedAt, wide?.let { resolver.simFor(it.accountComponent, it.accountId) })}",
            )
            val slot = freshSim?.slot
            if (slot != null) {
                val discard = slot != target
                AppLogger.i(
                    TAG,
                    "end decision ${if (discard) "REMOVE" else "KEEP"}: " +
                        "label '${freshSim.label}' slot $slot, chosen slot $target",
                )
                return discard
            }
            if (attempt < ATTEMPTS - 1) SystemClock.sleep(RETRY_MS)
        }
        AppLogger.i(TAG, "end: no row for this call after $ATTEMPTS tries; keeping '$displayName'")
        return false
    }

    private fun describe(call: PhoneCall?, recordingStartedAt: Long, sim: SimLabels.Sim?): String {
        if (call == null) return "none"
        val delta = call.startedAt - recordingStartedAt
        val account = if (call.accountId.isNullOrBlank()) "no-account" else "account"
        val simText = sim?.let { "slot ${it.slot} '${it.label}'" } ?: "sim-unresolved"
        return "Δ${delta}ms dur=${call.durationSeconds}s $account $simText"
    }
}
