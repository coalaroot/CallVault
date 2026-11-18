/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.services.recording

import com.baba.callvault.data.calls.SimChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordSimPolicyTest {

    @Test
    fun all_sims_always_auto_records() {
        assertEquals(RecordSimPolicy.Decision.RECORD, RecordSimPolicy.decide(SimChoice.All, null))
        assertEquals(RecordSimPolicy.Decision.RECORD, RecordSimPolicy.decide(SimChoice.All, 2))
        assertTrue(RecordSimPolicy.announcesStart(RecordSimPolicy.Decision.RECORD))
    }

    @Test
    fun none_is_manual_only() {
        val decision = RecordSimPolicy.decide(SimChoice.None, 1)
        assertEquals(RecordSimPolicy.Decision.OFFER, decision)
        assertEquals(false, RecordSimPolicy.announcesStart(decision))
    }

    @Test
    fun the_chosen_sim_is_auto_recorded_and_the_other_is_manual() {
        val treetopia = SimChoice.Only(2)
        assertEquals(RecordSimPolicy.Decision.RECORD, RecordSimPolicy.decide(treetopia, 2))
        assertEquals(RecordSimPolicy.Decision.OFFER, RecordSimPolicy.decide(treetopia, 1))
    }

    @Test
    fun an_unknown_sim_is_treated_as_the_chosen_one() {
        assertEquals(RecordSimPolicy.Decision.RECORD, RecordSimPolicy.decide(SimChoice.Only(2), null))
    }
}
