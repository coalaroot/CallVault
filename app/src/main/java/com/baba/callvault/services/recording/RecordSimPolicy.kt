/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.services.recording

/**
 * Whether an automatic recording should start, given the SIM the user chose and the SIM this call
 * is on right now.
 *
 * [targetSlot] null is every SIM: today's behaviour. A known other slot is not started on its own;
 * the user can still press Record. An unknown slot is recorded without the "Recording started"
 * announcement, and removed afterwards if the call log names the other SIM — a manual recording
 * is never removed that way.
 */
internal object RecordSimPolicy {

    enum class Decision {
        /** Automatic recording, announced as usual. */
        RECORD,

        /** Do not start. Offer the Record button. */
        OFFER,

        /** Start, but do not announce it. May be removed after the call. */
        RECORD_QUIET,
    }

    fun decide(targetSlot: Int?, activeSlot: Int?): Decision = when {
        targetSlot == null -> Decision.RECORD
        activeSlot == null -> Decision.RECORD_QUIET
        activeSlot == targetSlot -> Decision.RECORD
        else -> Decision.OFFER
    }

    /** The "Recording started" toast and its vibration. Only a confirmed automatic start. */
    fun announcesStart(decision: Decision): Boolean = decision == Decision.RECORD

    /**
     * Whether a finished recording on [resolvedSlot] is removed because it is not [targetSlot].
     * A recording the user started is kept. An unknown slot is kept.
     */
    fun discards(manual: Boolean, resolvedSlot: Int?, targetSlot: Int?): Boolean {
        if (manual || targetSlot == null || resolvedSlot == null) return false
        return resolvedSlot != targetSlot
    }
}
