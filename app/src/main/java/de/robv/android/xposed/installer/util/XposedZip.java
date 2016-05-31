package de.robv.android.xposed.installer.util;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

public class XposedZip {
    public String link = "";
    public String name = "";
    public String architecture = "";

    public static class Installer extends XposedZip {
        public String version = "";
        public int sdk = -1;

        public Installer() {
        }

        public Installer(String link, String name, String architecture, int sdk, String version) {
            this.link = link;
            this.name = name;
            this.architecture = architecture;
            this.sdk = sdk;
            this.version = version;
        }
    }

    public static class Uninstaller extends XposedZip {
        public String date = "";

        public Uninstaller() {
        }

        public Uninstaller(String link, String name, String architecture, String date) {
            this.link = link;
            this.name = name;
            super.architecture = architecture;
            this.date = date;
        }
    }

    public static class MyAdapter<T extends XposedZip> extends ArrayAdapter<T> {

        private final Context context;
        List<T> list;

        public MyAdapter(Context context, List<T> objects) {
            super(context, android.R.layout.simple_dropdown_item_1line, objects);
            this.context = context;
            this.list = objects;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return getMyView(parent, position);
        }

        @Override
        public View getDropDownView(int position, View convertView,
                                    ViewGroup parent) {
            return getMyView(parent, position);
        }

        private View getMyView(ViewGroup parent, int position) {
            View row;
            ItemHolder holder = new ItemHolder();

            LayoutInflater inflater = ((Activity) context).getLayoutInflater();
            row = inflater.inflate(android.R.layout.simple_dropdown_item_1line, parent, false);

            holder.name = (TextView) row.findViewById(android.R.id.text1);

            row.setTag(holder);

            holder.name.setText(list.get(position).name);
            return row;
        }

        private class ItemHolder {
            TextView name;
        }
    }
}