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
 * [targetSlot] null is every SIM: today's behaviour. A known other slot is not recorded at all.
 * An unknown slot is still recorded, and removed afterwards if the call log names the other SIM,
 * because a business call must not be dropped just because the slot could not be read in time.
 */
internal object RecordSimPolicy {

    enum class Decision {
        /** Record (or offer) exactly as the other rules say. */
        RECORD,

        /** Do not record and do not offer a Record button. */
        SKIP,

        /** Record now; drop the file at the end if the call log says it was the other SIM. */
        RECORD_UNCONFIRMED,
    }

    fun decide(targetSlot: Int?, activeSlot: Int?): Decision = when {
        targetSlot == null -> Decision.RECORD
        activeSlot == null -> Decision.RECORD_UNCONFIRMED
        activeSlot == targetSlot -> Decision.RECORD
        else -> Decision.SKIP
    }
}
