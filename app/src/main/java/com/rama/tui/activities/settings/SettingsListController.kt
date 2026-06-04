package com.rama.tui.activities.settings

import android.widget.RadioGroup
import com.rama.tui.R
import com.rama.tui.activities.SettingsActivity
import com.rama.tui.managers.PrefsManager
import com.rama.tui.managers.PrefsManager.PrefKeys
import com.rama.tui.managers.PrefsManager.SortStyle

class SettingsListController(private val activity: SettingsActivity) {

    private val prefs get() = activity.prefs

    fun setup() {
        val sortGroup = activity.findViewById<RadioGroup>(R.id.list_sort)

        // Restore saved sort style
        val currentStyle = prefs.getString(PrefKeys.LIST_SORT_STYLE, SortStyle.AZ)
        sortGroup.check(
            when (currentStyle) {
                SortStyle.ZA -> R.id.sort_za
                else -> R.id.sort_az
            }
        )

        sortGroup.setOnCheckedChangeListener { _, checkedId ->
            val newStyle = when (checkedId) {
                R.id.sort_za -> SortStyle.ZA
                else -> SortStyle.AZ
            }
            prefs.setString(PrefKeys.LIST_SORT_STYLE, newStyle)
        }
    }
}
