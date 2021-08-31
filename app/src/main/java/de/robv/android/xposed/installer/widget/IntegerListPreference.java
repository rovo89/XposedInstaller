package de.robv.android.xposed.installer.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;

import com.afollestad.materialdialogs.prefs.MaterialListPreference;

public class IntegerListPreference extends MaterialListPreference {
    public IntegerListPreference(Context context) {
        super(context);
    }

    public IntegerListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public static int getIntValue(String value) {
        if (value == null)
            return 0;

        return (int) ((value.startsWith("0x"))
                ? Long.parseLong(value.substring(2), 16)
                : Long.parseLong(value));
    }

    @Override
    public void setValue(String value) {
        super.setValue(value);
        notifyChanged();
    }

    @Override
    protected boolean persistString(String value) {
        return value != null && persistInt(getIntValue(value));

    }

    @Override
    protected String getPersistedString(String defaultReturnValue) {
        SharedPreferences pref = getPreferenceManager().getSharedPreferences();
        String key = getKey();
        if (!shouldPersist() || !pref.contains(key))
            return defaultReturnValue;

        return String.valueOf(pref.getInt(key, 0));
    }

    @Override
    public int findIndexOfValue(String value) {
        CharSequence[] entryValues = getEntryValues();
        int intValue = getIntValue(value);
        if (value != null && entryValues != null) {
            for (int i = entryValues.length - 1; i >= 0; i--) {
                if (getIntValue(entryValues[i].toString()) == intValue) {
                    return i;
                }
            }
        }
        return -1;
    }
}