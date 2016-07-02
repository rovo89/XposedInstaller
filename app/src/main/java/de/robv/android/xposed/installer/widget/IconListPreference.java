package de.robv.android.xposed.installer.widget;

/*
* Copyright (C) 2013 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ImageView;
import android.widget.ListAdapter;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.installer.R;

public class IconListPreference extends ListPreference {

    private List<Drawable> mEntryDrawables = new ArrayList<>();

    public IconListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.IconListPreference, 0, 0);

        CharSequence[] drawables;

        try {
            drawables = a.getTextArray(R.styleable.IconListPreference_icons);
        } finally {
            a.recycle();
        }

        for (CharSequence drawable : drawables) {
            int resId = context.getResources().getIdentifier(drawable.toString(), "mipmap", context.getPackageName());

            Drawable d = context.getResources().getDrawable(resId);

            mEntryDrawables.add(d);
        }

        setWidgetLayoutResource(R.layout.color_icon_preview);
    }

    protected ListAdapter createListAdapter() {
        final String selectedValue = getValue();
        int selectedIndex = findIndexOfValue(selectedValue);
        return new AppArrayAdapter(getContext(), R.layout.icon_preference_item, getEntries(), mEntryDrawables, selectedIndex);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        String selectedValue = getValue();
        int selectedIndex = findIndexOfValue(selectedValue);

        Drawable drawable = mEntryDrawables.get(selectedIndex);

        ((ImageView) view.findViewById(R.id.preview)).setImageDrawable(drawable);
    }

    @Override
    protected void onPrepareDialogBuilder(Builder builder) {
        builder.setAdapter(createListAdapter(), this);
        super.onPrepareDialogBuilder(builder);
    }

    public class AppArrayAdapter extends ArrayAdapter<CharSequence> {
        private List<Drawable> mImageDrawables = null;
        private int mSelectedIndex = 0;

        public AppArrayAdapter(Context context, int textViewResourceId,
                               CharSequence[] objects, List<Drawable> imageDrawables,
                               int selectedIndex) {
            super(context, textViewResourceId, objects);
            mSelectedIndex = selectedIndex;
            mImageDrawables = imageDrawables;
        }

        @Override
        @SuppressLint("ViewHolder")
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = ((Activity) getContext()).getLayoutInflater();
            View view = inflater.inflate(R.layout.icon_preference_item, parent, false);
            CheckedTextView textView = (CheckedTextView) view.findViewById(R.id.label);
            textView.setText(getItem(position));
            textView.setChecked(position == mSelectedIndex);

            ImageView imageView = (ImageView) view.findViewById(R.id.icon);
            imageView.setImageDrawable(mImageDrawables.get(position));
            return view;
        }
    }
}