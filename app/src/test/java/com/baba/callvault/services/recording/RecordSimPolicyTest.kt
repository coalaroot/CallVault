/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.services.recording

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordSimPolicyTest {

    @Test
    fun every_sim_records_as_before() {
        assertEquals(RecordSimPolicy.Decision.RECORD, RecordSimPolicy.decide(null, null))
        assertEquals(RecordSimPolicy.Decision.RECORD, RecordSimPolicy.decide(null, 2))
    }

    @Test
    fun the_chosen_sim_is_recorded_and_announced() {
        val decision = RecordSimPolicy.decide(1, 1)
        assertEquals(RecordSimPolicy.Decision.RECORD, decision)
        assertEquals(true, RecordSimPolicy.announcesStart(decision))
    }

    @Test
    fun the_other_sim_is_offered_for_a_manual_recording_and_not_announced() {
        val decision = RecordSimPolicy.decide(1, 2)
        assertEquals(RecordSimPolicy.Decision.OFFER, decision)
        assertEquals(false, RecordSimPolicy.announcesStart(decision))
    }

    @Test
    fun an_unknown_sim_is_recorded_quietly() {
        val decision = RecordSimPolicy.decide(2, null)
        assertEquals(RecordSimPolicy.Decision.RECORD_QUIET, decision)
        assertEquals(false, RecordSimPolicy.announcesStart(decision))
    }

    @Test
    fun an_automatic_recording_on_the_other_sim_is_removed() {
        assertEquals(true, RecordSimPolicy.discards(manual = false, resolvedSlot = 1, targetSlot = 2))
    }

    @Test
    fun a_manual_recording_on_the_other_sim_is_kept() {
        assertEquals(false, RecordSimPolicy.discards(manual = true, resolvedSlot = 1, targetSlot = 2))
    }

    @Test
    fun a_quiet_recording_that_turns_out_to_be_the_chosen_sim_is_kept() {
        assertEquals(false, RecordSimPolicy.discards(manual = false, resolvedSlot = 2, targetSlot = 2))
    }

    @Test
    fun an_unknown_sim_after_the_call_is_kept_and_all_sims_never_discards() {
        assertEquals(false, RecordSimPolicy.discards(manual = false, resolvedSlot = null, targetSlot = 2))
        assertEquals(false, RecordSimPolicy.discards(manual = false, resolvedSlot = 1, targetSlot = null))
    }
}
