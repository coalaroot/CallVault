/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.services.recording

import com.baba.callvault.data.calls.SimChoice

/**
 * Auto-record is All SIMs, one SIM, or none.
 *
 * A SIM that cannot be read at the start is treated as the chosen SIM: record and announce.
 * A known other SIM, or "none", is not started; the user can press Record.
 */
internal object RecordSimPolicy {

    enum class Decision {
        /** Start recording and announce it. */
        RECORD,

        /** Do not start. Show Record. */
        OFFER,
    }

    fun decide(choice: SimChoice, activeSlot: Int?): Decision = when (choice) {
        SimChoice.All -> Decision.RECORD
        SimChoice.None -> Decision.OFFER
        is SimChoice.Only -> when (activeSlot) {
            null, choice.slot -> Decision.RECORD
            else -> Decision.OFFER
        }
    }

    /** Toast and vibration. Only an automatic start. */
    fun announcesStart(decision: Decision): Boolean = decision == Decision.RECORD
}
