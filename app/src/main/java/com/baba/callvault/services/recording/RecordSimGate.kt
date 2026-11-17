/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.services.recording

import android.content.Context
import com.baba.callvault.data.AppPreferences
import com.baba.callvault.data.recordings.RecordingsRepository
import com.baba.callvault.services.call.ActiveCallSim
import com.baba.callvault.transcription.AfterCallSimGate
import com.baba.callvault.utils.AppLogger

/**
 * Applies [RecordSimPolicy] when a carrier call goes off-hook, and drops a finished recording
 * whose call-log SIM is not the chosen one.
 *
 * The second check is the fallback for a call whose SIM could not be read at the start, and it
 * also removes a recording the start-time guess got wrong. A SIM that is still unknown after the
 * call is kept.
 */
internal object RecordSimGate {

    private const val TAG = "CV:RecordSim"

    fun onCallStarting(context: Context, hintSlot: Int?): RecordSimPolicy.Decision {
        val target = AppPreferences(context).getRecordAutoSimSlot()
        val active = if (target == null) null else ActiveCallSim.slot(context, hintSlot)
        val decision = RecordSimPolicy.decide(target, active)
        if (target != null) {
            AppLogger.i(TAG, "Call SIM ${active ?: "unknown"}, automatic recording limited to SIM $target → $decision")
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
        val slot = AfterCallSimGate.resolvedSlot(context, displayName) ?: run {
            if (RecordingsRepository.isCarrierCall(displayName)) {
                AppLogger.i(TAG, "SIM for '$displayName' still unknown after the call; keeping the recording")
            }
            return false
        }
        val discard = slot != target
        if (discard) {
            AppLogger.i(TAG, "Removing '$displayName': SIM $slot is not the one selected for automatic recording")
        }
        return discard
    }
}
