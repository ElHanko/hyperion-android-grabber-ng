package com.elhanko.hyperiongrabber.ng.tv.fragments.settings

import androidx.leanback.widget.GuidanceStylist
import com.elhanko.hyperiongrabber.ng.tv.R

class SettingsStepStylist : GuidanceStylist() {
    override fun onProvideLayoutId(): Int {
        return R.layout.settings_guidance
    }
}
