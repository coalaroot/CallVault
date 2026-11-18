/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.data.calls

/** All SIMs, one slot, or none. Stored as "all", "none", or a 1-based slot. */
sealed class SimChoice {
    data object All : SimChoice()
    data object None : SimChoice()
    data class Only(val slot: Int) : SimChoice()

    val key: String
        get() = when (this) {
            All -> "all"
            None -> "none"
            is Only -> slot.toString()
        }

    companion object {
        fun fromKey(raw: String?): SimChoice = when (raw) {
            null, "", "all" -> All
            "none" -> None
            else -> raw.toIntOrNull()?.takeIf { it > 0 }?.let { Only(it) } ?: All
        }
    }
}
