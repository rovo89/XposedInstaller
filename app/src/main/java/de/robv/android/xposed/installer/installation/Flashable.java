package de.robv.android.xposed.installer.installation;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;

public abstract class Flashable implements Parcelable {
    public static final String KEY = "flash";

    protected final File mZipPath;

    public Flashable(File zipPath) {
        mZipPath = zipPath;
    }

    protected Flashable(Parcel in) {
        mZipPath = (File) in.readSerializable();
    }

    public abstract void flash(Context context, FlashCallback callback);

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeSerializable(mZipPath);
    }
}
