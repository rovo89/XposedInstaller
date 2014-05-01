package de.robv.android.xposed.installer.widget;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

public class ListPreferenceSummaryFix extends ListPreference {
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
