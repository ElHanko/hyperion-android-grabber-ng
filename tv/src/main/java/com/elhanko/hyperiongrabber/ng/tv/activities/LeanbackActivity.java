package com.elhanko.hyperiongrabber.ng.tv.activities;

import androidx.fragment.app.FragmentActivity;

public abstract class LeanbackActivity extends FragmentActivity {
    @Override
    public boolean onSearchRequested() {
        return false;
    }
}
