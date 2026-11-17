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
    fun the_chosen_sim_is_recorded_and_the_other_is_not() {
        assertEquals(RecordSimPolicy.Decision.RECORD, RecordSimPolicy.decide(1, 1))
        assertEquals(RecordSimPolicy.Decision.SKIP, RecordSimPolicy.decide(1, 2))
    }

    @Test
    fun an_unknown_sim_is_recorded_and_checked_afterwards() {
        assertEquals(RecordSimPolicy.Decision.RECORD_UNCONFIRMED, RecordSimPolicy.decide(2, null))
    }
}
