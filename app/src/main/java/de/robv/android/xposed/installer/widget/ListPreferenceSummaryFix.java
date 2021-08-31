package de.robv.android.xposed.installer.widget;

import android.content.Context;
import android.util.AttributeSet;

import com.afollestad.materialdialogs.prefs.MaterialListPreference;

public class ListPreferenceSummaryFix extends MaterialListPreference {
    public ListPreferenceSummaryFix(Context context) {
        super(context);
    }

    public ListPreferenceSummaryFix(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setValue(String value) {
        super.setValue(value);
        notifyChanged();
    }
}