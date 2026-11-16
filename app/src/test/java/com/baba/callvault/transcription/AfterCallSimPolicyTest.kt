/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.transcription

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baba.callvault.data.AppPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Which finished call is transcribed, and which recording is deleted, when after-call
 * transcription is limited to one SIM. An unknown SIM must keep today's behaviour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AfterCallSimPolicyTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun reset() {
        val prefs = AppPreferences(context)
        prefs.setTranscriptionAfterCallSimSlot(null)
        prefs.setTranscriptionOtherSim(OtherSimAction.KEEP)
    }

    @Test
    fun defaults_are_every_sim_and_keep() {
        val prefs = AppPreferences(context)
        assertNull(prefs.getTranscriptionAfterCallSimSlot())
        assertEquals(OtherSimAction.KEEP, prefs.getTranscriptionOtherSim())
        assertEquals("all", AppPreferences.DefaultsValue.TRANSCRIPTION_AFTER_CALL_SIM)
    }

    @Test
    fun a_stored_slot_round_trips_and_all_clears_it() {
        val prefs = AppPreferences(context)
        prefs.setTranscriptionAfterCallSimSlot(2)
        prefs.setTranscriptionOtherSim(OtherSimAction.DELETE)
        assertEquals(2, AppPreferences(context).getTranscriptionAfterCallSimSlot())
        assertEquals(OtherSimAction.DELETE, AppPreferences(context).getTranscriptionOtherSim())
        prefs.setTranscriptionAfterCallSimSlot(null)
        assertNull(prefs.getTranscriptionAfterCallSimSlot())
        assertEquals(OtherSimAction.KEEP, OtherSimAction.fromKey("nope"))
    }

    @Test
    fun every_sim_transcribes_and_keeps_whatever_the_call_was() {
        listOf(null, 1, 2).forEach { resolved ->
            val decision = AfterCallSimPolicy.decide(targetSlot = null, otherDeletes = true, resolvedSlot = resolved)
            assertTrue(decision.transcribe)
            assertFalse(decision.deleteRecording)
        }
    }

    @Test
    fun an_unknown_sim_is_transcribed_and_kept() {
        val decision = AfterCallSimPolicy.decide(targetSlot = 1, otherDeletes = true, resolvedSlot = null)
        assertTrue(decision.transcribe)
        assertFalse("must not delete a call whose SIM could not be resolved", decision.deleteRecording)
    }

    @Test
    fun the_chosen_sim_is_transcribed_and_kept() {
        val decision = AfterCallSimPolicy.decide(targetSlot = 1, otherDeletes = true, resolvedSlot = 1)
        assertTrue(decision.transcribe)
        assertFalse(decision.deleteRecording)
    }

    @Test
    fun the_other_sim_is_not_transcribed_and_kept_unless_delete_was_chosen() {
        val keep = AfterCallSimPolicy.decide(targetSlot = 1, otherDeletes = false, resolvedSlot = 2)
        assertFalse(keep.transcribe)
        assertFalse(keep.deleteRecording)

        val delete = AfterCallSimPolicy.decide(targetSlot = 2, otherDeletes = true, resolvedSlot = 1)
        assertFalse(delete.transcribe)
        assertTrue(delete.deleteRecording)
    }
}
